package tech.pegasys.heku.util.net.eth2rpc

import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.*
import tech.pegasys.heku.util.RateLimiter
import tech.pegasys.heku.util.beacon.Slot
import tech.pegasys.heku.util.collections.KQueue
import tech.pegasys.heku.util.collections.Pool
import tech.pegasys.heku.util.collections.map
import tech.pegasys.heku.util.ext.split
import tech.pegasys.heku.util.ext.toUInt64
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.toStringCompact
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

class BlockByRangeException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

fun interface BlockByRangeFunction {

    @Throws(BlockByRangeException::class)
    suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock>

    companion object {
        fun createFromEth2Peer(peer: Eth2Peer) = Eth2PeerBlockByRangeFunction(peer)
    }
}

abstract class DelegatingBlockByRangeFunction(
    val delegate: BlockByRangeFunction
) : BlockByRangeFunction {

    override fun equals(other: Any?): Boolean {
        if (other !is DelegatingBlockByRangeFunction) return false
        return delegate == other.delegate
    }

    override fun hashCode(): Int {
        return delegate.hashCode()
    }
}

fun KQueue<Pool.Resource<BlockByRangeFunction>>.toFunctionQueue(): KQueue<BlockByRangeFunction> =
    this.map { ReleasingBlockByRangeFunction(it) }

class ReleasingBlockByRangeFunction(
    private val delegateResource: Pool.Resource<BlockByRangeFunction>
) : DelegatingBlockByRangeFunction(delegateResource.value) {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {
        try {
            return delegateResource.value.getBlocksByRange(startSlot, count)
                .onCompletion {
                    delegateResource.release()
                }
        } catch (e: Throwable) {
            delegateResource.release()
            throw e
        }
    }

}

fun KQueue<BlockByRangeFunction>.toRetryingFunction(maxRetries: Int = 8) =
    RetryingBlockByRangeFunction(maxRetries) { this.dequeue() }

class RetryingBlockByRangeFunction(
    val maxRetries: Int,
    val delegatesSupplier: suspend () -> BlockByRangeFunction,
) : BlockByRangeFunction {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> = flow {
        var trialsLeft = maxRetries
        while (true) {
            val delegate = delegatesSupplier()
            try {
                val blocksFlow = delegate.getBlocksByRange(startSlot, count)
                // collecting all blocks and catching errors
                emitAll(blocksFlow.toList().asFlow())
                break
            } catch (e: Exception) {
                if (trialsLeft == 0) {
                    throw BlockByRangeException("Max trials exceeded", e)
                }
                trialsLeft--
            }
        }
    }
}

fun BlockByRangeFunction.chunkedSequentially(maxChunkSize: Int) =
    SplitSequentiallyBlockByRangeFunction(this, maxChunkSize)

class SplitSequentiallyBlockByRangeFunction(
    delegate: BlockByRangeFunction,
    val maxChunkSize: Int
) : DelegatingBlockByRangeFunction(delegate) {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> =
        flow {
            (startSlot until startSlot + count).split(maxChunkSize).forEach {
                val rangeFlow = delegate.getBlocksByRange(it.first, it.count())
                rangeFlow.collect {
                    emit(it)
                }
            }
        }
}

fun BlockByRangeFunction.chunkedParallel(maxChunkSize: Int, concurrency: Int) =
    SplitParallelBlockByRangeFunction(this, maxChunkSize, concurrency)

class SplitParallelBlockByRangeFunction(
    delegate: BlockByRangeFunction,
    val maxChunkSize: Int,
    val concurrency: Int
) : DelegatingBlockByRangeFunction(delegate) {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {
        return (startSlot until startSlot + count)
            .split(maxChunkSize)
            .asFlow()
            .flatMapMerge(concurrency) {
                delegate.getBlocksByRange(it.first, it.count())
            }
    }
}

fun BlockByRangeFunction.withRateLimit(maxBlocks: Int, perPeriod: Duration) =
    RateLimitingBlockByRangeFunction(this, maxBlocks, perPeriod)

class RateLimitingBlockByRangeFunction(
    delegate: BlockByRangeFunction,
    maxBlocks: Int,
    perPeriod: Duration,
) : DelegatingBlockByRangeFunction(delegate) {

    private val rateLimiter = RateLimiter(maxBlocks, perPeriod, true)

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {
        return rateLimiter.call(count) {
            delegate.getBlocksByRange(startSlot, count)
        }
    }
}

//fun BlockByRangeFunction.odered() = OrderedBlockByRangeFunction(this)
//
//class OrderedBlockByRangeFunction(
//    val delegate: BlockByRangeFunction
//) : BlockByRangeFunction {
//
//    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {
//        return flow {
//            val flow = delegate.getBlocksByRange(startSlot, count)
//            val receivedBlocks = mutableMapOf<Slot, BeaconBlock>()
//            var nextSlot = startSlot
//            flow.collect {
//                val slot = it.slot.intValue()
//                receivedBlocks[slot] = it
//                if (slot == nextSlot) {
//                    while (true) {
//                        val block = receivedBlocks.remove(nextSlot)
//                        if (block != null) {
//                            emit(block)
//                        } else {
//                            break
//                        }
//                        nextSlot++
//                    }
//                }
//            }
//
//            receivedBlocks
//                .toSortedMap()
//                .values
//                .forEach{ emit(it) }
//        }
//    }
//}
//
fun BlockByRangeFunction.withLogging(
    logger: (String) -> Unit = { log(it) }
) = LoggingBlockByRangeFunction(this, logger)

class LoggingBlockByRangeFunction(
    delegate: BlockByRangeFunction,
    val logger: (String) -> Unit
) : DelegatingBlockByRangeFunction(delegate) {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {
        val id = counter.incrementAndGet()
        logger("[$id] getBlocksByRange($startSlot, $count)")
        try {
            val ret = delegate.getBlocksByRange(startSlot, count)
            logger("[$id] got the flow")

            val blockCounter = AtomicInteger()

            return ret
                .onEach { blockCounter.incrementAndGet() }
                .catch {
                    logger("[$id] Flow error after $blockCounter blocks: ${it.toStringCompact()}")
                    throw it
                }
                .onCompletion {
                    logger("[$id] Collected $blockCounter blocks")
                }

        } catch (e: Exception) {
            logger("[$id] error $e")
            throw e
        }
    }

    companion object {
        private val counter = AtomicLong()
    }
}

fun BlockByRangeFunction.withMinimumBlockCount(
    minimumThreshold: Int
) = ErrorWhenFewBlocksByRangeFunction(this, minimumThreshold)

class ErrorWhenFewBlocksByRangeFunction(
    delegate: BlockByRangeFunction,
    private val blockCountThreshold: Int
) : DelegatingBlockByRangeFunction(delegate) {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> =
        flow {
            val delegateFlow = delegate.getBlocksByRange(startSlot, count)
            var counter = 0
            delegateFlow.collect {
                emit(it)
                counter++
            }
            if (counter < blockCountThreshold) {
                throw BlockByRangeException("Too few block received $counter < $blockCountThreshold")
            }
        }

}

class Eth2PeerBlockByRangeFunction(
    val peer: Eth2Peer
) : BlockByRangeFunction {

    override suspend fun getBlocksByRange(startSlot: Slot, count: Int): Flow<BeaconBlock> {

        return callbackFlow<SignedBeaconBlock> {

            val callback = RpcResponseListener<SignedBeaconBlock> {
                val res = trySendBlocking(it)
                if (res.isSuccess) {
                    SafeFuture.COMPLETE
                } else {
                    SafeFuture.failedFuture<Void>(RuntimeException("Couldn't emit block: $res"))
                }
            }
            val resultFuture = peer.requestBlocksByRange(startSlot.toUInt64(), count.toUInt64(), callback)

            resultFuture
                .catchAndRethrow {
                    cancel("", it)
                }
                .thenPeek {
                    channel.close()
                }

            awaitClose()
        }
            .map { it.message }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Eth2PeerBlockByRangeFunction) return false
        return this.peer == other.peer
    }

    override fun hashCode(): Int {
        return peer.hashCode()
    }
}