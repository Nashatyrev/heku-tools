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
