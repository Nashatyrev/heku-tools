package tech.pegasys.heku.util

import kotlinx.coroutines.delay
import tech.pegasys.heku.util.ext.max
import tech.pegasys.heku.util.ext.removeFirstWhile
import kotlin.time.Duration

class RateLimiter(
    maxCalls: Int,
    perPeriod: Duration,
    private val conservative: Boolean,
    time: MTimeSupplier = MTimeSupplier.SYSTEM_TIME
) {
    private val rateTracker = RateTracker(maxCalls, perPeriod, time)
    private val returnRateTracker = RateTracker(maxCalls, perPeriod, time)

    suspend fun <R> call(itemCount: Int, block: suspend () -> R): R =
        if (conservative) callConservative(itemCount, block)
        else callOptimistic(itemCount, block)

    private suspend fun <R> callOptimistic(itemCount: Int, block: suspend () -> R): R {
        while (true) {
            val duration = rateTracker.requestItems(itemCount)
            if (duration == null) {
                return block()
            } else {
                delay(duration)
            }
        }
    }

    private suspend fun <R> callConservative(itemCount: Int, block: suspend () -> R): R {
        while (true) {
            val duration = rateTracker.checkRequestItems(itemCount)
            val conservativeDuration = returnRateTracker.checkRequestItems(itemCount)
            if (duration == null && conservativeDuration == null) {
                rateTracker.requestItems(itemCount)
                val ret = block()
                returnRateTracker.requestItems(itemCount)
                return ret
            } else {
                val delayPeriod = max(duration ?: Duration.ZERO, conservativeDuration ?: Duration.ZERO)
                delay(delayPeriod)
            }
        }
    }
}

class RateTracker(
    private val maxItems: Int,
    private val perPeriod: Duration,
    private val time: MTimeSupplier = MTimeSupplier.SYSTEM_TIME
) {

    private data class Request(
        val itemsCount: Int,
        val time: MTime
    )

    private val requests = ArrayDeque<Request>()

    private fun pruneOld(curTime: MTime) {
        val oldTime = curTime - perPeriod
        requests.removeFirstWhile { it.time < oldTime }
    }

    fun requestItems(requestedItemCount: Int): Duration? = requestItemsInt(requestedItemCount, true)
    fun checkRequestItems(requestedItemCount: Int): Duration? = requestItemsInt(requestedItemCount, false)

    @Synchronized
    private fun requestItemsInt(requestedItemCount: Int, addRequest: Boolean): Duration? {
        val curTime = time.getTime()
        pruneOld(curTime)

        val doneItemCount = requests.sumOf { it.itemsCount }
        if (doneItemCount + requestedItemCount <= maxItems) {
            if (addRequest) {
                requests += Request(requestedItemCount, curTime)
            }
            return null
        } else {
            var remainingItemCount = doneItemCount
            for (i in requests.indices) {
                remainingItemCount -= requests[i].itemsCount
                if (remainingItemCount + requestedItemCount <= maxItems) {
                    return curTime - (requests[i].time + perPeriod)
                }
            }
            throw IllegalArgumentException("Too many items requested $requestedItemCount ( > $maxItems)")
        }
    }
}