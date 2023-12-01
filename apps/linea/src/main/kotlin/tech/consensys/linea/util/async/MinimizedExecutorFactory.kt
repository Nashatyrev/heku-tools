package tech.consensys.linea.util.async

import com.google.common.util.concurrent.ThreadFactoryBuilder
import tech.pegasys.teku.infrastructure.async.ExecutorServiceFactory
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService

class MinimizedExecutorFactory(
    val factoryId: String,
    val threadNumber: Int
) : ExecutorServiceFactory {
    private val oneThreadExecutor =
                    Executors.newSingleThreadScheduledExecutor(
                        ThreadFactoryBuilder().setNameFormat("linea-sim-$factoryId-event").build()
                    )
    private val executor =
        Executors.newScheduledThreadPool(threadNumber,
            ThreadFactoryBuilder().setNameFormat("linea-sim-$factoryId-%d").build()
        )
    override fun createExecutor(
        name: String,
        maxThreads: Int,
        maxQueueSize: Int,
        threadPriority: Int
    ): ExecutorService {
        return if (maxThreads == 1) {
            oneThreadExecutor
        } else {
            executor
        }
    }

    override fun createScheduledExecutor(name: String): ScheduledExecutorService = executor
}