package tech.pegasys.heku.fixtures

import kotlinx.coroutines.delay
import tech.pegasys.heku.util.MTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class Waiter(
    val maxTimeToWait: Duration = 5.seconds,
    val checkInterval: Duration = 100.milliseconds
) {

    fun wait(condition: () -> Boolean) {
        val start = MTime.now()
        while (MTime.now() - start <= maxTimeToWait) {
            if (condition()) return
            Thread.sleep(checkInterval.inWholeMilliseconds)
        }
        throw AssertionError("Waiting failed")
    }

    suspend fun waitSuspend(condition: () -> Boolean) {
        val start = MTime.now()
        while (MTime.now() - start <= maxTimeToWait) {
            if (condition()) return
            delay(checkInterval)
        }
        throw AssertionError("Waiting failed")
    }
}