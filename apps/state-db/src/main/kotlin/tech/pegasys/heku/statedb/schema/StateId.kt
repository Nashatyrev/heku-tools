package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.util.type.Slot

data class StateId(
    val slot: Slot
) {
    override fun toString(): String {
        return "StateId(slot=$slot, ${slot.epoch} epochs + ${slot - slot.epoch.startSlot}"
    }
}