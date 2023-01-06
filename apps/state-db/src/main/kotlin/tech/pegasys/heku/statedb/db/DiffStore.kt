package tech.pegasys.heku.statedb.db

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.util.ext.toUVariantBytes

data class DiffId(
    val stateId: StateId
) {
    val key get() = stateId.slot.value.toInt().toUVariantBytes()
}

interface DiffStore {

    suspend fun hasDiff(diffId: DiffId): Boolean

    suspend fun loadDiff(diffId: DiffId): Bytes

    suspend fun storeDiff(diffId: DiffId, bytes: Bytes)
}

fun interface DiffStoreFactory {
    fun createStore(): DiffStore
}

class MemDiffStore : DiffStore {
    val map = mutableMapOf<DiffId, Bytes>()

    override suspend fun hasDiff(diffId: DiffId): Boolean = diffId in map

    override suspend fun loadDiff(diffId: DiffId): Bytes {
        return map[diffId] ?: throw NoSuchElementException("No diff found: $diffId")
    }

    override suspend fun storeDiff(diffId: DiffId, bytes: Bytes) {
        map[diffId] = bytes
    }
}

