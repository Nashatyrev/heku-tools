package tech.pegasys.heku.util.config

import io.libp2p.core.ConnectionClosedException
import io.libp2p.etc.util.netty.mux.AbstractMuxHandler
import io.libp2p.security.noise.NoiseXXSecureChannel
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Filter
import org.ethereum.beacon.discovery.pipeline.handler.BadPacketHandler
import org.ethereum.beacon.discovery.schema.NodeSession
import org.ethereum.beacon.discovery.task.RecursiveLookupTask
import tech.pegasys.heku.util.config.logging.RawFilter
import tech.pegasys.teku.bls.BLS
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.logging.LoggingConfig
import tech.pegasys.teku.networking.eth2.peers.Eth2PeerManager
import tech.pegasys.teku.networking.eth2.rpc.core.Eth2IncomingRequestHandler
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPeer
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifierImpl
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceUpdateData
import kotlin.io.path.absolutePathString

val NOISY_DEBUG_GENERAL_LOGGERS = listOf(
    "org.quartz",
) + listOf(
    // Libp2p
    NoiseXXSecureChannel::class,
    // Discovery
    NodeSession::class,
    BadPacketHandler::class,
    RecursiveLookupTask::class
).map { it.qualifiedName!! }

val NOISY_DEBUG_FORKCHOICE_LOGGERS = listOf(
    ForkChoiceUpdateData::class,
    ForkChoiceNotifierImpl::class
).map { it.qualifiedName!! }

val NOISY_LOG_FILTERS_BY_CLASS = listOf(
    AbstractMuxHandler::class to RawFilter.excludeByMessageLevelException(
        "Muxer exception",
        Level.DEBUG,
        ConnectionClosedException::class
    ),
    Eth2IncomingRequestHandler::class to RawFilter.excludeByMessage("Failed to receive incoming request data within"),
    Eth2IncomingRequestHandler::class to RawFilter.excludeByMessage("RPC Request stream closed prematurely"),
    LibP2PPeer::class to RawFilter.excludeByMessage("Failed to disconnect from"),
    LibP2PPeer::class to RawFilter.excludeByMessage("Disconnected from peer"),
    Eth2PeerManager::class to RawFilter.excludeByMessage("Failed to send status"),
    Eth2PeerManager::class to RawFilter.excludeByMessage("Ping request failed for peer"),
    BLS::class to RawFilter.excludeByMessage("Skipping bls verification.")
)

val NOISY_LOG_FILTERS =
    listOf(
        "${ResponseCallback::class.java.packageName}.RpcResponseCallback" to RawFilter.excludeByMessageAndLevel(
            "Responding to RPC request with error",
            Level.DEBUG
        ),
    ) + NOISY_LOG_FILTERS_BY_CLASS.map { (klass, filter) -> klass.qualifiedName!! to filter }

fun TekuConfiguration.startLogging(
    level: Level = Level.DEBUG,
    colored: Boolean = false,
    loggingModifier: LoggingConfigExt.() -> Unit = { }
): TekuConfiguration =
    this.startLogging {
        logConfigBuilder.logLevel(level)
        logConfigBuilder.colorEnabled(colored)
        loggingModifier(this)
    }

fun TekuConfiguration.startLogging(loggingModifier: LoggingConfigExt.() -> Unit): TekuConfiguration {
    val loggingConfigBuilder = createLoggingConfigBuilder(this)
    loggingModifier(loggingConfigBuilder)
    loggingConfigBuilder.addCustomLevel(NOISY_DEBUG_GENERAL_LOGGERS + NOISY_DEBUG_FORKCHOICE_LOGGERS, Level.INFO)
    NOISY_LOG_FILTERS.forEach { (logger, filter) ->
        loggingConfigBuilder.addFilter(logger, filter)
    }
    startLogging(loggingConfigBuilder)
    return this
}

fun TekuConfiguration.startLogging(config: LoggingConfigExt): TekuConfiguration {
    HekuLoggingConfigurator().startLogging(config)
    return this
}

fun createLoggingConfigBuilder(config: TekuConfiguration): LoggingConfigExt {
    return LoggingConfigExt().also {
        it.logConfigBuilder.dataDirectory(config.dataConfig().dataBasePath.absolutePathString())

    }
}

class LoggingConfigExt {

    val logConfigBuilder: LoggingConfig.LoggingConfigBuilder = LoggingConfig.builder()

    val filters = mutableListOf<Pair<String, Filter>>()
    val loggerLevels = mutableListOf<Pair<String, Level>>()
    var consoleLevel = Level.WARN

    fun addFilter(logger: String, filter: Filter) {
        filters += logger to filter
    }

    fun addCustomLevel(logger: String, level: Level) {
        loggerLevels += logger to level
    }

    fun addCustomLevel(loggers: List<String>, level: Level) {
        loggers.forEach { addCustomLevel(it, level) }
    }
}