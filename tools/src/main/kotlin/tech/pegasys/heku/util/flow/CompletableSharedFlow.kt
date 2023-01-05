package tech.pegasys.heku.util.flow

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

class CompletableSharedFlow<T>(
    replay: Int = 0,
    extraBufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.SUSPEND
) {
    private val delegate = MutableSharedFlow<T?>(replay, extraBufferCapacity, onBufferOverflow)
    var completed = false

    fun sharedFlow(): Flow<T> = delegate.takeWhile { it != null }.map { it!! }

    fun tryEmit(elem: T): Boolean {
        return delegate.tryEmit(elem)
    }

    fun complete() {
        delegate.tryEmit(null)
        completed = true
    }
}