package tech.pegasys.heku.util.discovery.disv5.system

import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tech.pegasys.heku.util.net.discovery.discv5.system.DiscoverySystemExtBuilder
import tech.pegasys.heku.util.net.discovery.discv5.system.descr
import tech.pegasys.heku.util.generatePrivateKeysFromSeed
import tech.pegasys.heku.util.ext.increasingSequence
import tech.pegasys.teku.spec.networks.Eth2Network
import java.net.InetAddress
import kotlin.streams.toList

class DiscoverySystemExtBuilderTest {

    @Test
    fun copyTest() {
        val b1 = DiscoverySystemExtBuilder()
        b1.port = 7777
        val b2 = b1.copy()

        assertThat(b2.port).isEqualTo(7777)
    }

    @Test
    fun launchHiveTest() {
        val eth2Network = Eth2Network.PRATER
        val numberOfNodes = 4
        val startPort = 19010

        val privateKeys = generatePrivateKeysFromSeed(numberOfNodes, 111)

        println("Launching discovery hive")
        val hive = runBlocking {
            DiscoverySystemExtBuilder().apply {
                network = eth2Network
                advertiseAddress = InetAddress.getByName("127.0.0.1")
            }.launchHive(privateKeys.asSequence(), startPort.increasingSequence())
                .onEach { println("Launched system") }
                .toList(mutableListOf())
        }
        println("Hive launched")

        val neighbourIds = hive
            .drop(1)
            .map { it.system.localNodeRecord.nodeId }
            .toSet()

        repeat(10) {
            val knownNodes = hive[0].system.streamLiveNodes().toList().distinct()
            println("Main node neighbours: ")
            println(knownNodes.map { "  " + it.descr }.joinToString("\n"))
            if (knownNodes.size >= numberOfNodes - 1) {
                if (knownNodes.map { it.nodeId }.all { it in neighbourIds }) {
                    return
                }
            }
            assertThat(it < 9)
            Thread.sleep(1000)
        }
    }
}