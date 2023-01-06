package tech.pegasys.heku.util

import kotlinx.coroutines.CancellationException
import java.nio.channels.ClosedChannelException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletionException


private val LOG_TIME_FORMAT = SimpleDateFormat("hh:mm:ss.SSS")

fun log(s: String) {
    println("[${LOG_TIME_FORMAT.format(Date())}] $s")
}

val silentExceptionClasses = setOf(
    CancellationException::class,
    CompletionException::class,
)
fun Throwable.toStringCompact(): String {
    val causeChain = mutableListOf(this)
    var ex = this
    while (ex.cause != null) {
        causeChain += ex.cause!!
        ex = ex.cause!!
    }
    return causeChain.map {
        val simpleName = it::class.simpleName
        if (it::class in silentExceptionClasses) {
            simpleName
        } else {
            if (it.message != null)
                "$simpleName(${it.message})"
            else
                simpleName
        }
    }.joinToString("<-")
}

fun setDefaultExceptionHandler() {
    Thread.setDefaultUncaughtExceptionHandler { t, e ->
        if (e is ClosedChannelException) {
            // ignore
        } else {
            e.printStackTrace()
        }
    }
}

enum class SizeUnit(
    val shortName: String,
    val longName: String,
    val multiplier: Long
) {
    BYTES("b", "bytes", 1),
    KILO_BYTES("Kb", "KBytes", 1L shl 10),
    MEGA_BYTES("Mb", "MBytes", 1L shl 20),
    GIGA_BYTES("GB", "GBytes", 1L shl 30),
    TERA_BYTES("TB", "TBytes", 1L shl 40)
}

data class ReadableSize(
    val value: Double,
    val units: SizeUnit
) {
    private fun defaultPrecision() = if (value < 10 && units != SizeUnit.BYTES) 1 else 0

    fun valueString(decimalPrecision: Int) = String.format(Locale.US, "%.${decimalPrecision}f", value)
    val valueString = valueString(defaultPrecision())

    override fun toString() = valueString + units.shortName

    companion object {
        fun create(bytesSize: Number): ReadableSize {
            val bytes = bytesSize.toLong()
            val unit = when {
                bytes < SizeUnit.KILO_BYTES.multiplier -> SizeUnit.BYTES
                bytes < SizeUnit.MEGA_BYTES.multiplier -> SizeUnit.KILO_BYTES
                bytes < SizeUnit.GIGA_BYTES.multiplier -> SizeUnit.MEGA_BYTES
                bytes < SizeUnit.TERA_BYTES.multiplier -> SizeUnit.GIGA_BYTES
                else -> SizeUnit.TERA_BYTES
            }
            return ReadableSize(bytes.toDouble() / unit.multiplier, unit)
        }
    }
}
