package tech.pegasys.heku.util.ext

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

fun <T> ScheduledExecutorService.schedule(delay: Duration, task: () -> T): CompletionStage<T> {
    val ret = CompletableFuture<T>()
    this.schedule({
        try {
            val res = task()
            ret.complete(res)
        } catch (e: Throwable) {
            ret.completeExceptionally(e)
        }
    }, delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
    return ret
}
