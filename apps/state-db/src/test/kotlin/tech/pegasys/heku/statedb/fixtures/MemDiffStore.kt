package tech.pegasys.heku.statedb.fixtures

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.db.DiffId
import tech.pegasys.heku.statedb.db.DiffStore

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

