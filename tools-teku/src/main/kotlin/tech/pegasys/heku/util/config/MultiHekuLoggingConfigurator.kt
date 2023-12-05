package tech.pegasys.heku.util.config

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Layout
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.RollingFileAppender
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy
import org.apache.logging.log4j.core.appender.routing.Route
import org.apache.logging.log4j.core.appender.routing.Routes
import org.apache.logging.log4j.core.appender.routing.RoutingAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.filter.LevelRangeFilter
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.pattern.RegexReplacement
import java.util.regex.Pattern

class MultiHekuLoggingConfigurator {

    companion object {
        val THREAD_CONTEXT_APPENDER_SELECTOR_KEY: String = "HEKU_LOG_CONTEXT_ID"

        private val FILE_APPENDER_NAME_PREFIX = "teku-file-appender"
        private val CONSOLE_APPENDER_NAME_PREFIX = "teku-console-appender"
        private val NO_COLOR_LOG_REGEX = "[\\p{Cntrl}&&[^\r\n]]"
    }

    fun startLogging(configs: List<LoggingConfigExt>) {
        val ctx = LogManager.getContext(false) as LoggerContext
        val ctxConfiguration = ctx.configuration as AbstractConfiguration

        val fileAppenders = configs
            .withIndex()
            .map { createFileAppender(ctxConfiguration, it.value, it.index.toString()) }
        val consoleAppenders = configs
            .withIndex()
            .map { createConsoleAppender(ctxConfiguration, it.value, it.index.toString()) }
        val defaultConsoleAppender = createConsoleAppender(ctxConfiguration, "??", Level.DEBUG, true)

        val consoleRouteAppender =
            createRoutingAppender(ctxConfiguration, consoleAppenders, "CONSOLE_ROUTER", defaultConsoleAppender)
        val fileRouteAppender = createRoutingAppender(ctxConfiguration, fileAppenders, "FILE_ROUTER")

        addAppenderToRootLogger(ctxConfiguration, consoleRouteAppender)
        addAppenderToRootLogger(ctxConfiguration, fileRouteAppender)
    }

    private fun addAppenderToRootLogger(configuration: AbstractConfiguration, appender: Appender) {
        configuration.rootLogger.addAppender(appender, Level.DEBUG, null)
    }

    private fun createRoutingAppender(
        l4jConfig: AbstractConfiguration,
        appenders: List<Appender>,
        name: String,
        defaultAppender: Appender? = null
    ): RoutingAppender {
        val routes = appenders.withIndex()
            .map { (index, app) ->
                Route.createRoute(app.name, index.toString(), null)
            }
        val routesAndDefault = routes +
                if (defaultAppender != null) listOf(Route.createRoute(defaultAppender.name, null, null))
                else emptyList()

        val ret = RoutingAppender.newBuilder()
            .setConfiguration(l4jConfig)
            .withRoutes(
                Routes.newBuilder()
                    .withPattern("\${ctx:$THREAD_CONTEXT_APPENDER_SELECTOR_KEY}")
                    .withRoutes(routesAndDefault.toTypedArray())
                    .build()
            )
            .setName(name)
            .build()

        ret.start()
        return ret;
    }

    private fun consoleAppenderLayout(configuration: AbstractConfiguration, contextId: String, consoleOmitStackTraces: Boolean): PatternLayout {
        val logReplacement = Pattern.compile(NO_COLOR_LOG_REGEX)
        return PatternLayout.newBuilder()
            .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
            .withAlwaysWriteExceptions(!consoleOmitStackTraces)
            .withNoConsoleNoAnsi(true)
            .withConfiguration(configuration)
            .withPattern("[$contextId] %d{HH:mm:ss.SSS} %-5level - %msg%n")
            .build()
    }

    private fun createConsoleAppender(l4jConfig: AbstractConfiguration, config: LoggingConfigExt, contextId: String): Appender =
        createConsoleAppender(l4jConfig, contextId, config.consoleStatusLevel, config.consoleOmitStackTraces)


    private fun createConsoleAppender(
        l4jConfig: AbstractConfiguration,
        contextId: String,
        consoleStatusLevel: Level,
        consoleOmitStackTraces: Boolean
    ): Appender {
        val layout: Layout<*> = consoleAppenderLayout(l4jConfig, contextId, consoleOmitStackTraces)
        val consoleAppender: Appender = ConsoleAppender.newBuilder()
            .setName("$CONSOLE_APPENDER_NAME_PREFIX-$contextId")
            .setLayout(layout)
            .setImmediateFlush(true)
            .setFilter(LevelRangeFilter.createFilter(Level.OFF, consoleStatusLevel, null, null))
            .build()
        l4jConfig.addAppender(consoleAppender)
        consoleAppender.start()
        return consoleAppender
    }

    private fun fileAppenderLayout(l4jConfig: AbstractConfiguration): PatternLayout {
        val logReplacement =
            Pattern.compile(NO_COLOR_LOG_REGEX)
        return PatternLayout.newBuilder()
            .withRegexReplacement(RegexReplacement.createRegexReplacement(logReplacement, ""))
            .withPatternSelector(FilePatternSelector(l4jConfig))
            .withAlwaysWriteExceptions(false)
            .withConfiguration(l4jConfig)
            .build()
    }

    private fun createFileAppender(l4jConfig: AbstractConfiguration, config: LoggingConfigExt, contextId: String): Appender {
        val layout: Layout<*> = fileAppenderLayout(l4jConfig)
        val fileAppender: Appender = RollingFileAppender.newBuilder()
            .setName("$FILE_APPENDER_NAME_PREFIX-$contextId")
            .withAppend(true)
            .setImmediateFlush(false)
            .setLayout(layout)
            .withFileName(config.logConfig.logFile)
            .withFilePattern(config.logConfig.logFileNamePattern)
            .withPolicy(
                CompositeTriggeringPolicy.createPolicy(
                    TimeBasedTriggeringPolicy.newBuilder()
                        .withInterval(1)
                        .withModulate(true)
                        .build()
                )
            )
            .build()
        l4jConfig.addAppender(fileAppender)
        fileAppender.start()
        return fileAppender
    }

}