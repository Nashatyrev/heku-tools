package tech.pegasys.heku.statedb.legacy

import org.apache.logging.log4j.Level
import tech.pegasys.heku.util.beacon.bls.BlsUtils
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.generatePrivateKeyFromSeed
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.storage.server.StateStorageMode
import java.nio.file.Path

fun main() {
    RunArchiveNode().run()
}

class RunArchiveNode {
    val eth2Network: Eth2Network = Eth2Network.MAINNET
    val port: Int = 9004
    val dataPath: String = "./work.dir/teku.archive.$eth2Network"
    val privKey = generatePrivateKeyFromSeed(21)
    val logLevel = Level.INFO

    fun run() {
        setDefaultExceptionHandler()

        BlsUtils.globallyDisableBls()

        val config = TekuConfiguration.builder()
            .eth2NetworkConfig {
                it
                    .applyNetworkDefaults(eth2Network)
            }
            .network {
                it
                    .listenPort(port)
                    .setPrivateKeySource { privKey.bytes().toBytes() }
            }
            .data {
                it.dataBasePath(Path.of(dataPath))
            }
            .storageConfiguration {
                it.dataStorageMode(StateStorageMode.ARCHIVE)
            }
            .executionLayer { it.engineEndpoint("unsafe-test-stub") }
            .sync { it.isSyncEnabled(true) }
            .build()
            .startLogging(logLevel)

        val beaconNode = TekuFacade.startBeaconNode(config)

        Thread.sleep(10000000000L)
    }
}