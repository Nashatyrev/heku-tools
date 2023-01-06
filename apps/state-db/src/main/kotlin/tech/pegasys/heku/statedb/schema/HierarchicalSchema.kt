package tech.pegasys.heku.statedb.schema

import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.statedb.db.DiffId
import tech.pegasys.heku.statedb.db.DiffStore
import tech.pegasys.heku.statedb.diff.Diff
import tech.pegasys.heku.statedb.diff.DiffResult
import tech.pegasys.heku.statedb.diff.DiffSchema
import tech.pegasys.heku.statedb.schema.DagSchema.Companion.loadOrEmpty
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
import tech.pegasys.heku.util.ext.getOrCompute
import tech.pegasys.teku.infrastructure.collections.LimitedMap

abstract class HierarchicalSchema(
    val diffSchema: DiffSchema,
    val diffStore: DiffStore,
    val sszSource: IndexedSszSource,
    val delegateToParent: Boolean = true,
    val name: String = "<unnamed>",
    diffCacheSize: Int = 0,
) : DagSchema {

    val diffCache = LimitedMap.createSynchronized<DiffId, Diff>(diffCacheSize)
    val resultCache = LimitedMap.createSynchronized<StateId, DiffResult>(diffCacheSize)

    abstract fun getParent(stateId: StateId): DagSchemaVertex?

    override fun getParents(stateId: StateId): List<DagSchemaVertex> =
        getParent(stateId)?.let { listOf(it) } ?: emptyList()

    override suspend fun load(stateId: StateId): DiffResult {
        val parentSchema = getParent(stateId)
        val parentSsz = parentSchema.loadOrEmpty()

        return if (delegateToParent && parentSchema?.stateId == stateId) {
            parentSsz
        } else {
            resultCache.getOrCompute(stateId) {
                val diff = loadDiff(DiffId(stateId))
                diff.apply(parentSsz)
            }
        }
    }

    open suspend fun loadDiff(diffId: DiffId): Diff =
        diffCache.getOrCompute(diffId) {
            val diffBytes = diffStore.loadDiff(diffId)
            diffSchema.deserializeDiff(diffBytes)
        }

    override suspend fun save(stateId: StateId) {
        val parent = getParent(stateId)
        if (delegateToParent && parent?.stateId == stateId) {
            parent.save()
        } else {
            val diffId = DiffId(stateId)
            if (!diffStore.hasDiff(diffId)) {
                val thisSsz = sszSource.loadSsz(stateId)
                val parentSsz =
                    if (parent != null) sszSource.loadSsz(parent.stateId)
                    else IndexedSsz.EMPTY
                val diff = diffSchema.diff(AlignedIndexedSsz.create(parentSsz, thisSsz))
                diffStore.storeDiff(diffId, diff.serialize())

                parent?.save()
            }
        }
    }

    override fun toString(): String {
        return "HierarchicalSchema($name)"
    }
}
