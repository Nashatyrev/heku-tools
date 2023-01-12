package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.diff.DiffResult.Companion.toDiffResult
import tech.pegasys.heku.util.collections.SparseBytes

interface DagSchema : Schema {

    fun getParents(stateId: StateId): List<DagSchemaVertex>

    companion object {
        suspend fun DagSchemaVertex?.loadOrEmpty() = this?.load() ?: SparseBytes.EMPTY.toDiffResult()
    }
}