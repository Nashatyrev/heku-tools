package tech.pegasys.heku.util.flow

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.future.await
import java.util.concurrent.CompletionStage
import kotlin.time.Duration

abstract class RepeatableFlow<In, Out>(
    inFlow: Flow<In>,
    private val repeatDuration: Duration
) {

    @OptIn(FlowPreview::class)
    val outFlow = inFlow
        .flatMapMerge(65536) { inItem ->
            flow {
                while (true) {
                    try {
                        val v = task(inItem).await()
                        emit(v)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emit(handleException(inItem, e))
                    }
                    delay(repeatDuration)
                }
            }
        }

    abstract suspend fun task(inItem: In): CompletionStage<Out>

    open fun handleException(inItem: In, err: Throwable): Out {
        throw err
    }
}
