@file:OptIn(ExperimentalTime::class)

package tech.pegasys.heku.statedb.db

import tech.pegasys.heku.statedb.schema.StateId
import tech.pegasys.heku.statedb.ssz.IndexedSszSource
import tech.pegasys.heku.util.beacon.spec.spec
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration
import java.nio.file.Path
import kotlin.time.ExperimentalTime

class IncSszStateLoader(
    val eth2Network: Eth2Network = Eth2Network.MAINNET,
    val dbPath: String = "./work.dir/incssz.dump.$eth2Network/inc.state.db",
    val startStateEpoch: Epoch =
        eth2Network.spec().getInstantSpecAtMilestone(SpecMilestone.ALTAIR).slot.asSlot().epoch + 1
) : StateLoader {
    val db = LevelDbFactory.create(
        KvStoreConfiguration()
            .withDatabaseDir(Path.of(dbPath))
    )
    val storageFactory = SimpleLevelDBDiffStorageFactory(db)
        .trackingLastState()

    val storageSchemaBuilder =
        StateStorageSchema(
            storageFactory,
            IndexedSszSource.NOOP,
            startStateEpoch.startSlot
        )
    val storageSchema = storageSchemaBuilder.schema

    val firstEpoch = startStateEpoch
    val lastEpoch = storageFactory.getLatestState()!!.slot.epoch

    override suspend fun loadState(slot: Slot): BeaconState {
        require(slot.epoch.startSlot == slot) { "Only epoch slots supported: $slot" }

        val stateSsz = storageSchema.load(StateId(slot)).getBytes().toDenseBytes()

        val specExt = eth2Network.spec()
        val instantSpec = specExt.getInstantSpecAt(specExt.getSlotStartTime(slot.uint64))
        val stateSchema = instantSpec.specVersion.schemaDefinitions.beaconStateSchema
        return stateSchema.sszDeserialize(stateSsz)
    }
}
