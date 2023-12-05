package tech.pegasys.heku.util.config

import org.apache.logging.log4j.ThreadContext
import tech.pegasys.teku.infrastructure.async.ExecutorServiceFactory
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class ThreadContextExecutorFactory(
    val delegate: ExecutorServiceFactory,
    val threadContextKey: String,
    val threadContextValue: String,

) : ExecutorServiceFactory  {

    override fun createExecutor(
        name: String,
        maxThreads: Int,
        maxQueueSize: Int,
        threadPriority: Int
    ): ExecutorService =
        CtxExecutorService(delegate.createExecutor(name, maxThreads, maxQueueSize, threadPriority))


    override fun createScheduledExecutor(name: String): ScheduledExecutorService =
        CtxScheduledExecutorService(delegate.createScheduledExecutor(name))

    private open inner class CtxExecutorService(val delegate: ExecutorService) : ExecutorService by delegate {
        override fun <T : Any?> submit(task: Callable<T>): Future<T> =
            delegate.submit( Callable {
                ThreadContext.put(threadContextKey, threadContextValue)
                task.call()
            })

        override fun <T : Any?> submit(task: Runnable, result: T): Future<T> =
            delegate.submit({
                ThreadContext.put(threadContextKey, threadContextValue)
                task.run()
            }, result)


        override fun submit(task: Runnable): Future<*> =
            delegate.submit {
                ThreadContext.put(threadContextKey, threadContextValue)
                task.run()
            }

        override fun execute(command: Runnable) {
            delegate.execute {
                ThreadContext.put(threadContextKey, threadContextValue)
                command.run()
            }
        }
    }

    private inner class CtxScheduledExecutorService(val schDelegate: ScheduledExecutorService) :
        CtxExecutorService(schDelegate), ScheduledExecutorService {
        override fun schedule(command: Runnable, delay: Long, unit: TimeUnit): ScheduledFuture<*> =
            schDelegate.schedule({
                ThreadContext.put(threadContextKey, threadContextValue)
                command.run()
            }, delay, unit)

        override fun <V : Any?> schedule(callable: Callable<V>, delay: Long, unit: TimeUnit): ScheduledFuture<V> =
            schDelegate.schedule( Callable {
                ThreadContext.put(threadContextKey, threadContextValue)
                callable.call()
            }, delay, unit)

        override fun scheduleAtFixedRate(
            command: Runnable,
            initialDelay: Long,
            period: Long,
            unit: TimeUnit
        ): ScheduledFuture<*> =
            schDelegate.scheduleAtFixedRate({
                ThreadContext.put(threadContextKey, threadContextValue)
                command.run()
            }, initialDelay, period, unit)


        override fun scheduleWithFixedDelay(
            command: Runnable,
            initialDelay: Long,
            delay: Long,
            unit: TimeUnit
        ): ScheduledFuture<*> =
            schDelegate.scheduleWithFixedDelay({
                ThreadContext.put(threadContextKey, threadContextValue)
                command.run()
            }, initialDelay, delay, unit)
    }
}