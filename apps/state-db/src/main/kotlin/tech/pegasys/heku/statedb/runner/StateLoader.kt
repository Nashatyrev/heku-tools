package tech.pegasys.heku.statedb.runner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.beacon.spec
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.readBytesGzipped
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.ssz.sos.SimpleSszReader
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import java.net.URI
import java.nio.file.Path

interface StateLoader {

    suspend fun loadState(slot: Slot): BeaconState
    suspend fun loadState(epoch: Epoch): BeaconState = loadState(epoch.startSlot)
}

class NodeApiStateLoader(
    val spec: Spec,
    val url: URI
) : StateLoader {

    override suspend fun loadState(slot: Slot): BeaconState {
        val targetUrl = url.resolve("/eth/v2/debug/beacon/states/$slot").toURL()
        val stateBytes = withContext(Dispatchers.IO) {
            val connection = targetUrl.openConnection()
            connection.setRequestProperty("Accept", "application/octet-stream")
            val bytesList = connection.getInputStream().use { inputStream ->
                generateSequence {
                    Bytes.wrap(inputStream.readNBytes(1024 * 1024))
                }
                    .onEach { print(".") }
                    .takeWhile { !it.isEmpty }
                    .toList()
            }
            Bytes.wrap(bytesList)
        }
        return spec.deserializeBeaconState(stateBytes)
    }
}

class FileEpochStateLoader(
    val eth2Network: Eth2Network,
    val statesDir: File
) : StateLoader {

    private fun getSchema(slot: Slot) =
        eth2Network.spec().spec.atSlot(slot.uint64).schemaDefinitions.beaconStateSchema


    override suspend fun loadState(slot: Slot): BeaconState {
        val stateFile = File(statesDir, "state-$slot.ssz.gz")
        if (!stateFile.exists()) {
            throw IllegalArgumentException("Couldn't find state file $stateFile")
        }
        val stateSsz = stateFile.readBytesGzipped()
        return getSchema(slot).sszDeserialize(SimpleSszReader(stateSsz))
    }

    override suspend fun loadState(epoch: Epoch): BeaconState {
        var curSlot = epoch.startSlot
        while (curSlot < epoch.endSlot) {
            try {
                return loadState(curSlot)
            } catch (e: IllegalArgumentException) {
                // do nothing
            }
            curSlot++
        }
        throw IllegalArgumentException("Couldn't find state for epoch $epoch")
    }
}

class TekuDbStateLoader(
    val eth2Network: Eth2Network,
    val dataPath: String
) : StateLoader {

    val beaconNode = run {
        setDefaultExceptionHandler()

        val config = TekuConfiguration.builder()
            .eth2NetworkConfig {
                it.applyNetworkDefaults(eth2Network)
            }
            .network {
                it.isEnabled(false)
            }
            .data { it.dataBasePath(Path.of(dataPath)) }
            .executionLayer { it.engineEndpoint("unsafe-test-stub") }
            .sync { it.isSyncEnabled(false) }
            .build()
            .startLogging()

        TekuFacade.startBeaconNode(config)
    }

    val recentChainData = beaconNode
        .beaconChainService.orElseThrow()
        .beaconChainController
        .recentChainData

    override suspend fun loadState(slot: Slot): BeaconState {
        val maybeState = recentChainData.retrieveStateInEffectAtSlot(slot.uint64).await()
        return maybeState.orElseThrow { IllegalArgumentException("No state for slot $slot") }
    }
}