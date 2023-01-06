package tech.pegasys.heku.util.net.discovery

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.schema.NodeRecord
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.MTimeSupplier
import tech.pegasys.heku.util.net.discovery.discv5.system.descr
import tech.pegasys.heku.util.net.discovery.discv5.system.getDiscNodeId
import tech.pegasys.heku.util.flow.SafeSharedFlow
import tech.pegasys.heku.util.log
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

class FindNodeTimeoutException(message: String) : TimeoutException(message)

fun interface FindNodesFunction {

    @Throws(FindNodeTimeoutException::class)
    suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord>

    companion object {
        fun createFromDiscoverySystem(disc: DiscoverySystem) = DiscV5FindNodesFunction(disc)
    }
}

fun FindNodesFunction.bind(bindTarget: NodeRecord) = BoundFindNodesFunction.create(this, bindTarget)
fun interface BoundFindNodesFunction {
    @Throws(FindNodeTimeoutException::class)
    suspend fun findNodes(distances: List<Int>): Collection<NodeRecord>

    companion object {
        fun create(findNodesFunction: FindNodesFunction, target: NodeRecord) = Default(target, findNodesFunction)
    }

    class Default(
        private val target: NodeRecord,
        private val findNodesFunction: FindNodesFunction
    ) : BoundFindNodesFunction {
        override suspend fun findNodes(distances: List<Int>): Collection<NodeRecord> =
            findNodesFunction.findNodes(target, distances)

    }
}

class DiscV5FindNodesFunction(
    private val disc: DiscoverySystem
) : FindNodesFunction {
    override suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord> {
        return disc.findNodes(target, distances).await()
    }
}

fun FindNodesFunction.withTimeout(timeout: Duration = 5.seconds) = FindNodesFunctionWithTimeout(this, timeout)
class FindNodesFunctionWithTimeout(
    private val delegate: FindNodesFunction,
    private val timeout: Duration
) : FindNodesFunction {

    override suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord> {
        try {
            return withTimeout(timeout) {
                delegate.findNodes(target, distances)
            }
        } catch (e: TimeoutCancellationException) {
            throw FindNodeTimeoutException("No response from $target")
        }
    }
}

fun FindNodesFunction.withDeadReported() = FindNodesFunctionDeadReporter(this)
class FindNodesFunctionDeadReporter(
    private val delegate: FindNodesFunction
) : FindNodesFunction {

    private val deadNodeSink = SafeSharedFlow<NodeRecord>(name = "FindNodesFunctionDeadReporter")
    val deadNodeFlow = deadNodeSink.sharedFlow()

    override suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord> {
        try {
            return delegate.findNodes(target, distances)
        } catch (e: FindNodeTimeoutException) {
            deadNodeSink.emitOrThrow(target)
            throw e
        }
    }
}

fun FindNodesFunction.withRetry(
    retryDelays: List<Duration> = listOf(
        1.milliseconds,
        1.milliseconds,
        5.seconds,
        10.seconds,
        30.seconds,
        60.seconds
    )
) = FindNodesFunctionRetry(this, retryDelays)
class FindNodesFunctionRetry(
    private val delegate: FindNodesFunction,
    private val retryDelays: List<Duration>
) : FindNodesFunction {

    override suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord> {
        for (retryDelay in (listOf(Duration.ZERO) + retryDelays)) {
            delay(retryDelay)
            try {
                return delegate.findNodes(target, distances)
            } catch (e: FindNodeTimeoutException) {
                // retry after timeout
            }
        }
        throw FindNodeTimeoutException("No response from $target")
    }
}

fun FindNodesFunction.withLogging(
    name: String = "",
    logger: (String) -> Unit = { log(it) }
) = FindNodesFunctionLogging(this, name, logger)

fun Collection<NodeRecord>.getDistancesMap(target: NodeRecord) = this
    .groupBy { target.getDiscNodeId().logDistance(it.getDiscNodeId()) }
    .mapValues { it.value.size }

class FindNodesFunctionLogging(
    private val delegate: FindNodesFunction,
    private val name: String,
    private val logger: (String) -> Unit
) : FindNodesFunction {

    private val counter = AtomicLong()

    override suspend fun findNodes(target: NodeRecord, distances: List<Int>): Collection<NodeRecord> {
        val id = name + counter.incrementAndGet()
        logger("[$id] findNodes(${target.descr}, $distances)")
        try {
            val result = delegate.findNodes(target, distances)
            val distanceCounts = result.getDistancesMap(target)
            logger("[$id] findNodes complete: distance -> count $distanceCounts")
            return result
        } catch (e: FindNodeTimeoutException) {
            logger("[$id] findNodes timeout")
            throw e
        } catch (e: Exception) {
            logger("[$id] findNodes: Unexpected exception: $e")
            throw e
        }
    }
}

fun BoundFindNodesFunction.withCycleDelay(
    targetCycleTime: Duration = 5.minutes,
    timer: MTimeSupplier = MTimeSupplier.SYSTEM_TIME
) = BoundFindNodesFunctionCycleDelay(this, targetCycleTime, timer)

class BoundFindNodesFunctionCycleDelay(
    private val delegate: BoundFindNodesFunction,
    private val targetCycleTime: Duration,
    private val timer: MTimeSupplier
) : BoundFindNodesFunction {

    private var lastCycleStarted: MTime = MTime.ZERO
    private val CYCLE_START_DISTANCE = 256

    override suspend fun findNodes(distances: List<Int>): Collection<NodeRecord> {
        if (CYCLE_START_DISTANCE in distances) {
            val curTime = timer.getTime()
            val nextCycleStartTime = lastCycleStarted + targetCycleTime
            if (curTime < nextCycleStartTime) {
                delay(nextCycleStartTime - curTime)
            }
            lastCycleStarted = timer.getTime()
        }
        return delegate.findNodes(distances)
    }
}
