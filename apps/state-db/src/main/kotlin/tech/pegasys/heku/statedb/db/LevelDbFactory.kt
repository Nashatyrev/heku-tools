package tech.pegasys.heku.statedb.db

import org.fusesource.leveldbjni.JniDBFactory
import org.iq80.leveldb.DB
import org.iq80.leveldb.Options
import tech.pegasys.teku.storage.server.DatabaseStorageException
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration
import java.io.IOException

object LevelDbFactory {

    fun create(
        configuration: KvStoreConfiguration = KvStoreConfiguration(),
    ): DB {
        val options: Options = Options()
            .createIfMissing(true)
            .maxOpenFiles(configuration.leveldbMaxOpenFiles)
            .blockSize(configuration.leveldbBlockSize)
            .writeBufferSize(configuration.leveldbWriteBufferSize)
        return try {
            JniDBFactory.factory.open(configuration.databaseDir.toFile(), options)
        } catch (e: IOException) {
            throw DatabaseStorageException.unrecoverable("Failed to open database", e)
        }
    }
}
