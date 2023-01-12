package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.util.ext.max
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot

fun StateIdCalculator.isSame(stateId: StateId) = this.calculateStateId(stateId) == stateId
fun StateIdCalculator.withMinimalSlot(minSlot: Slot) = StateIdCalculator {
    val stateId = calculateStateId(it)
    StateId(max(stateId.slot, minSlot))
}

fun interface StateIdCalculator {

    fun calculateStateId(stateId: StateId) : StateId

    companion object {

        val SAME = StateIdCalculator { it }

        fun everyNEpochs(epochs: Epoch) = everyNSlots(epochs.endSlot)

        fun everyNSlots(slots: Slot) = StateIdCalculator {
            val slot = it.slot floorTo slots
            StateId(slot)
        }
    }
}