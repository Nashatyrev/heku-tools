package tech.pegasys.heku.statedb.schema

import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.statedb.StateLoader
import tech.pegasys.heku.util.ext.getOrCompute
import tech.pegasys.teku.infrastructure.collections.LimitedMap

fun interface IndexedSszSource {

    suspend fun loadSsz(stateId: StateId): IndexedSsz

    companion object {
        val NOOP = IndexedSszSource { throw RuntimeException("NOOP instance") }

        fun createFromStateLoader(stateLoader: StateLoader) = IndexedSszSource {
            val state = runBlocking {
                stateLoader.loadState(it.slot)
            }
            IndexedSsz.Companion.create(state)
        }
    }
}

fun IndexedSszSource.cached(maxSize: Int) = CachedSszSource(this, maxSize)

class CachedSszSource(
    val delegate: IndexedSszSource,
    maxSize: Int
) : IndexedSszSource {
    val cache = LimitedMap.createNonSynchronized<StateId, IndexedSsz>(maxSize)

    override suspend fun loadSsz(stateId: StateId): IndexedSsz =
        cache.getOrCompute(stateId) { delegate.loadSsz(it) }
}