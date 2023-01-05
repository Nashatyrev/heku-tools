package tech.pegasys.heku.util

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asDeferred
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.ext.flattenDeferred
import tech.pegasys.heku.util.ext.parallelMap
import tech.pegasys.heku.util.ext.parallelOnEach
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds

@Disabled
class SharedFlowTest {
    @Test
    fun test1() {

        val flow = MutableSharedFlow<String>(replay = 100)

        Thread {
            runBlocking(Dispatchers.IO) {
                var cnt = 1
                while (true) {
                    flow.emit("A-" + cnt++)
                    Thread.sleep(300)
                }
            }
        }.start()

        Thread {
            runBlocking(Dispatchers.Default) {
                var cnt = 1
                while (true) {
                    flow.emit("B-" + cnt++)
                    Thread.sleep(300)
                }
            }
        }.start()

        flow
            .onEach {
                println(it + " : " + Thread.currentThread())
            }
            .launchIn(GlobalScope)
    }

    @Test
    fun testConvertingFuturesFlow() {
        data class D(val idx: Int, val fut: CompletableFuture<String> = CompletableFuture()) {
            fun complete() = fut.complete("s-$idx")
        }

        val futures = List(3) { D(it) }
        val futuresFlow = flow {
            futures.indices.onEach { idx ->
//        delay(1.seconds)
                println("Emitting $idx")
                emit(futures[idx])
            }
        }

//    val resFlow = futuresFlow.map {
//      println("Awaiting $it")
//      it.fut.await()
//    }

//    val resFlow = futuresFlow.flatMapMerge { d ->
//      flow {
//        val v = d.fut.await()
//        emit(v)
//      }
//    }

//    val resFlow =
//      runBlocking {
//        flow<String> {
//          futuresFlow.collect { d ->
//            println("Hey $d")
//            async {
//              println("Awaiting $d")
//              val v = d.fut.await()
//              println("Emitting result $d")
//              emit(v)
//            }
//          }
//        }
//      }

//    val resFlow = GlobalScope.async {
//      flow<String> {
//        futuresFlow.collect { d ->
//          launch {
//            val v = d.fut.await()
//            emit(v)
//          }
//        }
//      }
//    }.getCompleted()
//    val resFlow =
//    channelFlow<String> {
//        futuresFlow
//          .collect { d ->
//            GlobalScope.launch {
//              val v = d.fut.await()
//              send(v)
//            }
//          }
//    }

//    val resFlow =
//      flow<String> {
//        futuresFlow
//          .collect { d ->
//            GlobalScope.launch {
//              val v = d.fut.await()
//              emit(v)
//            }
//          }
//      }


//    futuresFlow.transform<> { req ->
//      la
//    }

        val resFlow = futuresFlow
            .map { d ->
                d.fut.thenApply { d.idx to it }
            }
            .map { it.asDeferred() }
            .flattenDeferred()

        println("resFlow created")

        resFlow.onEach {
            println("Resulted $it")
        }.launchIn(GlobalScope)

        Thread.sleep(1000)
        futures[2].complete()
        Thread.sleep(1000)
        futures[0].complete()
        Thread.sleep(1000)
        futures[1].complete()

        Thread.sleep(10000)
    }

    @Test
    fun test2() {
        println("Starting runBlocking")
        runBlocking { // this: CoroutineScope
            launch {
                doWorld()
                println("doWorld complete")
            }
            println("Hello")
        }
        println("runBlocking complete")
    }

    // this is your first suspending function
    suspend fun doWorld() = coroutineScope {
        launch {
            delay(2000)
            println("Async world")
        }
        delay(1000L)
        println("World!")
    }

    @Test
    fun test3() {
        val sharedFlow = MutableSharedFlow<Int?>(replay = 1000)
        val flow = sharedFlow.takeWhile { it != null }.map { it!! }

        flow
            .onEach { println("Subs-1: $it") }
            .onCompletion { println("Subs-1 complete") }
            .launchIn(GlobalScope)

        println("Emitting 1")
        sharedFlow.tryEmit(1)

        Thread.sleep(300)

        println("Subs-2 starting")
        flow
            .onEach { println("Subs-2: $it") }
            .onCompletion { println("Subs-2 complete") }
            .launchIn(GlobalScope)

        Thread.sleep(300)

        println("Emitting 2")
        sharedFlow.tryEmit(2)

        Thread.sleep(300)

        println("Terminating")
        sharedFlow.tryEmit(null)

        Thread.sleep(3000)
    }

    @Test
    fun flattenMergeTest() {
        val flowList = List(3) { MutableSharedFlow<String>(3) }
        val flowOfFlows = flowList.asFlow()
        flowOfFlows
            .flattenMerge(100)
            .onEach { println(it) }
            .launchIn(GlobalScope)

        Thread.sleep(100)
        flowList[2].tryEmit("c-1")
        Thread.sleep(100)
        flowList[2].tryEmit("c-2")
        Thread.sleep(100)
        flowList[0].tryEmit("a-1")

        Thread.sleep(1000)
    }

    @Test
    fun mapConcurrentTest() {
        runBlocking {
            flowOf(3, 2, 1)
                .parallelMap {
                    delay(it.seconds)
                    "Str-$it"
                }
                .collect { println(it) }
        }
    }

    @Test
    fun passiveProcessing() {
        val coroutineScope = CoroutineScope(Dispatchers.Default)

        val sink = MutableSharedFlow<Int>(3, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        val flow1 = sink.asSharedFlow()
        val passiveFlow = flow1
            .filter { it % 2 == 1 }
            .parallelOnEach {
                println("concurrentOnEach: $it")
            }
            .shareIn(coroutineScope, SharingStarted.Eagerly, 3)
//      .launchIn(GlobalScope)

        flow1
            .onEach { println("flow1-1: $it") }
            .launchIn(GlobalScope)

        flow1
            .onEach { println("flow1-2: $it") }
            .launchIn(GlobalScope)

        sink.tryEmit(1)
        Thread.sleep(100)
        sink.tryEmit(2)
        Thread.sleep(100)
        sink.tryEmit(3)
        Thread.sleep(100)
        sink.tryEmit(4)

        Thread.sleep(200)
        println("Subscribing to passive flow")

        passiveFlow
            .onEach { println("passiveFlow-1: $it") }
            .launchIn(GlobalScope)

        passiveFlow
            .onEach { println("passiveFlow-2: $it") }
            .launchIn(GlobalScope)

        Thread.sleep(10000)
    }

    @Test
    fun `MutableSharedFlow records when no subscribers`() {
        val sink = MutableSharedFlow<String>(3, onBufferOverflow = BufferOverflow.DROP_LATEST)
        val flow = sink.asSharedFlow()

        sink.tryEmit("a")
        sink.tryEmit("b")
        sink.tryEmit("c")
        sink.tryEmit("d")

        runBlocking {
            flow
                .onEach { println(it) }
                .collect()
        }
    }

    @Test
    fun `subsequent buffer test`() {
        val coroutineScope = CoroutineScope(Dispatchers.Default)

        val sink = MutableSharedFlow<String>(3, onBufferOverflow = BufferOverflow.SUSPEND)

        val filteredFlow = sink
            .onSubscription {
                println("Sink subscribed")
            }
            .filter {
                println("Filtering $it")
                it.startsWith("a")
            }
            .shareIn(coroutineScope, SharingStarted.Eagerly, 3)

        Thread.sleep(100)

//    coroutineScope.launch {
        listOf("a1", "b1", "a2", "b2", "a3", "b3", "b4").forEach {
//        sink.emit(it)
            val res = sink.tryEmit(it)
            println("Emitted $it ($res)")
        }
//    }.asCompletableFuture().join()

        println("Collecting...")

        runBlocking {
            filteredFlow
                .onEach { println(it) }
                .collect()
        }
    }
}
