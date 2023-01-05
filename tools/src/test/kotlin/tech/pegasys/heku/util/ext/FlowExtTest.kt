package tech.pegasys.heku.util.ext

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.pegasys.heku.util.flow.BufferOverflowHekuFlowException
import tech.pegasys.heku.util.flow.CompletableSharedFlow
import tech.pegasys.heku.util.flow.SafeSharedFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FlowExtTest {

    @Disabled
    @Test
    fun associateByTest() {
//    val sink = MutableSharedFlow<String>()
        val sink = MutableSharedFlow<String>(1000)

        val mapFlow = sink
            .onSubscription { println("Sink subscribed") }
            .associateBy(
                { it.substring(0..0) },
                { it.elementAt(2) == 'C' },
                {
                    CompletableSharedFlow(
                        replay = 1024,
                        onBufferOverflow = BufferOverflow.DROP_OLDEST
                    )
                })
            .map {
                AssociatedFlow(it.key, it.flow.filter { it.elementAt(1) == '-' })
            }

        mapFlow.onEach { aFlow ->
            println("New associate flow: ${aFlow.key}")
            aFlow.flow
                .onEach { println("  New elem in ${aFlow.key}: $it") }
                .onCompletion { println("Flow complete ${aFlow.key}: $it") }
                .launchIn(GlobalScope)
        }.launchIn(GlobalScope)

        Thread.sleep(100)

        runBlocking {
            println("Emitting events")
            sink.emit("A Open")
            sink.emit("A-1")
            sink.emit("A Close")
            println("Event emitted")
        }

        println("Sleeping")
        Thread.sleep(10000000)
    }

    @Test
    @Disabled // TODO fails for some reason
    fun testSafeSharedFlow() {
        val sink = SafeSharedFlow<String>(1, 9)
        val flow = sink.sharedFlow()

        val latch = CountDownLatch(1)
        val consumed1 = mutableListOf<String>()
        val job1 = flow
            .catch {
                // do nothing
            }
            .consume(GlobalScope) {
                consumed1 += it
                if (consumed1.size >= 3) {
                    latch.await()
                }
            }
        val consumed2 = mutableListOf<String>()
        val exceptionFuture = CompletableFuture<Throwable>()
        val job2 = flow
            .catch { exceptionFuture.complete(it) }
            .consume(GlobalScope) {
                consumed2 += it
            }

        Thread.sleep(300)

        for (i in 0..9) {
            sink.emitOrThrow("s-$i")
        }

        Thread.sleep(300)

        assertThat(consumed1).hasSize(3)
        assertThat(consumed2).hasSize(10)
        assertThat(job1.isActive).isTrue()
        assertThat(job2.isActive).isTrue()

        for (i in 10..12) {
            sink.emitOrThrow("s-$i")
        }

        Thread.sleep(300)

        assertThat(consumed2).hasSize(13)
        assertThat(job1.isActive).isTrue()
        assertThat(job2.isActive).isTrue()

        assertThrows<BufferOverflowHekuFlowException> {
            sink.emitOrThrow("s-!")
        }

        latch.countDown()
        Thread.sleep(300)

        assertThatCode {
            sink.emitOrThrow("s-!!")
        }.doesNotThrowAnyException()

        Thread.sleep(300)

        assertThat(job2.isActive).isFalse()
        val ex = exceptionFuture.get(1, TimeUnit.SECONDS)

        assertThat(ex).isInstanceOf(BufferOverflowHekuFlowException::class.java)

        assertThatCode {
            sink.emitOrThrow("s-!!!")
        }.doesNotThrowAnyException()

    }
}