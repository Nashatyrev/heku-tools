package tech.pegasys.heku.statedb.db

import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.statedb.schema.AbstractSchema
import tech.pegasys.heku.statedb.schema.IndexedSszSource
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.util.beacon.spec
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema
import tech.pegasys.teku.spec.networks.Eth2Network

class StateAppender(
    val eth2Network: Eth2Network,
) {

    private data class SourceEntry(
        val stateId: StateId,
        val indexedSsz: IndexedSsz
    )

    inner class SelfFeedingIndexedSszSource(
        val schemaSupplier: (StateId) -> BeaconStateSchema<*,*>
    ) : IndexedSszSource{

        private var single: SourceEntry? = null

        fun putSingle(stateId: StateId, indexedSsz: IndexedSsz) {
            single = SourceEntry(stateId, indexedSsz)
        }

        override suspend fun loadSsz(stateId: StateId): IndexedSsz {
            return if (single?.stateId == stateId) {
                single!!.indexedSsz
            } else {
                try {
                    val stateSchema = schemaSupplier(stateId)
                    val stateSsz = storageSchema.load(stateId).getBytes().toDenseBytes()
                    val state = stateSchema.sszDeserialize(stateSsz)
                    val indexedSsz = IndexedSsz.create(state)
                    indexedSsz
                } catch (e: Exception) {
                    throw IllegalArgumentException("Error loading $stateId, signle = $single", e)
                }
            }
        }
    }

    val selfFeedingIndexedSszSource = SelfFeedingIndexedSszSource { stateId ->
        val specExt = eth2Network.spec()
        val instantSpec = specExt.getInstantSpecAt(specExt.getSlotStartTime(stateId.slot.uint64))
        instantSpec.specVersion.schemaDefinitions.beaconStateSchema
    }

    lateinit var storageSchema: AbstractSchema

    fun initStorageSchema(schema: AbstractSchema) {
        storageSchema = schema
    }

    suspend fun append(stateId: StateId, ssz: IndexedSsz) {
        selfFeedingIndexedSszSource.putSingle(stateId, ssz)
        storageSchema.save(stateId)
    }

    suspend fun append(state: BeaconState) {
        val indexedSsz = IndexedSsz.create(state)
        val stateId = StateId(state.slot.asSlot())
        append(stateId, indexedSsz)
    }
}