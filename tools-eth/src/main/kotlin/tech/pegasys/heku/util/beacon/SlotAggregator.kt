package tech.pegasys.heku.util.beacon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.selects.select
import tech.pegasys.heku.util.flow.SafeSharedFlow
import tech.pegasys.heku.util.flow.bufferWithError
import tech.pegasys.heku.util.ext.toChannel
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.heku.util.type.slots
import java.util.concurrent.ConcurrentHashMap

/**
 * Collects slot-annotated data until `slotsFlow` exceeds collection slot by `trackDistance`
 * after which the collection is emitted to `aggregatesBySlotFlow`
 * If a data with an older slot is received it is emitted to `lateAggregatesFlow`
 * If `emitEmptyAggregates` is `true` then empty aggregates are also emitted for the slots without data
 */
class SlotAggregator<T>(
    slotData: Flow<T>,
    slotExtractor: (T) -> Slot,
    slotsFlow: Flow<Slot>,
    trackDistance: Slot,
    emitEmptyAggregates: Boolean = true,
    name: String = "Unnamed",
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    data class SlotData<T>(
        val slot: Slot,
        val aggregatedData: List<T>
    )

    private val lateAggregatesSink = SafeSharedFlow<T>(0, 1024, "SlotAggregator.lateAggregatesSink")
    val lateAggregatesFlow: Flow<T> = lateAggregatesSink.sharedFlow()

    private val bufferedSlotData = slotData
        .bufferWithError(64 * 1024, "SlotAggregator[$name].bufferedSlotData")
    private val bufferedSlots = slotsFlow
            // 1 epoch buffering
        .bufferWithError(32, "SlotAggregator[$name].bufferedSlots")

    val aggregatesBySlotFlow: Flow<SlotData<T>> = flow {
        val aggregatesBySlot = ConcurrentHashMap<Slot, MutableList<T>>()

        val aggregatesChannel = bufferedSlotData.toChannel(scope)
        val slotChannel = slotsFlow.toChannel(scope)
        var lastSlot = 0.slots
        while(true) {
            select<Unit> {
                aggregatesChannel.onReceive {
                    val slot = slotExtractor(it)
                    if (slot + trackDistance > lastSlot) {
                        val list = aggregatesBySlot
                            .computeIfAbsent(slot) { mutableListOf() }
                        list += it
                    } else {
                        lateAggregatesSink.emitOrThrow(it)
                    }
                }
                slotChannel.onReceive { curSlot ->
                    val startSlot1 = lastSlot.value - trackDistance.value + 1
                    val startSlot =
                        when {
                            startSlot1 > 0 -> startSlot1.slots
                            else -> aggregatesBySlot.keys.minOrNull()
                                ?: (curSlot - trackDistance + 1)
                        }
                    val pastSlots = startSlot .. (curSlot - trackDistance)
                    for (pastSlot in pastSlots) {
                        val list = aggregatesBySlot.remove(pastSlot)
                        if (list == null) {
                            if (emitEmptyAggregates) {
                                emit(SlotData(pastSlot, emptyList()))
                            }
                        } else {
                            emit(SlotData(pastSlot, list))
                        }
                    }
                    lastSlot = curSlot
                }
            }
        }
    }
}
