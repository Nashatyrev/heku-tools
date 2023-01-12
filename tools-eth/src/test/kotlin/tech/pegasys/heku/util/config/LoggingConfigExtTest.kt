package tech.pegasys.heku.util.config

import org.apache.logging.log4j.Level
import org.apache.logging.log4j.LogManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tech.pegasys.heku.util.config.LoggingConfigExt
import tech.pegasys.heku.util.config.logging.RawFilter
import tech.pegasys.heku.util.config.startLogging
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.logging.LoggingConfig
import java.io.File

class LoggingConfigExtTest {

    @field:TempDir
    lateinit var tmpDir: File
    val logFileName = "test.log"

    @AfterEach
    fun cleanup() {
        LogManager.shutdown(true, true)
    }

    @Disabled
    @Test
    fun test1() {
        val configBuilder = LoggingConfig.builder()
            .dataDirectory(tmpDir.canonicalPath)
            .logFileName(logFileName)
            .logLevel(Level.DEBUG)
        val configExt = LoggingConfigExt().also {
            it.addCustomLevel("net.a", Level.INFO)
            it.addFilter("net.b", RawFilter.excludeByMessage("aaa"))
            it.addFilter("net.b", RawFilter.excludeByMessage("ccc"))
        }

        TekuConfiguration.builder()
            .data { it.dataBasePath(tmpDir.toPath()) }
            .build()
            .startLogging(configExt)

        val logA = LogManager.getLogger("net.a")
        val logB = LogManager.getLogger("net.b")
        val logC = LogManager.getLogger("net.c")
        val log = LogManager.getLogger("net")

        logA.debug("a-debug-1")
        logA.info("a-info-1")
        logA.info("a-info-2 aaa")
        logB.debug("b-debug-1 aaa qqq")
        logB.debug("b-debug-1 bbb qqq")
        logB.debug("b-debug-1 ccc qqq")
        logC.debug("c-debug-1")
        logC.info("c-info-1")
        log.debug("debug-1 ccc qqq")

        log.warn("warn-1")

        LogManager.shutdown(false, true)

        val entries = File(tmpDir, logFileName).readLines()
        entries.forEach { println(it) }
    }
}