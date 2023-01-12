package tech.pegasys.heku.statedb.db

import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState

interface StateLoader {

    suspend fun loadState(slot: Slot): BeaconState
    suspend fun loadState(epoch: Epoch): BeaconState = loadState(epoch.startSlot)
}
