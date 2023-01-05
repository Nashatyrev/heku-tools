package tech.pegasys.heku.util.collections

import tech.pegasys.heku.util.KLock
import tech.pegasys.heku.util.withLock

fun <T> KQueue() = KWritableQueueImpl<T>()

interface KQueue<out T> {

    val size: Int

    suspend fun dequeue(): T
}

interface KWritableQueue<T> : KQueue<T> {

    suspend fun enqueue(resource: T)

    fun remove(resource: T)
}

fun <T, R> KQueue<T>.map(mapper: suspend (T) -> R) = MappingQueue(this, mapper)

class MappingQueue<T, R>(
    val src: KQueue<T>,
    val mapper: suspend (T) -> R
) : KQueue<R> {

    override val size: Int
        get() = src.size

    override suspend fun dequeue(): R =
        mapper(src.dequeue())
}

class KWritableQueueImpl<T> : KWritableQueue<T> {
    private val mutex = KLock()
    private val deque = ArrayDeque<T>()

    override val size: Int
        get() = synchronized(deque) { deque.size }

    override suspend fun enqueue(resource: T) {
        mutex.withLock {
            synchronized(deque) {
                deque.add(resource)
            }
            mutex.kNotifyAll()
        }
    }

    override suspend fun dequeue(): T {
        return mutex.withLock {
            while (true) {
                val el = synchronized(deque) {
                    deque.removeFirstOrNull()
                }
                if (el != null) {
                    return@withLock el
                }
                mutex.kWait()
            }
            throw IllegalStateException() // making compiler happy
        }
    }

    override fun remove(resource: T) {
        synchronized(deque) {
            deque.remove(resource)
        }
    }
}

