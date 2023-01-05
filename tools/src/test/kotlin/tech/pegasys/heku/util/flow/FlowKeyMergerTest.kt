package tech.pegasys.heku.util.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.ext.consume
import kotlin.random.Random
import kotlin.time.Duration.Companion.days

@Disabled // FlowKeyMerger is TODO
class FlowKeyMergerTest {

    val scope = CoroutineScope(Dispatchers.Default)
    val random = Random(1)

    @Test
    fun test1() {
        val flow1 = flow {
            for (i in 0..5) {
                delay(random.nextLong(20))
                emit(i)
            }
            delay(1.days)
        }

        val flow2 = flow {
            for (i in 0..5) {
                delay(random.nextLong(20))
                emit(i)
            }
            delay(1.days)
        }

        val flow3 = flow {
            for (i in 0..5) {
                delay(random.nextLong(20))
                emit(i)
            }
            delay(1.days)
        }


        val combine = flow1
            .combine(flow2) { o1, o2 -> o1 to o2 }
            .combine(flow3) { p1, o2 -> Triple(p1.first, p1.second, o2) }

        val merge = FlowKeyMerger.merge3<Int, Int, Int, Int>(
            flow1, { it },
            flow2, { it },
            flow3, { it },
        )

        combine.consume(scope) {
            println(it)
        }

        Thread.sleep(100000)
    }
}