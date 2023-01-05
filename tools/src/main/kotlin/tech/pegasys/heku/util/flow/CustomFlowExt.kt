package tech.pegasys.heku.util.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicInteger

typealias SubscriptionChangeListener = suspend (previousSubscriberCount: Int, currentSubscriberCount: Int) -> Unit

/**
 * The same as [Flow.buffer] but throws [BufferOverflowHekuFlowException] downstream if buffer overflows
 */
fun <T> Flow<T>.bufferWithError(capacity: Int, name: String = "Unnamed"): Flow<T> {

    return flow {
        var consumed = 0
        this@bufferWithError
            .withIndex()
            .buffer(capacity, BufferOverflow.DROP_OLDEST)
            .collect {
                if (consumed++ != it.index)
                    throw BufferOverflowHekuFlowException("Overflow after messages consumed: $consumed for flow [$name] with last element: ${it.value}")
                emit(it.value)
            }
    }

//    val consumedCounter = AtomicInteger()
//    return this
//        .withIndex()
//        .buffer(capacity, BufferOverflow.DROP_OLDEST)
//        .onEach {
//            val consumed = consumedCounter.getAndIncrement()
//            if (consumed != it.index) {
////                throw BufferOverflowHekuFlowException("Overflow after messages consumed: $consumed for flow [$name] with last element: ${it.value}")
//            }
//        }
//        .map { it.value }
}

/**
 * The same as [Flow.shareIn] but
 * 1. Completes all dependent flows when upstream flow is completed
 * 2. Rethrows upstream exception with [UpstreamHekuFlowException] to all dependent flows. Completes after exception
 *
 * Starts sharing coroutine in the [scope] which completes on upstream completion or exception
 */
fun <T> Flow<T>.shareInCompletable(scope: CoroutineScope, sharingStarted: SharingStarted, replay: Int = 0): Flow<T> =
    (this as Flow<Any?>)
        .catch {
            emit(it as Any?)
        }
        .onCompletion {
            emit(null)
        }
        .shareIn(scope, sharingStarted, replay)
        .takeWhile { it != null }
        .map {
            if (it is Throwable) {
                throw UpstreamHekuFlowException("Upstream flow exception", it)
            } else {
                @Suppress("UNCHECKED_CAST")
                it as T
            }
        }

/**
 * The same as [Flow.shareIn] but
 * 1. Completes all dependent flows when upstream flow is completed
 * 2. Rethrows upstream exception with [UpstreamHekuFlowException] to all dependent flows. Completes after exception
 *
 * Starts sharing coroutine in the [scope] which completes on upstream completion or exception
 */
@Suppress("UNCHECKED_CAST")
fun <T> Flow<T>.stateInCompletable(scope: CoroutineScope, sharingStarted: SharingStarted, initialValue: T): StateFlow<T> {

    val stateFlow = (this as Flow<Any?>)
        .catch {
            emit(it as Any?)
        }
        .onCompletion {
            emit(null)
        }
        .stateIn(scope, sharingStarted, initialValue);
    val resultFlow = stateFlow
        .takeWhile { it != null }
        .map {
            if (it is Throwable) {
                throw UpstreamHekuFlowException("Upstream flow exception", it)
            } else {
                @Suppress("UNCHECKED_CAST")
                it as T
            }
        }

    class MyStateFlow : StateFlow<T> {
        override val replayCache: List<T> get() = stateFlow.replayCache.mapNotNull { it as? T }

        override suspend fun collect(collector: FlowCollector<T>): Nothing {
            resultFlow.collect(collector)
            throw RuntimeException("Should not happen")
        }

        override val value: T get() = stateFlow.value as T
    }

    return MyStateFlow()
}

fun <T> Flow<T>.onSubscriptionChange(listener: SubscriptionChangeListener): Flow<T> {
    class SubscriptionTracker : Flow<T> {
        private val subscriptionCount = AtomicInteger()
        override suspend fun collect(collector: FlowCollector<T>) {
            try {
                val newCnt = subscriptionCount.incrementAndGet()
                listener(newCnt - 1, newCnt)
                this@onSubscriptionChange.collect(collector)
            } finally {
                val newCnt = subscriptionCount.decrementAndGet()
                listener(newCnt + 1, newCnt)
            }
        }
    }
    return SubscriptionTracker()
}

