package tech.pegasys.heku.samples

import org.apache.logging.log4j.Level
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.generatePrivateKeyFromSeed
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.spec.networks.Eth2Network
import java.nio.file.Path

class SimpleTekuNode {

    val eth2Network: Eth2Network = Eth2Network.MAINNET
    val port: Int = 9004
    val dataPath: String = "./work.dir/simple.teku.$eth2Network"
    val skeySeed: Long = 21
    val privKey = generatePrivateKeyFromSeed(skeySeed)
    //    val checkpointSyncUrl: String =
//        "https://205kCHUGgGtmuVU9OD1m672VCuN:e351a08e778d172630c1d04bf027b222@eth2-beacon-mainnet.infura.io/eth/v2/debug/beacon/states/finalized"
    val logLevel = Level.DEBUG

    @Test
    fun runDefaultTekuNode() {
        setDefaultExceptionHandler()

        val config = TekuConfiguration.builder()
            .eth2NetworkConfig {
                it
                    .applyNetworkDefaults(eth2Network)
//                    .customInitialState(checkpointSyncUrl)
            }
            .network {
                it
                    .listenPort(port)
                    .setPrivateKeySource { privKey.bytes().toBytes() }
            }
            .data { it.dataBasePath(Path.of(dataPath)) }
            .executionLayer { it.engineEndpoint("unsafe-test-stub") }
            .build()
            .startLogging(logLevel)

        val beaconNode = TekuFacade.startBeaconNode(config)

        Thread.sleep(100000000L)
    }
}