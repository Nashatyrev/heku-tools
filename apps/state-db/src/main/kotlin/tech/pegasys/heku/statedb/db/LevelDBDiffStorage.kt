package tech.pegasys.heku.statedb.db

import org.apache.tuweni.bytes.Bytes
import org.iq80.leveldb.DB
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.util.ext.fromUVariantBytes
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.ext.toUVariantBytes
import tech.pegasys.heku.util.type.slots

class LevelDBDiffStorage(
    val db: DB,
    val schemaId: Byte
) : DiffStore {

    private fun getKey(diffId: DiffId) = Bytes.wrap(Bytes.of(schemaId), diffId.key).toArrayUnsafe()

    override suspend fun hasDiff(diffId: DiffId): Boolean =
        db.get(getKey(diffId)) != null


    override suspend fun loadDiff(diffId: DiffId): Bytes =
        Bytes.wrap(db.get(getKey(diffId)))


    override suspend fun storeDiff(diffId: DiffId, bytes: Bytes) {
        db.put(getKey(diffId), bytes.toArrayUnsafe())
    }
}

class SimpleLevelDBDiffStorageFactory(
    val db: DB
) : DiffStoreFactory {
    var idCounter: Byte = 0

    override fun createStore(): DiffStore = LevelDBDiffStorage(db, idCounter++)

    fun trackingLastState(): LastStateTrackingDiffStorageFactory = TrackingLevelDBDiffStorageFactory(db, this)
}

interface LastStateTrackingDiffStorageFactory : DiffStoreFactory {

    fun getLatestState(): StateId?
}

class TrackingLevelDBDiffStorageFactory(
    val db: DB,
    val delegate: DiffStoreFactory
) : LastStateTrackingDiffStorageFactory {

    override fun getLatestState(): StateId? =
        db.get(LATEST_STATE_ID_KEY)?.toBytes()?.fromUVariantBytes()?.value?.slots
            ?.let { StateId(it) }

    override fun createStore(): DiffStore =
        LatestRecordingDiffStore(delegate.createStore())

    inner class LatestRecordingDiffStore(
        val delegate: DiffStore
    ) : DiffStore by delegate {
        override suspend fun storeDiff(diffId: DiffId, bytes: Bytes) {
            delegate.storeDiff(diffId, bytes)
            val value = diffId.stateId.slot.value.toUVariantBytes().toArrayUnsafe()
            db.put(LATEST_STATE_ID_KEY, value)
        }
    }

    companion object {
        val LATEST_STATE_ID_KEY = ByteArray(0)
    }
}
