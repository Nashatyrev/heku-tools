package tech.consensys.tmp

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.ThreadContext
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.core.appender.ConsoleAppender
import org.apache.logging.log4j.core.appender.routing.Route
import org.apache.logging.log4j.core.appender.routing.Routes
import org.apache.logging.log4j.core.appender.routing.RoutingAppender
import org.apache.logging.log4j.core.config.AbstractConfiguration
import org.apache.logging.log4j.core.impl.ContextAnchor
import org.apache.logging.log4j.core.layout.PatternLayout
import org.apache.logging.log4j.core.selector.BasicContextSelector
import tech.pegasys.heku.util.config.LoggingConfigExt
import tech.pegasys.teku.infrastructure.logging.LoggingDestination
import java.util.concurrent.Executors


fun main() {
    val loggingConfigExt = LoggingConfigExt().also {
        it.consoleLevel = Level.WARN
        it.logConfigBuilder.destination(LoggingDestination.CONSOLE)
        it.logConfigBuilder.logDirectory(".")
    }
//    HekuLoggingConfigurator().startLogging(loggingConfigExt)
    val ctx = LogManager.getContext(false) as LoggerContext
    val ctxConfiguration = ctx.configuration as AbstractConfiguration
    val appender1 = ConsoleAppender.newBuilder()
        .setImmediateFlush(true)
        .setName("AAA1")
        .setConfiguration(ctxConfiguration)
        .setLayout(
            PatternLayout.newBuilder()
                .withPattern("[1] %d{HH:mm:ss.SSS} | %t | %-5level | %c{1} | %msg%n")
                .build()
        )
        .build()
    ctxConfiguration.addAppender(appender1)
    appender1.start()

    val appender2 = ConsoleAppender.newBuilder()
        .setImmediateFlush(true)
        .setName("AAA2")
        .setConfiguration(ctxConfiguration)
        .setLayout(
            PatternLayout.newBuilder()
                .withPattern("[2] %d{HH:mm:ss.SSS} | %t | %-5level | %c{1} | %msg%n")
                .build()
        )
        .build()
    ctxConfiguration.addAppender(appender2)
    appender2.start()

    val defaultAppender = ConsoleAppender.newBuilder()
        .setImmediateFlush(true)
        .setName("AAA3")
        .setConfiguration(ctxConfiguration)
        .setLayout(
            PatternLayout.newBuilder()
                .withPattern("[?] %d{HH:mm:ss.SSS} | %t | %-5level | %c{1} | %msg%n")
                .build()
        )
        .build()
    ctxConfiguration.addAppender(defaultAppender)
    defaultAppender.start()

    val routeAppender = RoutingAppender.newBuilder()
        .setConfiguration(ctxConfiguration)
        .withRoutes(
            Routes.newBuilder()
                .withPattern("\${ctx:threadName}")
                .withRoutes(
                    arrayOf(
                        Route.createRoute("AAA1", "1", null),
                        Route.createRoute("AAA2", "2", null),
                        Route.createRoute("AAA3", null, null)
                    )
                )
                .build()
        )
        .setName("ROUTER")
        .build()

    routeAppender.routes.routes

    ctxConfiguration.getRootLogger().addAppender(routeAppender, Level.INFO, null)
    routeAppender.start()
    ctx.updateLoggers()

    val logger = LogManager.getLogger("aaa")

    logger.info("Hello-default")

    ThreadContext.put("threadName", "1")
    logger.info("Hello-1")

    ThreadContext.put("threadName", "2")
    logger.info("Hello-2")
}

fun main__() {
    System.setProperty("Log4jContextSelector", BasicContextSelector::class.qualifiedName!!)

    val ctx1 = LoggerContext("ctx-1")
    val exec1 = Executors.newFixedThreadPool(2) { runnable ->
        val thread = Thread({
            ContextAnchor.THREAD_CONTEXT.set(ctx1)
            runnable.run()
        }, "exec-1")
        thread
    }

    val ctx2 = LoggerContext("ctx-2")
    val exec2 = Executors.newFixedThreadPool(2) { runnable ->
        val thread = Thread({
            ContextAnchor.THREAD_CONTEXT.set(ctx2)
            runnable.run()
        }, "exec-2")
        thread
    }

    val logger = LogManager.getLogger("aaa")
    logger.error("Hello")
}

