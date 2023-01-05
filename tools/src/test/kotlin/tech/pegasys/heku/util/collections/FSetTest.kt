package tech.pegasys.heku.util.collections

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.fixtures.Waiter
import tech.pegasys.heku.util.log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FSetTest {
    val updatesFlow = MutableSharedFlow<FSet.Update<String>>(128)
    val fSet = FSet.createFromUpdates(updatesFlow)

    class SomeClass(val s: String) {
        override fun toString() = "SomeClass[$s]"
    }

    fun runBlockingWithTimeout(block: suspend CoroutineScope.() -> Unit) =
        runBlocking {
            withTimeout(5.seconds) {
                block()
            }
        }

    @Test
    @Disabled // not implemented
    fun `test map to arbitrary class remove`() {
        runBlockingWithTimeout {

            val mappedSet = fSet.fMap {
                SomeClass(it)
            }

            val job = launch {
                mappedSet.getUpdates().collect {
                    log("mappedSet update: $it")
                }
            }

            updatesFlow.emit(FSet.Update.createAdded("A"))

            Waiter().waitSuspend { mappedSet.size == 1 }

            updatesFlow.emit(FSet.Update.createAdded("B"))

            Waiter().waitSuspend { mappedSet.size == 2 }

            updatesFlow.emit(FSet.Update.createRemoved("A"))

            Waiter().waitSuspend { mappedSet.size == 1 }

            job.cancel()
        }
    }

    @Test
    fun `test map to arbitrary class dublicate`() {
        runBlockingWithTimeout {

            val mappedSet = fSet.fMap {
                SomeClass(it)
            }

            val job = launch {
                mappedSet.getUpdates().collect {
                    log("mappedSet update: $it")
                }
            }

            updatesFlow.emit(FSet.Update.createAdded("A"))

            Waiter().waitSuspend { mappedSet.size == 1 }

            updatesFlow.emit(FSet.Update.createAdded("B"))

            Waiter().waitSuspend { mappedSet.size == 2 }

            updatesFlow.emit(FSet.Update.createAdded("A"))

            delay(200.milliseconds)

            Assertions.assertThat(mappedSet.size == 2)

            job.cancel()
        }
    }
}