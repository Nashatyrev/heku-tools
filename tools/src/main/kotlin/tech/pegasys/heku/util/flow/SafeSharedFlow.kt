package tech.pegasys.heku.util.flow

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Stops emitting any other events after the buffer overflow
 * Throws (just once) [BufferOverflowHekuFlowException] to both emitter and derived flows
 * This class is intended to help with cases when events are just silently dropped
 */
class SafeSharedFlow<T>(
    replay: Int = 1,
    private val extraBufferCapacity: Int = 64 * 1024,
    private val name: String = "Unnamed",
    private val subscriberCountWarnThreshold: Int = 1024,
    private val warnPrinter: (String) -> Unit = { System.err.println(it) }
) {
    val delegate: MutableSharedFlow<T> = MutableSharedFlow(replay, 1024, BufferOverflow.DROP_OLDEST)
    private var overflowed = false

    fun sharedFlow(): Flow<T> {
        return delegate
            .bufferWithError(extraBufferCapacity, name)
            .onSubscriptionChange { _, count ->
                if (count >= subscriberCountWarnThreshold) {
                    warnPrinter("WARN: too many subscriptions ($count) for [$name].")
                }
            }
    }

    @Throws(BufferOverflowHekuFlowException::class)
    fun emitOrThrow(elem: T) {
        if (!overflowed) {
            if (!delegate.tryEmit(elem)) {
                overflowed = true
                throw BufferOverflowHekuFlowException("Couldn't emit element $elem")
            }
        }
    }
}