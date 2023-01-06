package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.diff.DiffResult
import tech.pegasys.heku.statedb.diff.toDiffResult
import tech.pegasys.heku.util.collections.SparseBytes
import tech.pegasys.heku.util.collections.mergeNoOverlap

open class MergeSchema(
    val componentSchemas: List<DagSchema>,
    val delegateToParent: Boolean = true
) : DagSchema {

    private fun parentDelegate(stateId: StateId): DagSchemaVertex? {
        if (!delegateToParent) return null
        val parents = getParents(stateId)
        if (parents.size != 1) return null
        val parent = parents.first()
        if (parent.stateId != stateId) return null
        return parent
    }

    override fun getParents(stateId: StateId): List<DagSchemaVertex> =
        componentSchemas.map { DagSchemaVertex(stateId, it) }

    override suspend fun load(stateId: StateId): DiffResult {
        val parent = parentDelegate(stateId)
        return if (parent != null) {
            parent.load()
        } else {
            componentSchemas
                .map {
                    it.load(stateId).getBytes()
                }
                .mergeNoOverlap()
                .toDiffResult()
        }
    }

    override suspend fun save(stateId: StateId) {
        val parent = parentDelegate(stateId)
        if (parent != null) {
            parent.save()
        } else {
            componentSchemas.forEach { it.save(stateId) }
        }
    }
}