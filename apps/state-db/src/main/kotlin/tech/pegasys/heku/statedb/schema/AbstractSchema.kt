package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.diff.DiffResult
import tech.pegasys.heku.statedb.diff.DiffResult.Companion.toDiffResult
import tech.pegasys.heku.util.collections.SparseBytes
import tech.pegasys.heku.util.ext.max
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot

data class StateId(
    val slot: Slot
) {
    override fun toString(): String {
        return "StateId(slot=$slot, ${slot.epoch} epochs + ${slot - slot.epoch.startSlot}"
    }
}

interface AbstractSchema {

    suspend fun load(stateId: StateId): DiffResult

    suspend fun save(stateId: StateId)
}

data class DagSchemaVertex(
    val stateId: StateId,
    val schema: DagSchema
) {
    suspend fun load(): DiffResult = schema.load(stateId)
    suspend fun save() = schema.save(stateId)
}

interface DagSchema : AbstractSchema {

//    val schemaStateIdCalculator: StateIdCalculator

    fun getParents(stateId: StateId): List<DagSchemaVertex>

    companion object {
        suspend fun DagSchemaVertex?.loadOrEmpty() = this?.load() ?: SparseBytes.EMPTY.toDiffResult()
    }
}