package tech.pegasys.heku.util.collections

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import tech.pegasys.heku.util.ext.consume


interface Pool<T> : KQueue<Pool.Resource<T>>{

    interface Resource<out T> {

        val value: T

        suspend fun release()
    }

    override suspend fun dequeue(): Resource<T>

    companion object {
        fun <T> createFromFSet(resources: FSet<T>, resourceQueue: KWritableQueue<T> = KQueue()) =
            FSetPool(resources, resourceQueue)
    }
}

class FSetPool<T>(
    private val resources: FSet<T>,
    private val resourceQueue: KWritableQueue<T>,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : Pool<T> {

    init {
        resources.getUpdates().consume(scope) {
            it.added.forEach {
                resourceQueue.enqueue(it)
            }
            it.removed.forEach {
                resourceQueue.remove(it)
            }
        }
    }

    inner class ResourceImpl(
        override val value: T
    ) : Pool.Resource<T> {

        override suspend fun release() {
            if (value in resources) {
                resourceQueue.enqueue(value)
            }
        }
    }

    override val size get() = resourceQueue.size

    override suspend fun dequeue(): Pool.Resource<T> {
        val res = resourceQueue.dequeue()
        return ResourceImpl(res)
    }
}

fun <T> FSet<T>.pooled(): Pool<T> = Pool.createFromFSet(this)

suspend fun <T, R> Pool.Resource<T>.use(block: suspend (T) -> R): R =
    try {
        block(value)
    } finally {
        release()
    }

fun <T> Pool<T>.streamResources(): Flow<Pool.Resource<T>> = flow {
    while (true) {
        emit(dequeue())
    }
}

