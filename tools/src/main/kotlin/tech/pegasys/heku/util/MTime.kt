package tech.pegasys.heku.util

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun Long.toMTime() = MTime(this)

@JsonSerialize(using = MTimeJsonSerializer::class)
class MTime(val millis: Long) : Comparable<MTime> {

    init {
        require(millis >= 0)
    }

    operator fun minus(other: MTime): Duration = abs(this.millis - other.millis).milliseconds
    operator fun plus(duration: Duration): MTime = MTime(millis + duration.inWholeMilliseconds)
    operator fun minus(duration: Duration): MTime = MTime(millis - duration.inWholeMilliseconds)
    override operator fun compareTo(other: MTime): Int = this.millis.compareTo(other.millis)

    companion object {
        val ZERO = MTime(0)
        val FORMAT_HHMMSS = SimpleDateFormat("HH:mm:ss")
        val FORMAT_DATE_MILLIS = SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS")
        val FORMAT_DATE_MILLIS_UTC = (FORMAT_DATE_MILLIS.clone() as SimpleDateFormat)
            .also { it.timeZone = TimeZone.getTimeZone("UTC") }


        fun now() = MTime(System.currentTimeMillis())
        fun parseUTC(str: String): MTime =
            MTime(FORMAT_DATE_MILLIS_UTC.parse(str).time)
    }

    private fun DateFormat.formatOrZero(t: Long) = if (t == 0L) "[ZERO MTime]" else this.format(Date(t))

    fun toString(format: DateFormat): String = format.formatOrZero(millis)
    fun toStringUTC(): String = toString(FORMAT_DATE_MILLIS_UTC)
    override fun toString(): String = toString(FORMAT_DATE_MILLIS)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MTime

        if (millis != other.millis) return false

        return true
    }

    override fun hashCode(): Int {
        return millis.hashCode()
    }
}

interface MTimestamped {
    val time: MTime
}

fun interface MTimeSupplier {
    fun getTime(): MTime

    companion object {
        val SYSTEM_TIME = MTimeSupplier { MTime.now() }
    }
}

class MTimeJsonSerializer : JsonSerializer<MTime>() {
    override fun serialize(value: MTime, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.toString())
    }
}

