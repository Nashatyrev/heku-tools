package tech.pegasys.heku.util.beacon.synccommittee

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import tech.pegasys.heku.util.beacon.SlotAggregator
import tech.pegasys.heku.util.beacon.SlotAggregator.SlotData
import tech.pegasys.heku.util.beacon.SyncSubCommitteeIndex
import tech.pegasys.heku.util.ext.or
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.heku.util.type.slots
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution
import java.lang.Integer.max

class SyncCommitteeParticipationTracker(
    syncCommitteeContributionAndProofFlow: Flow<ContributionAndProof>,
    slotsFlow: Flow<Slot>,
    trackDistance: Slot = 2.slots,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    data class Participation(
        val committeeIndex: SyncSubCommitteeIndex,
        val participants: SszBitvector,
        val maxAggrCount: Int = participants.bitCount
    ) {
        val totalCount get() = participants.schema.length
        val participationCount get() = participants.bitCount

        constructor(contrib: SyncCommitteeContribution) : this(
            contrib.subcommitteeIndex.intValue(),
            contrib.aggregationBits
        )

        fun merge(other: Participation): Participation {
            require(this.committeeIndex == other.committeeIndex)
            return Participation(
                committeeIndex,
                participants.or(other.participants),
                max(maxAggrCount, other.maxAggrCount)
            )
        }
    }

    data class SlotParticipation(
        val slot: Slot,
        val subParticipations: Map<SyncSubCommitteeIndex, Participation>
    )

    private val slotAggregator = SlotAggregator(
        syncCommitteeContributionAndProofFlow,
        { it.contribution.slot.asSlot() },
        slotsFlow,
        trackDistance = trackDistance,
        emitEmptyAggregates = true,
        name = "SyncCommitteeParticipationTracker.slotAggregator",
        scope = scope
    )

    val aggregatesBySlotFlow: Flow<SlotData<ContributionAndProof>> = slotAggregator.aggregatesBySlotFlow
    val lateAggregatesFlow: Flow<ContributionAndProof> = slotAggregator.lateAggregatesFlow

    val participationsFlow: Flow<SlotParticipation> = aggregatesBySlotFlow.map {
        val subs = mutableMapOf<SyncSubCommitteeIndex, Participation>()
        for (contributionAndProof in it.aggregatedData) {
            val newParticipation = Participation(contributionAndProof.contribution)
            subs.merge(newParticipation.committeeIndex, newParticipation) { oldVal, newVal ->
                oldVal.merge(newVal)
            }
        }
        SlotParticipation(it.slot, subs.toSortedMap())
    }
}
