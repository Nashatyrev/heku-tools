package tech.pegasys.heku.statedb.ssz

import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.util.ext.getOrCompute
import tech.pegasys.teku.infrastructure.collections.LimitedMap

fun interface IndexedSszSource {

    suspend fun loadSsz(stateId: StateId): IndexedSsz

    companion object {
        val NOOP = IndexedSszSource { throw RuntimeException("NOOP instance") }
    }
}

fun IndexedSszSource.cached(maxSize: Int): IndexedSszSource = CachedSszSource(this, maxSize)

private class CachedSszSource(
    val delegate: IndexedSszSource,
    maxSize: Int
) : IndexedSszSource {
    val cache = LimitedMap.createNonSynchronized<StateId, IndexedSsz>(maxSize)

    override suspend fun loadSsz(stateId: StateId): IndexedSsz =
        cache.getOrCompute(stateId) { delegate.loadSsz(it) }
}