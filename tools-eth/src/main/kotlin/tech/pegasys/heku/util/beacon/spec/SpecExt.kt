package tech.pegasys.heku.util.beacon.spec

import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.ext.toEthSeconds
import tech.pegasys.heku.util.ext.toMTime
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData

/**
 * Adds convenient [Spec] util methods by incorporating [genesisData]
 */
class SpecExt(
    val spec: Spec,
    val genesisData: GenesisData,
) {
    val genesisTime get() = genesisData.genesisTime
    val forks = Forks.fromSpec(spec, genesisData.genesisValidatorsRoot)

    fun getMilestoneEpoch(milestone: SpecMilestone) =
        forks.milestoneToFork[milestone]?.epoch ?: throw IllegalArgumentException("No milestone found: $milestone")

    fun getInstantSpecAt(currentTime: MTime) = InstantSpec(this, currentTime)
    fun getInstantSpecAtMilestone(milestone: SpecMilestone) =
        InstantSpec(this, getEpochStartTime(getMilestoneEpoch(milestone)))
    fun getInstantSpecNow() = getInstantSpecAt(MTime.now())

    fun getSlot(time: MTime) = spec.getCurrentSlot(time.toEthSeconds(), genesisTime)
    fun getEpoch(time: MTime) = spec.computeEpochAtSlot(getSlot(time))

    fun getSlotStartTime(slot: UInt64) = spec.getSlotStartTime(slot, genesisTime).toMTime()
    fun getEpochStartTime(epoch: UInt64) = getSlotStartTime(spec.computeStartSlotAtEpoch(epoch))
}

/**
 * [SpecExt] at fixed time (slot)
 */
class InstantSpec(
    val specExt: SpecExt,
    val time: MTime
) {
    val slot = specExt.getSlot(time)
    val epoch = specExt.getEpoch(time)

    val specVersion by lazy { specExt.spec.atEpoch(epoch) }
    val specConfig by lazy { specVersion.config }

    val enrForkId by lazy { specExt.forks.createEnrForkId(epoch) }
}