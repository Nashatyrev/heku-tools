package tech.pegasys.heku.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.flow.RepeatableFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.time.Duration.Companion.milliseconds

class RepeatableFlowTest {

    @Disabled
    @Test
    fun experimentTest() {
        val promises = mutableListOf<CompletionStage<String>>()

        val testFlow = flow {
            emit("1")
//    delay(1000)
            for (i in 2..20)
                emit("$i")
            delay(20000)
        }

        val testObj =
            object : RepeatableFlow<String, String>(inFlow = testFlow, 10000.milliseconds) {
                var cnt = 0
                override suspend fun task(inItem: String): CompletionStage<String> {
//      if (inItem == "2") {
//        delay(1000)
//      }
                    return CompletableFuture.completedFuture("$inItem-${cnt++}")
                }
            }

        runBlocking {
            println("Collecting")
            testObj.outFlow.collect {
                println(it)
            }
        }

    }
}