package tech.pegasys.heku.fixtures.flow

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.util.flow.SafeSharedFlow
import tech.pegasys.heku.util.flow.awaitForSubscribers
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class FlowFuzzTest(
    val scope: CoroutineScope,
    parallelThreads: Int = 16,
    private val waitSomeTimePeriod: Duration = 300.milliseconds,
    private val waitNoNextPeriod: Duration = 500.milliseconds,
) {
    private val threadPool = Executors.newFixedThreadPool(parallelThreads)

    inner class ResultingFlow<T>(flow: Flow<T>) {
        private val messages = ArrayBlockingQueue<T>(1024)

        init {
            val startLatch = CountDownLatch(1)
            flow
                .onStart {
                    startLatch.countDown()
                }
                .onEach { messages += it }
                .launchIn(scope)
            startLatch.await(1, TimeUnit.SECONDS)
        }

        fun waitNext(): T =
            messages.poll(5, TimeUnit.SECONDS)
                ?: throw AssertionError("Timeout waiting for next element")

        fun waitNoNext(): Boolean {
            val el = messages.poll(waitNoNextPeriod.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            return if (el != null)
                throw AssertionError("No elements were expected, but was $el")
            else true

        }

    }

    data class SourceFlow<T>(val flow: SafeSharedFlow<T>, val expectedSubscriberCount: Int = 1) {
        fun awaitForSubscribers() {
            runBlocking {
                flow.delegate.awaitForSubscribers(expectedSubscriberCount)
            }
        }
    }

    abstract class Action
    abstract class ExecuteAction : Action()
    class ExecuteActionSync(val action: () -> Unit) : ExecuteAction()
    class ExecuteActionParallel(val actions: List<() -> Unit>) : ExecuteAction()
    class CheckAction(val action: () -> Unit) : Action()
    class WaitAction : Action()

    val sourceFlows = mutableListOf<SourceFlow<*>>()
    val resultingFlows = mutableListOf<ResultingFlow<*>>()
    val actions = mutableListOf<Action>()

    fun executeSync(action: () -> Unit): FlowFuzzTest {
        actions += ExecuteActionSync(action)
        return this
    }

    fun executeParallel(actions: List<() -> Unit>): FlowFuzzTest {
        this.actions += ExecuteActionParallel(actions)
        return this
    }

    fun executeParallel(vararg actions: () -> Unit) = executeParallel(listOf(*actions))

    fun waitSomeTime(): FlowFuzzTest {
        this.actions += WaitAction()
        return this
    }

    fun runOnce() {
        actions.forEach { action ->
            when (action) {
                is WaitAction -> Thread.sleep(waitSomeTimePeriod.inWholeMilliseconds)
                is ExecuteActionSync -> action.action()
                is ExecuteActionParallel -> {
                    threadPool.invokeAll(action.actions.map { Executors.callable(it) })
                        .forEach { it.get(10, TimeUnit.SECONDS) }
                }
            }
        }
    }

    fun <T> addResultingFlow(flow: Flow<T>): ResultingFlow<T> {
        val resultingFlow = ResultingFlow(flow)
        resultingFlows += resultingFlow
        return resultingFlow
    }

    fun <T> addLastResultingFlow(flow: Flow<T>): ResultingFlow<T> {
        val ret = addResultingFlow(flow)

        runBlocking {
            sourceFlows.forEach { it.awaitForSubscribers() }
        }
        return ret
    }

    fun <T> createSourceFlow(expectedSubscriberCount: Int = 1): SafeSharedFlow<T> {
        val sourceFlow = SourceFlow(SafeSharedFlow<T>(), expectedSubscriberCount)
        sourceFlows += sourceFlow
        return sourceFlow.flow
    }
}