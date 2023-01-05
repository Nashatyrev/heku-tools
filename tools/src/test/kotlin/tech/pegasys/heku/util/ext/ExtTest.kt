package tech.pegasys.heku.util.ext

import kotlinx.coroutines.delay
import kotlinx.coroutines.future.asDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

@ExperimentalTime
class ExtTest {

    @Test
    fun `Deferred orTimeout test`() {
        runBlocking {
            println("Start")
            val f1 = CompletableFuture<String>()
            val f2 = CompletableFuture<String>()

            println("Creating d1...")
            val d1 = f1.asDeferred().orTimeout(5.seconds)
//      val d1 = f1.asDeferred().let {
//        async {
//          withTimeout(5.seconds) {
//            it.await()
//          }
//        }
//      }

            println("Creating d2...")
            val d2 = f2.asDeferred().orTimeout(5.seconds)
//      val d2 = f2.asDeferred().let {
//        async {
//          withTimeout(5.seconds) {
//            it.await()
//          }
//        }
//      }

            println("Awaiting 1 ...")
            try {
                d1.await()
            } catch (e: Exception) {
                println("Err: $e")
            }

            println("Awaiting 2 ...")
            try {
                d2.await()
            } catch (e: Exception) {
                println("Err: $e")
            }

            println("Complete")
        }
    }

    @Test
    fun coroutine1() {
        runBlocking {
            println("Outer scope: $this")
            // this: CoroutineScope
            launch {
                println("Inner scope: $this")
                // launch a new coroutine and continue
                delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
                println("World!") // print after delay
            }
            println("Hello") // main coroutine continues while a previous one is delayed
        }
    }

    @Test
    fun `IntRange split test`() {
        assertThat(
            (2..3).split(2)
        ).isEqualTo(
            listOf(
                2..3
            )
        )

        assertThat(
            (2..3).split(3)
        ).isEqualTo(
            listOf(
                2..3
            )
        )

        assertThat(
            (2..4).split(2)
        ).isEqualTo(
            listOf(
                2..3,
                4..4
            )
        )

        assertThat(
            (2..5).split(2)
        ).isEqualTo(
            listOf(
                2..3,
                4..5
            )
        )

        assertThat(
            (2..6).split(2)
        ).isEqualTo(
            listOf(
                2..3,
                4..5,
                6..6
            )
        )
    }
}