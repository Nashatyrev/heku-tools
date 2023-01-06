package tech.pegasys.heku.util.net.discovery.discv5.system

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.BUFFERED
import kotlinx.coroutines.future.asDeferred
import org.apache.tuweni.bytes.Bytes32
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.schema.NodeRecordFactory
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.ext.orTimeout
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.streams.toList
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

val nodesFile = File("disc-nodes.txt")

fun populateNodesFile() {
    populateNodesFile(launchDiscovery(true))
}

fun populateNodesFile(disc: DiscoverySystemExt) {
    while (true) {
        val initialNodes = disc.system.streamLiveNodes().toList()
        println("Live nodes: " + initialNodes.size)
        nodesFile.writeText(initialNodes.map { it.asEnr() }.joinToString("\n"))
        Thread.sleep(5000)
    }
}

fun launchDiscovery(fillNodes: Boolean) = DiscoverySystemExtBuilder().also {
    it.network = Eth2Network.MAINNET
    it.withNetworkBootnodes()
    it.builderTweaker = { bld ->
        if (!fillNodes) {
            bld.recursiveLookupInterval(1.days.toJavaDuration())
        }
    }
}.buildAndLaunch().join()

fun main() {
    val fillNodes = false

    println("Launching disc...")
    val disc = launchDiscovery(fillNodes)
    if (fillNodes) {
        populateNodesFile(disc)
    }

    println("Reading init nodes...")
    val initNodes = nodesFile.readLines().map { NodeRecordFactory.DEFAULT.fromEnr(it) }
//    val initNodes = listOf(NodeRecordFactory.DEFAULT.fromEnr("enr:-KG4QJRlj4pHagfNIm-Fsx9EVjW4rviuZYzle3tyddm2KAWMJBDGAhxfM2g-pDaaiwE8q19uvLSH4jyvWjypLMr3TIcEhGV0aDKQ9aX9QgAAAAD__________4JpZIJ2NIJpcIQDE8KdiXNlY3AyNTZrMaEDhpehBDbZjM_L9ek699Y7vhUJ-eAdMyQW_Fil522Y0fODdGNwgiMog3VkcIIjKA"))


//    searchOne(disc.system, initNodes)
    searchMultiple(disc.system, initNodes, 64)
}

fun searchOne(disc: DiscoverySystem, initNodes: List<NodeRecord>) {
    //    val targetId = DiscNodeId.ZERO
//    val targetId = DiscNodeId(Bytes32.random())
    val targetId =
        DiscNodeId(Bytes32.fromHexString("071c000000000000000000000000000000000000000000000000000000000000"))

    log("Searching for target: $targetId")
    val nodes = runBlocking {
        RecursiveNodeSearch(disc, initNodes, targetId = targetId)
//            .lookup_go()
            .lookupCoroutine()
//            .lookup_simple()
    }
    log("Found: ")
    nodes.map { it.getDiscNodeId() }.onEach { println("    $it") }

}

fun searchMultiple(disc: DiscoverySystem, initNodes: List<NodeRecord>, targetCount: Int) {
    val targets = (0 until targetCount).map { DiscNodeId(Bytes32.random()) }
    val res =
        runBlocking {
            searchMultiple(disc, initNodes, targets)
        }
    println("Result: ")
    println("    " + res.joinToString("\n    ") {
        "" + it.target + ": " + it.result.take(4).map { it.getDiscNodeId() }
    })

    val wantedDistance = 256 - 12
    val matchPrefixCount = res.sumOf { (target, nodes) ->
        nodes.count { it.getDiscNodeId().logDistance(target) <= wantedDistance }
    }
    log("Prefixed Nodes found: $matchPrefixCount")

    println("Nodes, " +
            "queried: " + res.sumOf { it.search.queriedNodeIds.size } + ", " +
            "distinct queried: " + res.flatMap { it.search.queriedNodeIds }.distinct().size + ", " +
            "responded: " + res.sumOf { it.search.respondedNodeIds.size } + ", " +
            "timed out: " + res.sumOf { it.search.failedNodeIds.size }
    )
    val allQueriedIds = res.flatMap { it.search.queriedNodeIds }
    allQueriedIds
        .distinct()
        .map { id -> id to allQueriedIds.count { it == id } }
        .filter { it.second >= 3 }
        .onEach { println("  ${it.second} times queried : ${it.first}") }
}

data class SearchMultipleResult(
    val target: DiscNodeId,
    val result: List<NodeRecord>,
    val search: RecursiveNodeSearch
)

suspend fun searchMultiple(
    disc: DiscoverySystem,
    initNodes: List<NodeRecord>,
    targets: List<DiscNodeId>
) = coroutineScope {
    val counter = AtomicInteger()
    val results = targets.map { target ->
        target to async {
            log("Searching target $target")
            counter.incrementAndGet()
            val search = RecursiveNodeSearch(disc, initNodes, targetId = target, scope = this)
            val res = search.lookupCoroutine()
            log("Target search complete (${res.size} found): $target. Remaining targets: ${counter.decrementAndGet()}")
            res to search
        }
    }.map { (target, futureRes) ->
        val (result, search) = futureRes.await()
        SearchMultipleResult(target, result, search)
    }

    results
}

//@OptIn(ExperimentalTime::class)
class RecursiveNodeSearch(
    val discv5: DiscoverySystem,
    val kBuckets: Collection<NodeRecord>,
    val alphaParam: Int = 3,
    val kParam: Int = 15,
    val findNodesTimeout: Duration = 3.seconds,
    val targetId: DiscNodeId = DiscNodeId(Bytes32.random()),
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val foundNodesMap: MutableMap<DiscNodeId, NodeRecord> =
        (kBuckets.associateBy { it.getDiscNodeId() }).toMutableMap()
    val queriedNodeIds: MutableSet<DiscNodeId> = mutableSetOf()
    val respondedNodeIds: MutableSet<DiscNodeId> = mutableSetOf()
    val failedNodeIds: MutableSet<DiscNodeId> = mutableSetOf()

    suspend fun lookupCoroutine(): List<NodeRecord> {
        while (true) {
            val closestNotDead = sortByDistance(foundNodesMap.values, targetId)
                .map { it.getDiscNodeId() }
                .filter { it !in failedNodeIds }
                .take(kParam)

            if (closestNotDead.size == kParam && closestNotDead.all { it in respondedNodeIds }) {
                return closestNotDead.map { foundNodesMap[it]!! }
            }

            val queryCandidates = foundNodesMap - queriedNodeIds
            val toQuery = getClosest(queryCandidates.values, targetId, alphaParam)
            queriedNodeIds += toQuery.map { it.getDiscNodeId() }
            toQuery
                .map { node ->
                    scope.async {
                        try {
                            val distances = distancesToQuery(node.getDiscNodeId(), targetId)
                            val res = findNodes(node, distances)
                            respondedNodeIds += node.getDiscNodeId()
                            res
                        } catch (e: TimeoutCancellationException) {
                            failedNodeIds += node.getDiscNodeId()
                            // node not responded - ignore
                            emptyList<NodeRecord>()
                        }
                    }
                }
                .onEach { queryResultDeffered ->
                    val res = queryResultDeffered.await()
                    foundNodesMap += res.associateBy { it.getDiscNodeId() }
                }
        }
    }

    /**
     * @throws TimeoutCancellationException
     */
    suspend fun findNodes(node: NodeRecord, distances: List<Int>) =
        findNodesDeferred(node, distances).await()

    fun findNodesDeferred(node: NodeRecord, distances: List<Int>) =
        discv5.findNodes(node, distances).asDeferred().orTimeout(findNodesTimeout)

    fun findNodesFuture(node: NodeRecord, distances: List<Int>) =
        SafeFuture.of(discv5.findNodes(node, distances))
            .orTimeout(findNodesTimeout.toJavaDuration())

    companion object {
        fun sortByDistance(nodes: Collection<NodeRecord>, targetId: DiscNodeId): List<NodeRecord> =
            nodes.sortedBy { it.getDiscNodeId().logDistance(targetId) }

        fun getClosest(
            nodes: Collection<NodeRecord>,
            targetId: DiscNodeId,
            count: Int
        ): List<NodeRecord> =
            sortByDistance(nodes, targetId).take(count)

        fun distancesToQuery(queryNodeId: DiscNodeId, targetId: DiscNodeId): List<Int> {
            val dist = queryNodeId.logDistance(targetId)
            return listOf(dist, dist + 1, dist - 1)
                .filter { it >= 0 && it <= 256 }
        }
    }

    private val asked: MutableSet<DiscNodeId> = mutableSetOf()
    private val seen: MutableSet<DiscNodeId> = mutableSetOf()
    private val result = NodesByDistance(targetId, kParam)
    private val replyCh: Channel<Collection<NodeRecord>> = Channel(BUFFERED)
    private var queries = -1

    suspend fun lookup_go(): List<NodeRecord> {
        while (advance()) {
        }
        return result.nodes
    }

    suspend fun advance(): Boolean {
        if (startQueries()) {
            val nodes = replyCh.receive()
            queries--
            val ret = nodes
                .filter { it.getDiscNodeId() !in seen }
                .onEach {
                    seen += it.getDiscNodeId()
                    result.push(it)
                }
            return ret.isNotEmpty()
        } else {
            return false
        }
    }

    suspend fun startQueries(): Boolean {
        if (queries == -1) {
            replyCh.send(getClosest(kBuckets, targetId, kParam))
            queries = 1
            return true
        } else {
            val toQuery = result.nodes.take(alphaParam)
            for (n in toQuery) {
                if (n.getDiscNodeId() !in asked) {
                    asked += n.getDiscNodeId()
                    queries++
                    scope.launch {
                        val newNodes = try {
                            findNodes(n, distancesToQuery(n.getDiscNodeId(), targetId))
                        } catch (e: TimeoutCancellationException) {
                            // node not responded - ignore
                            emptyList<NodeRecord>()
                        }
                        replyCh.send(newNodes)
                    }
                }
            }
            return toQuery.isNotEmpty()
        }
    }

    private class NodesByDistance(
        val target: DiscNodeId,
        val maxCapacity: Int
    ) {
        var nodes: List<NodeRecord> = emptyList()

        fun push(node: NodeRecord) {
            nodes = getClosest(nodes + node, target, maxCapacity)
        }
    }
}

