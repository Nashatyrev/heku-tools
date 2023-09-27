package tech.pegasys.heku.util.net.discovery.discv5.system

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.DiscoverySystemBuilder
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder
import org.ethereum.beacon.discovery.schema.NodeRecordFactory
import org.ethereum.beacon.discovery.util.Functions
import tech.pegasys.heku.util.ext.distinctBy
import tech.pegasys.heku.util.ext.orTimeout
import tech.pegasys.heku.util.ext.toDiscV5SecretKey
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.spec.networks.Eth2Network
import java.lang.Integer.max
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@ExperimentalTime
fun main() {
    val discoverySystem = NodeSearcher.launchDiscoverySystem(9010)
    val searcher = NodeSearcher(
        discoverySystem,
        getBootnodes(Eth2Network.PRATER),
        searchesPerRound = 128,
//    searchesPerRound = 32,
        findNodesTimeout = 10.seconds
    )
//  val pingDiscoverySystem = NodeSearcher.launchDiscoverySystem(9099)

    val totalCounter = AtomicInteger()
    val eth2Counter = AtomicInteger()
    val mainnetCounter = AtomicInteger()
    val praterCounter = AtomicInteger()
    val liveCount = AtomicInteger()

    val rnd = Random()

    val val64Counter = AtomicInteger()
    val val32to64Counter = AtomicInteger()
    val val1to32Counter = AtomicInteger()

    searcher
//    .search(DiscNodeId.startingWith(Bytes.fromHexString("0x07")), 31 * 8)
        .searchAll()
        .catch { it.printStackTrace() }
        .onCompletion {
            println("Search completed.")
        }
        .onEach {
            totalCounter.incrementAndGet()
            if (it.isEth2Network) eth2Counter.incrementAndGet()
            if (it.eth2Network == Eth2Network.MAINNET) mainnetCounter.incrementAndGet()
            if (it.eth2Network == Eth2Network.PRATER) praterCounter.incrementAndGet()
        }
//    .onEach {
//      val synBits = it.get(SYNC_COMMITTEE_SUBNET_ENR_FIELD) as Bytes?
//      if (synBits != null && !synBits.isZero && it.eth2Network == Eth2Network.PRATER) {
////        println("Found sync committee peer: ${it.descr}")
//      }
//    }
        .filter { it.eth2Network == Eth2Network.MAINNET }
        .onEach {
            val subnetsCnt = it.attestSubnets.size
            when {
                subnetsCnt == 64 -> val64Counter.incrementAndGet()
                subnetsCnt in 32..63 -> val32to64Counter.incrementAndGet()
                subnetsCnt in 1..31 -> val1to32Counter.incrementAndGet()
            }
            it.attestSubnets.isNotEmpty()
        }

//    .onEach { println(it.descr) }
//    .onEach {
//      GlobalScope.launch {
//        delay(rnd.nextInt(10 * 1000).milliseconds)
//
//        val pingRes = DiscoveryPingService.ping(pingDiscoverySystem, it,
//          retries = 5, pingTimeout = 10.seconds)
//
//        if (pingRes) {
//          liveCount.incrementAndGet()
//        }
////        println("Live: $pingRes, " + it.descr)
//      }
//    }
        .launchIn(GlobalScope)

    while (true) {
        println("Found nodes: Total: $totalCounter (live: $liveCount), Eth2: $eth2Counter, Main: $mainnetCounter, Prater: $praterCounter")
        println("Validator nodes found (<32/32-64/64: $val1to32Counter/$val32to64Counter/$val64Counter")
        Thread.sleep(10000)
    }
}

@OptIn(ExperimentalTime::class)
class NodeSearcher(
    val discv5: DiscoverySystem,
    bootNodes: Collection<NodeRecord>? = null,
    val searchesPerRound: Int = 128,
    val findNodesTimeout: Duration = 3.seconds,
    val knownNodes: MutableMap<DiscNodeId, NodeRecord> = ConcurrentHashMap(),
    val logger: (String) -> Unit = { println(it) }
) {

    companion object {

        fun launchDiscoverySystem(): DiscoverySystem = launchDiscoverySystem(0)
        fun launchDiscoverySystem(port: Int): DiscoverySystem {
            val sk = Functions.randomKeyPair().secretKey()
            val nodeRecord = NodeRecordBuilder().secretKey(sk).build()

            Functions.setSkipSignatureVerify(true)
            val discoverySystem = DiscoverySystemBuilder()
                .listen("0.0.0.0", port)
                .secretKey(sk)
                .localNodeRecord(nodeRecord)
                .build()

            discoverySystem.start().join()
            return discoverySystem
        }

        fun create(network: Eth2Network, port: Int = 9000): NodeSearcher {
            val discoverySystem = launchDiscoverySystem(port)
            return NodeSearcher(discoverySystem, getBootnodes(network))
        }
    }

    init {
        if (bootNodes != null) {
            knownNodes += bootNodes
        } else {
            GlobalScope.launch {
                while (true) {
                    val foundPeers = discv5.searchForNewPeers().await()
                    knownNodes += foundPeers
                    if (knownNodes.isNotEmpty()) break
                    delay(1.seconds)
                }
            }
        }
        logger("Bootnodes found: " + knownNodes.size)
    }

    //  fun searchAllDeep(): Flow<NodeRecord> {
//    return (0..255).asFlow().flatMapConcat { idPrefix ->
//      val id = Bytes32.ZERO.mutableCopy()
//      id.set(0, idPrefix.toByte())
//      search(id.toDiscNodeId(), 31 * 8, 16)
//    }
//  }
//
    fun searchAll(): Flow<NodeRecord> {
        val minRespondedNodes = 64
        return flow {
            val checkedNodes = mutableSetOf<DiscNodeId>()
            val respondedNodes = mutableSetOf<DiscNodeId>()
            fun uncheckedNodes() = (knownNodes - checkedNodes).values

            emitAll(knownNodes.values.asFlow())

            while (true) {
                val targetNodes = uncheckedNodes().shuffled().take(searchesPerRound)
                if (targetNodes.isEmpty()) {
                    if (respondedNodes.size < minRespondedNodes) {
                        // too few nodes responded - retry again
                        checkedNodes.clear()
                        checkedNodes.addAll(respondedNodes)
                        continue
                    } else {
                        // requested all recursively
                        break
                    }
                }

                checkedNodes += targetNodes.map { it.getDiscNodeId() }

                for (distance in 256 downTo 256) {

                    suspend fun query(targets: List<NodeRecord>): List<Pair<NodeRecord, Collection<NodeRecord>>> {
                        return targets
                            .mapTo(mutableListOf()) {
                                it to discv5.findNodes(it, listOf(distance)).asDeferred()
                                    .orTimeout(findNodesTimeout)
                            }.mapNotNullTo(mutableListOf()) { (node, nodesPromise) ->
                                try {
                                    node to nodesPromise.await()
                                } catch (e: Exception) {
                                    null
                                }
                            }
                    }

                    val foundNodes = query(targetNodes)

                    respondedNodes += foundNodes.map { it.first.getDiscNodeId() }
                    val unrespondedNodeIds =
                        targetNodes.map { it.getDiscNodeId() } - respondedNodes

                    val foundNodes2 =
                        query(targetNodes.filter { it.getDiscNodeId() in unrespondedNodeIds })

                    val allFoundNodes = foundNodes
                        .flatMapTo(mutableListOf()) { it.second }

                    val newNodes = allFoundNodes
                        .distinctBy { it.getDiscNodeId() }
                        .filter { it.getDiscNodeId() !in knownNodes }

                    emitAll(newNodes.asFlow())
                    knownNodes += newNodes

                    logger(
                        "Looked through ${targetNodes.size} nodes. " +
                                "Responded: ${foundNodes.size}, totally found: ${allFoundNodes.size}, new nodes: ${newNodes.size}, " +
                                "remaining to check: " + (knownNodes.size - checkedNodes.size) +
                                ", found additionally: " + foundNodes2.size
                    )

//          targetNodes = foundNodes.map { it.first }
                }

            }
        }.distinctBy { it.getDiscNodeId() }
    }

    fun search(
        nodeId: DiscNodeId,
        log2Radius: Int,
        maxIterations: Int = Int.MAX_VALUE
    ): Flow<NodeRecord> {

        return flow {
            val checkedNodes = mutableSetOf<DiscNodeId>()
            for (i in 1..maxIterations) {
                val closestNodes = closestKnownNodes(nodeId)
                    .filter { it.getDiscNodeId() !in checkedNodes }
                    .take(searchesPerRound * 1)
                if (closestNodes.isEmpty()) {
                    break
                }
                val targetNodes = closestNodes.shuffled().take(searchesPerRound).toList()
                val newNodes = targetNodes
                    .onEach { checkedNodes += it.getDiscNodeId() }
                    .mapTo(mutableListOf()) { closestNode ->
                        val nodeDistance = closestNode.getDiscNodeId().logDistance(nodeId)
                        val distanceRange = (nodeDistance)..(max(nodeDistance, log2Radius))
                        val searchDistances = distanceRange.toList()
                        closestNode to
                                discv5.findNodes(closestNode, searchDistances).asDeferred()
                                    .orTimeout(findNodesTimeout)
                    }.flatMapTo(mutableListOf()) { (_, requestFuture) ->
                        try {
                            requestFuture.await()
                        } catch (e: Exception) {
                            listOf()
                        }
                    }.distinctBy { it.getDiscNodeId() }
                    .filter { it.getDiscNodeId() !in knownNodes }

                newNodes.forEach { nodeRec ->
                    if (nodeRec.getDiscNodeId().logDistance(nodeId) <= log2Radius) {
                        emit(nodeRec)
                    }
                }

                logger("Total nodes: ${knownNodes.size}, checked: ${checkedNodes.size}, new nodes: ${newNodes.size}")
                knownNodes += newNodes

//        delay(1.seconds)
            }
        }.distinctBy { it.getDiscNodeId() }
    }

    private operator fun MutableMap<DiscNodeId, NodeRecord>.plusAssign(newNodes: Collection<NodeRecord>) {
        this += newNodes
            .map { it.getDiscNodeId() to it }
            .toMap()
    }

    private fun closestKnownNodes(nodeId: DiscNodeId): List<NodeRecord> {
        return knownNodes.values.sortedWith(Comparator.comparing {
            it.getDiscNodeId().logDistance(nodeId)
        })
    }
}

fun getBootnodes(network: Eth2Network): Collection<NodeRecord> =
    TekuConfiguration.builder()
        .eth2NetworkConfig { it.applyNetworkDefaults(network) }
        .build()
        .discovery()
        .bootnodes
        .map { NodeRecordFactory.DEFAULT.fromEnr(it) }