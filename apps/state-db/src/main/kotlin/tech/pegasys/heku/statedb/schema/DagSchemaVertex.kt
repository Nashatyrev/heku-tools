package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.diff.DiffResult

data class DagSchemaVertex(
    val stateId: StateId,
    val schema: DagSchema
) {
    suspend fun load(): DiffResult = schema.load(stateId)
    suspend fun save() = schema.save(stateId)
}