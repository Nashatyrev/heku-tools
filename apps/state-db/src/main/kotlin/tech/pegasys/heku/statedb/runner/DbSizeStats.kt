package tech.pegasys.heku.statedb.runner

import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.statedb.db.DiffId
import tech.pegasys.heku.statedb.db.LevelDbFactory
import tech.pegasys.heku.statedb.db.SimpleLevelDBDiffStorageFactory
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.util.beacon.spec.spec
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.type.ETime
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration
import java.nio.file.Path

fun main() {
    val schemasCount = 8

    val sizes = DbSizeStats().getSizes()

    val schemaSizes = sizes.values
        .fold(List(schemasCount) { 0L }) { acc, value ->
            acc.zip(value) { a, b -> a + (b ?: 0) }
        }

    val schemaCounts = sizes.values
        .fold(List(schemasCount) { 0 }) { acc, value ->
            acc.zip(value) { a, b -> a + if (b == null) 0 else 1 }
        }

    println(
        schemaCounts
            .zip(schemaSizes)
            .joinToString("\n") { "${it.first}\t${it.second}" }
    )
}

class DbSizeStats(
    val eth2Network: Eth2Network = Eth2Network.MAINNET,
    val dbPath: String = "./work.dir/incssz.dump.$eth2Network/inc.state.db",
    val startEpoch: Epoch =
        eth2Network.spec().getInstantSpecAtMilestone(SpecMilestone.ALTAIR).slot.asSlot().epoch + 1,
    val endEpoch: Epoch = startEpoch + ETime.EEAR.epochs,
    val schemasCount: Int = 8
) {
    val db = LevelDbFactory.create(
        KvStoreConfiguration()
            .withDatabaseDir(Path.of(dbPath))
    )

    val storageFactory = SimpleLevelDBDiffStorageFactory(db)
    val stores = (0 until schemasCount)
        .map { storageFactory.createStore() }

    fun getSizes(): Map<Epoch, List<Long?>> = runBlocking {
        (startEpoch..endEpoch)
            .associateWith { epoch ->
                val ret = stores.map { store ->
                    try {
                        store.loadDiff(DiffId(StateId(epoch.startSlot))).size().toLong()
                    } catch (e: Exception) {
                        null
                    }
                }
                log("Loaded $epoch: $ret")
                ret
            }
    }
}