package tech.pegasys.heku.util.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.pegasys.heku.util.ext.consume
import java.lang.management.ManagementFactory
import java.lang.management.MemoryType
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class CustomFlowExtTest {

    val scope1 = CoroutineScope(Dispatchers.Default)
    val scope2 = CoroutineScope(Dispatchers.Default)
    val scope21 = CoroutineScope(Dispatchers.Default)
    val scope22 = CoroutineScope(Dispatchers.Default)
    val scope3 = CoroutineScope(Dispatchers.Default)

    suspend fun MutableSharedFlow<*>.awaitForSubscribers(numberOfSubscribers: Int) {
        this@awaitForSubscribers.subscriptionCount
            .takeWhile { it < numberOfSubscribers }
            .collect()
    }

    @Test
    fun `test shareInCompleatable completes dependent flows`() {
        val srcFlow = flow {
            emit(1)
            delay(100.milliseconds)
            emit(2)
            delay(100.milliseconds)
            emit(3)
            delay(100.milliseconds)
            println(scope1)
        }

        val sharedFlow: Flow<Int> = srcFlow
            .map { it * it }
            .shareInCompletable(scope1, SharingStarted.Lazily, replay = 1000)

        runBlocking {
            withTimeout(5.seconds) {
                val result1 = async {
                    sharedFlow
                        .map { it + 1 }
                        .toCollection(mutableListOf())
                }
                val result2 = async {
                    sharedFlow
                        .map { it + 2 }
                        .toCollection(mutableListOf())
                }

                assertThat(result1.await()).isEqualTo(listOf(2, 5, 10))
                assertThat(result2.await()).isEqualTo(listOf(3, 6, 11))
            }

            println(scope1)
        }
    }

    @Test
    fun `test shareInCompleatable exceptionally completes dependent flows`() {
        val srcFlow = flow {
            emit(1)
            delay(100.milliseconds)
            emit(2)
            delay(100.milliseconds)
            emit(3)
            delay(100.milliseconds)
            throw RuntimeException("Test exception")
        }

        val sharedFlow: Flow<Int> = srcFlow
            .map { it * it }
            .shareInCompletable(scope1, SharingStarted.Lazily, replay = 1000)

        runBlocking {
            withTimeout(5.seconds) {
                val data1 = mutableListOf<Int>()
                val data2 = mutableListOf<Int>()
                val result1 = scope2.async {
                    sharedFlow
                        .onEach { data1 += it }
                        .collect()
                }
                val result2 = scope3.async {
                    sharedFlow
                        .onEach { data2 += it }
                        .collect()
                }

                assertThrows<UpstreamHekuFlowException> { result1.await() }
                assertThrows<UpstreamHekuFlowException> { result2.await() }
                assertThat(data1).isEqualTo(listOf(1, 4, 9))
                assertThat(data2).isEqualTo(listOf(1, 4, 9))
            }
        }
    }

    @Test
    fun `test Flow bufferWithException`() {
        val sink = MutableSharedFlow<String>()

        runBlocking {
            println("Launching emitter...")
            launch {
                println("Waiting for subscriber...")
                sink.awaitForSubscribers(2)
                (0..50).forEach {
                    println("Emitting $it...")
                    sink.emit("$it")
                    delay(10.milliseconds)
                }
                println("Emitting done")
            }


            println("Launching slow consumer...")
            val slowConsumerRes = async {
                try {
                    sink
                        .bufferWithError(5)
                        .onEach { println("1. Received: $it") }
                        .onEach { delay(20.milliseconds) }
                        .collect()
                    null
                } catch (e: BufferOverflowHekuFlowException) {
                    println("1. Buffer overflow caught: $e")
                    e
                }
            }

            println("Launching fast consumer...")
            launch {
                sink
                    .bufferWithError(5)
                    .onEach { println("2. Received: $it") }
                    .take(50)
                    .collect()
            }
            println("Waiting all jobs to complete...")

            assertThat(slowConsumerRes.await()).isNotNull()
        }

        println("All complete")
    }

    @Test
    fun `test Flow bufferWithException multithreaded`() {
        val threadCnt = 16
        val elemCnt = 1024
        val totCnt = elemCnt * threadCnt

        val sink = MutableSharedFlow<Int>(extraBufferCapacity = totCnt)

        val bufferedFlow = sink.bufferWithError(totCnt)

        val res1 = mutableListOf<Int>()
        bufferedFlow.consume(scope1) {
            res1 += it
        }

        val res2 = mutableListOf<Int>()
        bufferedFlow.consume(scope2) {
            res2 += it
        }

        runBlocking {
            sink.awaitForSubscribers(2)
        }

        val threads = (0 until threadCnt)
            .map { idx ->
                Thread {
                    for (i in 0 until elemCnt) {
                        val res = sink.tryEmit(elemCnt * idx + i)
                        assertThat(res).isTrue()
                    }
                }
            }
            .onEach { it.start() }

        threads.forEach {
            it.join(5000)
        }

        for (i in 0..50) {
            if (res1.size == totCnt && res2.size == totCnt) {
                break
            }
            Thread.sleep(100)
        }

        assertThat(res1).hasSize(totCnt).doesNotHaveDuplicates()
        assertThat(res2).hasSize(totCnt).doesNotHaveDuplicates()
    }


    @Disabled
    @Test
    fun test1() {

        val sink = MutableSharedFlow<String>()

        println("Launching emit...")
        val job = scope1.launch {
            println("Waiting for subscriber...")
            sink.awaitForSubscribers(2)
            (0..4).forEach {
                println("Emitting $it...")
                sink.emit("$it")
                delay(1.seconds)
            }
            println("Emitting done")
        }

        val sharedFlow = sink
            .withIndex()
            .onEach {
                println("1. Received: $it")
                if (it.index == 1) {
                    println("1. Throwing...")
                    throw RuntimeException("test")
                }
            }
            .catch {
                println("1. Exception caught: $it")
                throw it
            }
            .onCompletion {
                println("Consumer 1 complete")
//                scope2.cancel("Cancelled due to completion")
            }
            .shareInCompletable(scope2, SharingStarted.Eagerly)


        sharedFlow
            .catch {
                println("1-1. Exception caught: $it")
            }
            .onCompletion {
                println("1.1 complete")
            }
            .consume(scope21) {
                println("1-1. Received: $it")
            }
        sharedFlow
            .catch {
                println("1-2. Exception caught: $it")
            }
            .onCompletion {
                println("1.2 complete")
            }
            .consume(scope22) {
                println("1-2. Received: $it")
            }

        sink
            .withIndex()
            .onEach {
                println("2. Received: $it")
            }
            .launchIn(scope3)

        runBlocking {
            job.join()
        }

        Thread.sleep(1000)

        println(scope1)
        println(scope2)
        println(scope21)
        println(scope22)
        println(scope3)
    }

    @Test
    fun `test onSubscriptionChange `() {
        val src: Flow<Int> = flow {
            delay(100.milliseconds)
            emit(1)
            delay(100.milliseconds)
            emit(2)
            delay(100.milliseconds)
            emit(3)
            delay(1.days)
        }

        runBlocking {
            val cntRecords = mutableListOf<Pair<Int, Int>>()
            val sharedFlow = src
                .shareIn(scope1, SharingStarted.Lazily, replay = 10)
                .onSubscriptionChange { previousSubscriberCount, currentSubscriberCount ->
                    println("Cnt: $currentSubscriberCount, " + Thread.currentThread())
                    cntRecords += previousSubscriberCount to currentSubscriberCount
                }

            val neverCompletedSubscriber = scope1.async {
                sharedFlow
                    .map { it + 1 }
                    .first { it == 100 }
            }

            val completedSubscriber = async {
                sharedFlow
                    .map { it + 1 }
                    .first { it == 4 }
            }

            completedSubscriber.await()
            assertThat(cntRecords).containsExactlyInAnyOrderElementsOf(listOf(0 to 1, 1 to 2, 2 to 1))
            cntRecords.clear()

            val erroredSubscriber = scope2.async {
                sharedFlow
                    .map { it + 1 }
                    .onEach {
                        if (it == 4) {
                            throw RuntimeException("Test")
                        }
                    }
                    .collect()
            }

            assertThrows<RuntimeException> { erroredSubscriber.await() }
            assertThat(cntRecords).containsExactlyInAnyOrderElementsOf(listOf(1 to 2, 2 to 1))
        }
    }

    @Test
    fun testBufferMemoryConsumption() {
        val all = (0..1023)
            .map {
                SafeSharedFlow<Int>(extraBufferCapacity = 1 * 1024 * 1024)
            }
            .onEach { it.emitOrThrow(1) }

        System.gc()

        for (mpBean in ManagementFactory.getMemoryPoolMXBeans()) {
            if (mpBean.getType() === MemoryType.HEAP) {
                System.out.printf(
                    "Name: %s: %s\n",
                    mpBean.getName(), mpBean.getUsage()
                )
            }
        }

        println(all)
    }
}