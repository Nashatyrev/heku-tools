package tech.pegasys.heku.node

import io.libp2p.core.dsl.DebugBuilder
import io.netty.channel.ChannelHandler
import kotlinx.coroutines.handleCoroutineException
import org.apache.logging.log4j.Level
import tech.pegasys.heku.util.beacon.HekuBeaconChainController
import tech.pegasys.heku.util.beacon.HekuDiscoveryNetworkBuilder
import tech.pegasys.heku.util.config.LoggingConfigExt
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.net.libp2p.HekuLibP2PNetworkBuilder
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.BeaconNode
import tech.pegasys.teku.NodeServiceConfigBuilder
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.async.ExecutorServiceFactory
import tech.pegasys.teku.infrastructure.events.EventChannels
import tech.pegasys.teku.service.serviceutils.ServiceConfig
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFactory

class HekuNodeBuilder {

    val tekuConfigBuilder = TekuConfiguration.builder()

    val libp2pNetworkHandlersBuilder = DebugBuilder()

    var loggingConfig = LoggingConfigExt.createDefault()

    private var setDefaultExceptionHandler = true

    var executionServiceFactory: ExecutorServiceFactory? = null

    fun buildAndStart(): HekuNode {
        if (setDefaultExceptionHandler) {
            setDefaultExceptionHandler()
        }

        val bcControllerFactory = BeaconChainControllerFactory { serviceConfig, beaconChainConfig ->

            val libP2PNetworkBuilder = HekuLibP2PNetworkBuilder().apply {
                hostBuilderPostModifier = { hostBuilder ->
                    hostBuilder.debug.addAll(libp2pNetworkHandlersBuilder)
                }
            }

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


        val serviceConfig = object : NodeServiceConfigBuilder(config) {

            override fun createExecutorFactory(): ExecutorServiceFactory =
                executionServiceFactory ?: super.createExecutorFactory()

            override fun createEventChannels(): EventChannels =
                EventChannels.createSyncChannels(subscriberExceptionHandler, metricsEndpoint.metricsSystem)

        }.build()

        val beaconNode = BeaconNode(config, serviceConfig)
        beaconNode.start()
        return HekuNode(beaconNode)
    }

    companion object {

        private fun DebugBuilder.addAll(other: DebugBuilder) {
            this.beforeSecureHandler.handlers += other.beforeSecureHandler.handlers
            this.afterSecureHandler.handlers += other.afterSecureHandler.handlers
            this.muxFramesHandler.handlers += other.muxFramesHandler.handlers
            this.streamPreHandler.handlers += other.streamPreHandler.handlers
            this.streamHandler.handlers += other.streamHandler.handlers
        }
    }
}