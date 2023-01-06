package tech.pegasys.heku.util.config.logging

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.Marker
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.Logger
import org.apache.logging.log4j.core.filter.AbstractFilter
import org.apache.logging.log4j.message.Message
import kotlin.reflect.KClass

typealias LogMatcher = (formatStr: String, level: Level, err: Throwable?) -> Boolean

class RawFilter(
    val matcher: LogMatcher,
    onMatch: Filter.Result, 
    onMismatch: Filter.Result
) : AbstractFilter(onMatch, onMismatch) {

    companion object {
        fun excludeByMessage(formatMsg: String) =
            RawFilter({ msg, _, _ -> msg.contains(formatMsg) }, Filter.Result.DENY, Filter.Result.NEUTRAL)

        fun excludeByMessageAndLevel(formatMsg: String, level: Level) =
            RawFilter({ msg, entryLevel, _ -> level == entryLevel && msg.contains(formatMsg) }, Filter.Result.DENY, Filter.Result.NEUTRAL)

        fun <TErr: Throwable> excludeByMessageLevelException(formatMsg: String, level: Level, exceptionClass: KClass<TErr>) =
            RawFilter({ msg, entryLevel, err ->
                level == entryLevel && exceptionClass.isInstance(err) && msg.contains(
                    formatMsg
                ) }, Filter.Result.DENY, Filter.Result.NEUTRAL)
    }
    
    private fun filterImpl(msg: String, level: Level, err: Throwable?): Filter.Result {
        return if (matcher(msg, level, err)) onMatch else onMismatch
    }

    override fun filter(event: LogEvent): Filter.Result {
        return filterImpl(event.message.format, event.level, event.thrown)
    }

    override fun filter(logger: Logger, level: Level, marker: Marker, msg: Message, t: Throwable?): Filter.Result {
        return filterImpl(msg.format, level, t)
    }

    override fun filter(logger: Logger, level: Level, marker: Marker, msg: Any, t: Throwable?): Filter.Result {
        return filterImpl(msg.toString(), level, t)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        vararg params: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(logger: Logger, level: Level, marker: Marker, msg: String, p0: Any): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any,
        p5: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any,
        p5: Any,
        p6: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any,
        p5: Any,
        p6: Any,
        p7: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any,
        p5: Any,
        p6: Any,
        p7: Any,
        p8: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }

    override fun filter(
        logger: Logger,
        level: Level,
        marker: Marker,
        msg: String,
        p0: Any,
        p1: Any,
        p2: Any,
        p3: Any,
        p4: Any,
        p5: Any,
        p6: Any,
        p7: Any,
        p8: Any,
        p9: Any
    ): Filter.Result {
        return filterImpl(msg, level, null)
    }
}