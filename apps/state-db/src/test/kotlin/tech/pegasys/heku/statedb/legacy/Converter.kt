package tech.pegasys.heku.statedb.legacy

import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.statedb.db.*
import tech.pegasys.heku.statedb.runner.IncSszStateLoader
import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
import tech.pegasys.heku.util.beacon.spec
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.heku.util.type.epochs
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.networks.Eth2Network

fun main() {
    val converter = Converter()
//    converter.convert(StateId(converter.startStateEpoch.startSlot))
    converter.convert(StateId(131376.epochs.startSlot))
}

class Converter(
    val eth2Network: Eth2Network = Eth2Network.MAINNET,
    val dbPath: String = "./work.dir/incssz.dump.$eth2Network/inc.state.db",
    val startStateEpoch: Epoch =
        eth2Network.spec().getInstantSpecAtMilestone(SpecMilestone.ALTAIR).slot.asSlot().epoch + 1
) {

    class HackyDiffStorageFactory(val delegate: DiffStoreFactory) : DiffStoreFactory {
        override fun createStore(): DiffStore =
            HackyDiffStore(delegate.createStore())


        class HackyDiffStore(val delegate: DiffStore) : DiffStore by delegate {
            override suspend fun hasDiff(diffId: DiffId) = false
        }
    }

    val loader = IncSszStateLoader(eth2Network, dbPath, startStateEpoch)

    val storageFactory = HackyDiffStorageFactory(SimpleLevelDBDiffStorageFactory(loader.db))

    val sszSource = IndexedSszSource.createFromStateLoader(loader)
    val targetStorageSchema =
        StateStorageSchema(
            storageFactory,
            sszSource,
            startStateEpoch.startSlot
        ).schema

    fun convert(stateId: StateId) {
        runBlocking {
            targetStorageSchema.save(stateId)
        }
    }
}