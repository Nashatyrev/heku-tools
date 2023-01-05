package tech.pegasys.heku.util.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.ReadableSize
import tech.pegasys.heku.util.flow.bufferWithError
import tech.pegasys.heku.util.flow.shareInCompletable
import tech.pegasys.heku.util.flow.stateInCompletable
import tech.pegasys.heku.util.flow.windowed
import tech.pegasys.heku.util.ext.max
import kotlin.math.roundToLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit


class BandwidthStats(
    val messageFlow: Flow<MessageEvent>,
    val timer: () -> MTime = { MTime.now() },
    val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    enum class MessageDirection { IN, OUT }

    data class MessageEvent(
        val direction: MessageDirection,
        val size: Long
    )

    data class TrafficStat(
        val readMessageCount: Int = 0,
        val writeMessageCount: Int = 0,
        val readSize: Long = 0,
        val writeSize: Long = 0,
        val time: MTime
    ) {

        operator fun minus(other: TrafficStat): TrafficSpeed {
            require(other.time <= this.time)
            require(other.readSize <= this.readSize)
            require(other.writeSize <= this.writeSize)
            val duration = this.time - other.time
            return TrafficSpeed(
                this.time,
                TrafficDelta(this.readMessageCount - other.readMessageCount, this.readSize - other.readSize, duration),
                TrafficDelta(this.writeMessageCount - other.writeMessageCount, this.writeSize - other.writeSize, duration),
            )
        }

        operator fun plus(other: TrafficStat): TrafficStat =
            TrafficStat(
                this.readMessageCount + other.readMessageCount,
                this.writeMessageCount + other.writeMessageCount,
                this.readSize + other.readSize,
                this.writeSize + other.writeSize,
                max(this.time, other.time)
            )
    }

    data class TrafficDelta(
        val messages: Int,
        val bytes: Long,
        val duration: Duration
    ) {
        val durationSeconds
            get() = (if (duration.inWholeMilliseconds == 0L) 1.milliseconds else duration).toDouble(DurationUnit.SECONDS)
        val bytesPerSecond get() = (bytes.toDouble() / durationSeconds).roundToLong()

        override fun toString() = "$messages messages at ${ReadableSize.create(bytesPerSecond)}/s"
    }

    data class TrafficSpeed(
        val time: MTime,
        val read: TrafficDelta,
        val write: TrafficDelta
    )

    private val trafficStatFlow = messageFlow
        .bufferWithError(64 * 1024, "BandwidthStats.trafficStatFlow")
        .runningFold(TrafficStat(time = timer())) { stat, event ->
            stat + when (event.direction) {
                MessageDirection.IN -> TrafficStat(1, 0 ,event.size, 0, timer())
                MessageDirection.OUT -> TrafficStat(0, 1, 0, event.size, timer())
            }
        }
        .stateInCompletable(scope, SharingStarted.Eagerly, TrafficStat(time = timer()))

    val trafficSecondSnapshots = flow {
        while (true) {
            emit(trafficStatFlow.value)
            delay(1.seconds)
        }
    }.shareInCompletable(scope, SharingStarted.Lazily)

    val trafficSpeed1Sec = trafficSecondSnapshots
        .windowed(2, 1, false)
        .map {
            it.last() - it.first()
        }

    val trafficSpeed10Sec = trafficSecondSnapshots
        .windowed(10, 10, true)
        .map {
            it.last() - it.first()
        }

    val trafficSpeed1Min = trafficSecondSnapshots
        .windowed(60, 60, true)
        .map {
            it.last() - it.first()
        }
}