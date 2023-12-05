package tech.pegasys.heku.node

import io.libp2p.core.dsl.DebugBuilder
import io.netty.channel.ChannelHandler
import kotlinx.coroutines.handleCoroutineException
import org.apache.logging.log4j.Level
import tech.pegasys.heku.util.beacon.HekuBeaconChainController
import tech.pegasys.heku.util.beacon.HekuDiscoveryNetworkBuilder
import tech.pegasys.heku.util.config.LoggingConfigExt
import tech.pegasys.heku.util.config.LoggingConfigExt.Companion.setDataPathFromTekuConfig
import tech.pegasys.heku.util.config.MultiHekuLoggingConfigurator
import tech.pegasys.heku.util.config.ThreadContextExecutorFactory
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.heku.util.net.libp2p.HekuLibP2PNetworkBuilder
import tech.pegasys.heku.util.setDefaultExceptionHandler
import tech.pegasys.teku.BeaconNode
import tech.pegasys.teku.NodeServiceConfigBuilder
import tech.pegasys.teku.TekuFacade
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingSupplier
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

    var isSyncChannels = false

    private fun buildConfigs(hekuNodeId: String?): Configs {
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

        val serviceConfig = object : NodeServiceConfigBuilder(config) {

            override fun createExecutorFactory(): ExecutorServiceFactory {
                val delegateExecutorFactory = executionServiceFactory ?: super.createExecutorFactory()
                return if (hekuNodeId != null) {
                    ThreadContextExecutorFactory(delegateExecutorFactory, MultiHekuLoggingConfigurator.THREAD_CONTEXT_APPENDER_SELECTOR_KEY, hekuNodeId)
                } else {
                    delegateExecutorFactory
                }
            }

            override fun createEventChannels(): EventChannels =
                if (isSyncChannels)
                    EventChannels.createSyncChannels(subscriberExceptionHandler, metricsEndpoint.metricsSystem)
                else
                    super.createEventChannels()

        }.build()

        return Configs(config, serviceConfig)
    }

    fun buildAndStart(): HekuNode {
        if (setDefaultExceptionHandler) {
            setDefaultExceptionHandler()
        }

        val (tekuConfig, serviceConfig) = buildConfigs(null)

        tekuConfig.startLogging(loggingConfig)

        val beaconNode = BeaconNode(tekuConfig, serviceConfig)
        beaconNode.start()
        return HekuNode(beaconNode)
    }

    private data class Configs(
        val tekuConfig: TekuConfiguration,
        val serviceConfig: ServiceConfig
    )

    companion object {

        fun buildAndStartAll(hekuBuilders: List<HekuNodeBuilder>): List<HekuNode> {
            setDefaultExceptionHandler()

            val configs = hekuBuilders
                .withIndex()
                .map { it.value.buildConfigs(it.index.toString()) }

            val logConfigs =
                hekuBuilders
                    .map { it.loggingConfig }
                    .zip(configs)
                    .map { (logConfig, configs) ->
                        logConfig.setDataPathFromTekuConfig(configs.tekuConfig)
                        logConfig
                    }

            MultiHekuLoggingConfigurator().startLogging(logConfigs)

            return configs.map {
                val startupRunner = it.serviceConfig.asyncRunnerFactory.create("startup", 1)
                val nodeFuture = startupRunner.runAsync( ExceptionThrowingSupplier {
                    val beaconNode = BeaconNode(it.tekuConfig, it.serviceConfig)
                    beaconNode.start()
                    beaconNode
                })
                HekuNode(nodeFuture.join())
            }
        }

        private fun DebugBuilder.addAll(other: DebugBuilder) {
            this.beforeSecureHandler.handlers += other.beforeSecureHandler.handlers
            this.afterSecureHandler.handlers += other.afterSecureHandler.handlers
            this.muxFramesHandler.handlers += other.muxFramesHandler.handlers
            this.streamPreHandler.handlers += other.streamPreHandler.handlers
            this.streamHandler.handlers += other.streamHandler.handlers
        }
    }
}