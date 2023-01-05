package tech.pegasys.heku.util.collections

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.log
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PoolTest {

    val setUpdatesFlow = MutableSharedFlow<FSet.Update<String>>(128)
    val resourcesSet = FSet.createFromUpdates(setUpdatesFlow)
    val pool = Pool.createFromFSet(resourcesSet)

    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    @Disabled
    @Test
    fun test1() {
        runBlocking {
            launch {
                pool.streamResources().collect { resource ->
                    log("Resource: ${resource.value}")

                    launch {
                        resource.use {
                            log("Start using resource $it")
                            delay(500.milliseconds)
                            log("End using resource $it")
                        }
                    }
                }
            }

            log("Adding Res-A")
            setUpdatesFlow.emit(FSet.Update.createAdded("Res-A"))

            delay(5.seconds)

            log("Adding Res-B")
            setUpdatesFlow.emit(FSet.Update.createAdded("Res-B"))

            delay(5.seconds)

            log("Removing Res-A")
            setUpdatesFlow.emit(FSet.Update.createRemoved("Res-A"))
        }
    }
}