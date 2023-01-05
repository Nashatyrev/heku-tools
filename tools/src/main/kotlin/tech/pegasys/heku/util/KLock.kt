package tech.pegasys.heku.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

suspend fun <R> KLock.withLock(block: suspend () -> R): R {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

fun KLock(): KLock = KLockImpl()

interface KLock {

    suspend fun lock()
    fun unlock()

    suspend fun kWait()
    fun kNotify()
    fun kNotifyAll()
}

class KLockImpl : KLock {
    private val mutex = Mutex()
    private val waitMutex = Mutex(true)

    override suspend fun lock() {
        mutex.lock()
    }

    override fun unlock() {
        mutex.unlock()
    }

    override suspend fun kWait() {
        mutex.unlock()
        try {
            waitMutex.lock()
        } finally {
            mutex.lock()
        }
    }

    @Synchronized
    override fun kNotify() {
        if (waitMutex.isLocked) waitMutex.unlock()
        waitMutex.tryLock()
    }

    @Synchronized
    override fun kNotifyAll() {
        while (waitMutex.isLocked) waitMutex.unlock()
        waitMutex.tryLock()
    }
}