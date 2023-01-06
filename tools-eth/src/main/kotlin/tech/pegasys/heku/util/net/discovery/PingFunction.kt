package tech.pegasys.heku.util.net.discovery

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt64
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.message.PongData
import org.ethereum.beacon.discovery.schema.NodeRecord
import tech.pegasys.heku.util.net.discovery.discv5.system.descr
import tech.pegasys.heku.util.log
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class PingTimeoutException(message: String) : TimeoutException(message)

fun interface PingFunction {

    @Throws(PingTimeoutException::class)
    suspend fun ping(target: NodeRecord): PongData

    companion object {
        fun createFromDiscoverySystem(disc: DiscoverySystem) = DiscV5PingFunction(disc)
    }
}

class DiscV5PingFunction(
    private val disc: DiscoverySystem
) : PingFunction {
    override suspend fun ping(target: NodeRecord): PongData {
        return disc.ping(target).await()
    }
}

fun PingFunction.withTimeout(timeout: Duration = 5.seconds) = PingFunctionWithTimeout(this, timeout)
class PingFunctionWithTimeout(
    private val delegate: PingFunction,
    private val timeout: Duration
) : PingFunction {

    override suspend fun ping(target: NodeRecord): PongData {
        try {
            return withTimeout(timeout) {
                delegate.ping(target)
            }
        } catch (e: TimeoutCancellationException) {
            throw PingTimeoutException("No response from $target")
        }
    }
}

fun PingFunction.withLogging(
    name: String = "",
    logger: (String) -> Unit = { log(it) }
) = PingFunctionLogging(this, name, logger)
class PingFunctionLogging(
    private val delegate: PingFunction,
    private val name: String,
    private val logger: (String) -> Unit
) : PingFunction {

    private val counter = AtomicLong()

    override suspend fun ping(target: NodeRecord): PongData {
        val id = name + counter.incrementAndGet()
        logger("[$id] ping(${target.descr})")
        try {
            val result = delegate.ping(target)
            logger("[$id] ping complete")
            return result
        } catch (e: PingTimeoutException) {
            logger("[$id] ping timeout")
            throw e
        } catch (e: Exception) {
            logger("[$id] ping unexpected exception: $e")
            throw e
        }
    }
}

fun FindNodesFunction.toPingFunction(
    distances: List<Int> = listOf(0)
): PingFunction = PingFunctionFromFindNode(this, distances)

class PingFunctionFromFindNode(
    private val findNodesFunction: FindNodesFunction,
    private val distances: List<Int>
) : PingFunction {

    val dummyPongData = object : PongData {
        override fun getEnrSeq(): UInt64 = UInt64.ZERO
        override fun getRecipientIp(): Bytes = Bytes.fromHexString("0x00000000")
        override fun getRecipientPort(): Int = 0
    }

    override suspend fun ping(target: NodeRecord): PongData {
        try {
            findNodesFunction.findNodes(target, distances)
            return dummyPongData
        } catch (e: FindNodeTimeoutException) {
            throw PingTimeoutException("Timeout pinging $target")
        }
    }
}

