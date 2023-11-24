package tech.pegasys.heku.node

import io.netty.channel.ChannelHandler
import org.apache.logging.log4j.Level
import tech.pegasys.heku.util.beacon.HekuBeaconChainController
import tech.pegasys.heku.util.beacon.HekuDiscoveryNetworkBuilder
import tech.pegasys.heku.util.config.LoggingConfigExt
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.net.libp2p.HekuLibP2PNetworkBuilder
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory

class HekuNodeBuilder {

    val tekuConfigBuilder = TekuConfiguration.builder()

    val libp2pNetworkAfterSecureHandlers = mutableListOf<ChannelHandler>()
    val libp2pNetworkBeforeSecureHandlers = mutableListOf<ChannelHandler>()
    val libp2pNetworkMuxHandlers = mutableListOf<ChannelHandler>()

    var loggingConfig = LoggingConfigExt.createDefault()

    private var setDefaultExceptionHandler = true

    fun buildAndStart(): HekuNode {
        if (setDefaultExceptionHandler) {
            setDefaultExceptionHandler()
        }

        val bcControllerFactory = BeaconChainControllerFactory { serviceConfig, beaconChainConfig ->

            val libP2PNetworkBuilder = HekuLibP2PNetworkBuilder()
            val discoveryNetworkBuilder = HekuDiscoveryNetworkBuilder()

            HekuBeaconChainController(
                serviceConfig,
                beaconChainConfig,
                libP2PNetworkBuilder,
                discoveryNetworkBuilder,
                true,
                true,
                true
            )
        }
        val config = tekuConfigBuilder
            .beaconChainControllerFactory(bcControllerFactory)
            .build()
            .startLogging(loggingConfig)

        val nodeFacade = TekuFacade.startBeaconNode(config)
        return HekuNode(nodeFacade)
    }
}