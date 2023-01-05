package tech.pegasys.heku.util.ext

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import tech.pegasys.heku.util.flow.CompletableSharedFlow
import java.util.concurrent.CompletionStage
import java.util.concurrent.ConcurrentHashMap

const val MAX_CONCURRENCY = Int.MAX_VALUE

fun <T> Flow<T>.distinct() = distinctBy { it }

fun <T, K> Flow<T>.distinctBy(keySelector: (T) -> K): Flow<T> {
    val origFlow = this
    return flow {
        val past = mutableSetOf<K>()
        origFlow.collect { value ->
            val key = keySelector(value)
            val isNew = past.add(key)
            if (isNew) emit(value)
        }
    }
}

fun <T> Flow<T>.consume(scope: CoroutineScope, consumer: suspend (T) -> Unit) = this
    .onEach { consumer(it) }
    .catch { it.printStackTrace() }
    .launchIn(scope)


fun <T, U> Flow<T>.parallelMap(mapper: suspend (T) -> U): Flow<U> =
    this.flatMapMerge(MAX_CONCURRENCY) { v ->
        flow { emit(mapper(v)) }
    }

fun <T> Flow<T>.parallelOnEach(consumer: suspend (T) -> Unit): Flow<T> =
    parallelMap {
        consumer(it)
        it
    }

fun <T> Flow<T>.parallelFilter(predicate: suspend (T) -> Boolean): Flow<T> = this
    .parallelMap { if (predicate(it)) it else null }
    .mapNotNull { it }

fun <T> Flow<Deferred<T>>.flattenDeferred(): Flow<T> = parallelMap { it.await() }

data class FutureResult<T>(
    val result: T?,
    val error: Throwable?
)

fun <T> Flow<CompletionStage<T>>.flattenFutures(): Flow<FutureResult<T>> =
    parallelMap {
        try {
            FutureResult(it.await(), null)
        } catch (e: Exception) {
            FutureResult(null, e)
        }
    }

fun <T> Flow<CompletionStage<T>>.flattenFuturesSkippingErrors(): Flow<T> =
    flattenFutures()
        .mapNotNull { it.result }

fun <T> Flow<T>.toChannel(scope: CoroutineScope): ReceiveChannel<T> = scope.produce { this@toChannel.collect { send(it) } }

class AssociatedMap<TKey, TValue>(
    private val defaultValue: TValue,
    internal val map: MutableMap<TKey, MutableStateFlow<TValue>> = ConcurrentHashMap()
) : Map<TKey, StateFlow<TValue>> by map {

    fun getOrDefault(key: TKey): StateFlow<TValue> = getOrDefaultMutable(key)

    internal fun getOrDefaultMutable(key: TKey): MutableStateFlow<TValue> = map.computeIfAbsent(key) { MutableStateFlow(defaultValue) }
}
fun <TKey, TValue> Flow<Pair<TKey, TValue>>.associateToStateMap(
    scope: CoroutineScope,
    defaultValue: TValue
) = associateToStateMap(scope, {it.first}, {it.second}, defaultValue)


fun <T, TKey, TValue> Flow<T>.associateToStateMap(
    scope: CoroutineScope,
    keySelector: (T) -> TKey,
    valueSelector: (T) -> TValue,
    defaultValue: TValue
) : AssociatedMap<TKey, TValue> {
    val map = AssociatedMap<TKey, TValue>(defaultValue)
    this.consume(scope) {
        map.getOrDefaultMutable(keySelector(it)).value = valueSelector(it)
    }
    return map
}

data class AssociatedFlow<T, TKey>(
    val key: TKey,
    val flow: Flow<T>
)

/**
 * Experimental associateBy() without buffering and SharedFlow
 */
fun <T, TKey> Flow<T>.associateBy2(
    keySelector: (T) -> TKey,
    terminalEventSelector: (T) -> Boolean = { false }
): Flow<AssociatedFlow<T, TKey>> {
    return flow {
        val subChannels = mutableMapOf<TKey, Channel<T>>()
        this@associateBy2.collect { elem ->
            val key = keySelector(elem)

            val subChannel = subChannels[key] ?: run {
                val channel = Channel<T>()
                subChannels[key] = channel
                emit(AssociatedFlow(key, channel.consumeAsFlow()))
                channel
            }

            subChannel.send(elem)

            if (terminalEventSelector(elem)) {
                subChannel.close()
                subChannels -= key
            }
        }
    }
}

fun <T, TKey> Flow<T>.associateBy(
    keySelector: (T) -> TKey,
    terminalEventSelector: (T) -> Boolean = { false },
    subFlowFactory: () -> CompletableSharedFlow<T> = { CompletableSharedFlow() }
) = associateByWithEmitter(
    keySelector,
    { emitter, elem ->
        emitter.tryEmit(elem)
        if (terminalEventSelector(elem)) emitter.complete()
    },
    subFlowFactory
)

fun <T, TKey> Flow<T>.associateByWithEmitter(
    keySelector: (T) -> TKey,
    subEmitter: (CompletableSharedFlow<T>, T) -> Unit = { emitter, elem -> emitter.tryEmit(elem) },
    subFlowFactory: () -> CompletableSharedFlow<T> = { CompletableSharedFlow(replay = 1024) }
): Flow<AssociatedFlow<T, TKey>> {

    return this.transform {
        val subFlows = mutableMapOf<TKey, CompletableSharedFlow<T>>()
        this@associateByWithEmitter.collect { elem ->
            val key = keySelector(elem)
            val maybeSubFlow = subFlows[key]
            val subFlow =
                if (maybeSubFlow != null) maybeSubFlow
                else {
                    val newSubFlow = subFlowFactory()
                    subFlows[key] = newSubFlow
                    emit(AssociatedFlow(key, newSubFlow.sharedFlow()))
                    newSubFlow
                }
            subEmitter(subFlow, elem)
            if (subFlow.completed) {
                subFlows -= key
            }
        }
    }
}

fun <T> Flow<IndexedValue<T>>.withoutIndex(): Flow<T> = this.map { it.value }

fun <T> Flow<T>.takeUntilSignal(signal: Flow<*>): Flow<T> = flow {
    try {
        coroutineScope {
            launch {
                signal.first()
                this@coroutineScope.cancel()
            }

            collect {
                emit(it)
            }
        }

    } catch (e: CancellationException) {
        //ignore
    }

}
fun <T> Flow<T>.takeUntilSignal(signal: Channel<*>): Flow<T> = takeUntilSignal(signal.consumeAsFlow())