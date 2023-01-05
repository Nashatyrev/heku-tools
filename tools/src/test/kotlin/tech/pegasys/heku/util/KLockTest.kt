package tech.pegasys.heku.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import tech.pegasys.heku.fixtures.Waiter
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class KLockTest {

    val lock = KLock()

    @Test
    fun test1() {
        runBlocking {
            withTimeout(5.seconds) {
                val waitCounter = AtomicInteger()

                fun launchWait(n: Int) {
                    launch {
                        log("#$n entering lock")
                        lock.withLock {
                            log("#$n lock acquired")
                            waitCounter.incrementAndGet()
                            lock.kWait()
                            waitCounter.decrementAndGet()
                            log("#$n wait exited")
                        }
                        log("#$n released lock")
                    }
                }
                launchWait(1)
                launchWait(2)


                Waiter().waitSuspend { waitCounter.get() == 2 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 1 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 0 }

                log("#0 idle notify")
                lock.withLock {
                    lock.kNotify()
                }
                log("#0 idle notify complete")

                launchWait(3)
                launchWait(4)

                Waiter().waitSuspend { waitCounter.get() == 2 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 1 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 0 }

                log("#0 idle notify")
                lock.withLock {
                    lock.kNotify()
                }
                log("#0 idle notify complete")

                launchWait(5)
                launchWait(6)

                val waitLoopCounter = AtomicInteger()
                val infiniteJob = launch {
                    log("#7 entering lock")
                    lock.withLock {
                        log("#7 lock acquired")
                        waitCounter.incrementAndGet()
                        while (true) {
                            log("#7 entering wait")
                            lock.kWait()
                            waitLoopCounter.incrementAndGet()
                            log("#7 wait exited")
                        }
                    }
                    log("#7 released lock")
                }

                Waiter().waitSuspend { waitCounter.get() == 3 }

                log("#0-1 calling notifyAll")
                lock.withLock {
                    lock.kNotifyAll()
                }
                log("#0-1 notifyAll complete")

                Waiter().waitSuspend { waitCounter.get() == 1 && waitLoopCounter.get() == 1 }

                log("#0-2 calling notifyAll")
                lock.withLock {
                    lock.kNotifyAll()
                }
                log("#0-2 notifyAll complete")

                Waiter().waitSuspend { waitCounter.get() == 1 && waitLoopCounter.get() == 2 }

                log("#0-3 calling notifyAll")
                lock.withLock {
                    lock.kNotifyAll()
                }
                log("#0-3 notifyAll complete")

                Waiter().waitSuspend { waitCounter.get() == 1 && waitLoopCounter.get() == 3 }

                infiniteJob.cancel()
            }
        }
    }

    @Test
    fun test2() {
        runBlocking {
            withTimeout(5.seconds) {
                val waitCounter = AtomicInteger()

                fun launchWait(n: Int) {
                    launch {
                        log("#$n entering lock")
                        lock.withLock {
                            log("#$n lock acquired")
                            waitCounter.incrementAndGet()
                            lock.kWait()
                            waitCounter.decrementAndGet()
                            log("#$n wait exited")
                        }
                        log("#$n released lock")
                    }
                }
                launchWait(1)
                launchWait(2)

                Waiter().waitSuspend { waitCounter.get() == 2 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 1 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 0 }

                launchWait(3)

                Waiter().waitSuspend { waitCounter.get() == 1 }

                log("#0 entering lock")
                lock.withLock {
                    log("#0 calling notify")
                    lock.kNotify()
                    log("#0 notified")
                }
                log("#0 lock released")

                Waiter().waitSuspend { waitCounter.get() == 0 }

            }
        }
    }
}