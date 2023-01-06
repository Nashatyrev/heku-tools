package tech.pegasys.heku.util.beacon.synccommittee

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.pegasys.heku.util.beacon.Slot
import tech.pegasys.heku.util.beacon.SlotAggregator
import tech.pegasys.heku.util.beacon.SyncSubCommitteeIndex
import tech.pegasys.heku.util.beacon.ValidatorIndex
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage

class SyncContributionsTracker(
    syncContributionsFlow: Flow<SyncCommitteeMessageAndSubnet>,
    slotsFlow: Flow<Slot>,
    trackDistance: Slot = 2,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    data class SyncCommitteeMessageAndSubnet(
        val message: SyncCommitteeMessage,
        val index: SyncSubCommitteeIndex
    )

    data class SlotParticipations(
        val slot: Slot,
        val subParticipations: Map<SyncSubCommitteeIndex, Set<ValidatorIndex>>
    )

    private val slotAggregator = SlotAggregator(
        syncContributionsFlow,
        { it.message.slot.intValue() },
        slotsFlow,
        trackDistance = trackDistance,
        emitEmptyAggregates = true,
        scope = scope,
        name = "SyncContributionsTracker.slotAggregator"
    )

    val contributionsBySlotFlow = slotAggregator.aggregatesBySlotFlow
    val lateContributionsFlow = slotAggregator.lateAggregatesFlow

    val participationFlow = contributionsBySlotFlow.map {
        val participantMap = it.aggregatedData
            .groupBy { it.index }
            .mapValues { (idx, list) ->
                list.map { it.message.validatorIndex.intValue() }.toSet()
            }
            .toSortedMap()
        SlotParticipations(it.slot, participantMap)
    }
}