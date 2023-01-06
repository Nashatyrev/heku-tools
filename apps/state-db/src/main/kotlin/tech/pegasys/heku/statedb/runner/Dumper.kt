package tech.pegasys.heku.statedb.runner

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import org.apache.logging.log4j.Level
import tech.pegasys.heku.statedb.db.LevelDbFactory
import tech.pegasys.heku.statedb.db.SimpleLevelDBDiffStorageFactory
import tech.pegasys.heku.statedb.db.StateAppender
import tech.pegasys.heku.statedb.db.StateStorageSchema
import tech.pegasys.heku.statedb.ssz.cached
import tech.pegasys.heku.util.beacon.spec
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.getPrivateField
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.heku.util.type.*
import tech.pegasys.teku.BeaconNodeFacade
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.events.EventChannels
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.datastructures.state.Checkpoint
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel
import tech.pegasys.teku.storage.server.kvstore.KvStoreConfiguration
import java.nio.file.Path
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

fun main() = Dumper().run()

class Dumper(
    val eth2Network: Eth2Network = Eth2Network.MAINNET,
    val dataPath: String = "./work.dir/incssz.dump.$eth2Network",
    val startState: String = "./work.dir/131073.epoch.state.ssz",
    val startStateEpoch: Epoch =
        eth2Network.spec().getInstantSpecAtMilestone(SpecMilestone.ALTAIR).slot.asSlot().epoch + 1,
    val endStateEpoch: Epoch =
        eth2Network.spec().getInstantSpecAtMilestone(SpecMilestone.BELLATRIX).slot.asSlot().epoch
) {

    @OptIn(ExperimentalTime::class)
    fun run() {
        setDefaultExceptionHandler()

        println("Starting from epoch $startStateEpoch... till $endStateEpoch")

        val db = LevelDbFactory.create(
            KvStoreConfiguration()
                .withDatabaseDir(Path.of(dataPath, "inc.state.db"))
        )
        val storageFactory = SimpleLevelDBDiffStorageFactory(db)
            .trackingLastState()

        println("Last recorded state: " + storageFactory.getLatestState())

        val stateAppender = StateAppender(eth2Network)
        val cachedSszSource = stateAppender
            .selfFeedingIndexedSszSource
            .cached(128)
        val storageSchema =
            StateStorageSchema(
                storageFactory,
                cachedSszSource,
                startStateEpoch.startSlot
            )
        stateAppender.initStorageSchema(storageSchema.schema)

        val beaconNode = startBeaconNode()

        val beaconChainController = beaconNode.beaconChainService.orElseThrow().beaconChainController
        val chainDataClient = beaconChainController.combinedChainDataClient

        val finalizedFlow = createFinalizedCheckpointFlow(beaconNode)
            .onEach {
                log("New checkpoint: $it")
            }
        val newSlotsFlow = createNewSlotsFlow(storageFactory.getLatestState()?.slot, finalizedFlow)
        val newStatesFlow = newSlotsFlow
            .map { slot ->
                val maybeState = chainDataClient.getStateAtSlotExact(slot.uint64).await()
                maybeState.orElseThrow { IllegalStateException("Unable to find finalized state: ${slot.toStringLong()}") }
            }

        runBlocking {

            newStatesFlow
                .buffer(128)
                .collect { state ->
                val t = measureTime {
                    stateAppender.append(state)
                }
                log("State ${state.slot.asSlot().toStringLong()} appended in $t")
            }
//            finalizedFlow.collect { finalizedCheckpoint ->
//                val slot = finalizedCheckpoint.epoch.asEpochs().startSlot
//                println("Getting state for $slot...")
//                val stateFuture =
//                    chainDataClient.getStateAtSlotExact(slot.uint64)
//                val maybeState = stateFuture.await().toNullable()
//                println("Got state: ${maybeState?.slot}")
//            }
        }
    }

    fun createNewSlotsFlow(latestStoredState: Slot?, checkpointFlow: Flow<Checkpoint>): Flow<Slot> {
        return flow {
            var nextWantedSlot =
                if (latestStoredState == null)
                    startStateEpoch.startSlot
                else
                    latestStoredState + 1.epochs

            checkpointFlow.collect {
                while (it.epoch.asEpochs().startSlot >= nextWantedSlot) {
                    emit(nextWantedSlot)
                    nextWantedSlot += 1.epochs
                }
            }
        }
    }

    fun createFinalizedCheckpointFlow(beaconNode: BeaconNodeFacade): Flow<Checkpoint> {
        val beaconChainController = beaconNode.beaconChainService.orElseThrow().beaconChainController
        val eventChannels = beaconChainController.getPrivateField("eventChannels") as EventChannels

        return callbackFlow {
            eventChannels.subscribe(FinalizedCheckpointChannel::class.java,
                FinalizedCheckpointChannel { checkpoint, b ->
                    trySend(checkpoint)
                })
            awaitClose()
        }
    }

    fun startBeaconNode(): BeaconNodeFacade {
        val config = TekuConfiguration.builder()
            .eth2NetworkConfig {
                it
                    .applyNetworkDefaults(eth2Network)
                    .customInitialState(startState)
            }
            .data { it.dataBasePath(Path.of(dataPath)) }
            .executionLayer { it.engineEndpoint("unsafe-test-stub") }
            .build()
            .startLogging(Level.INFO)

        return TekuFacade.startBeaconNode(config)
    }
}