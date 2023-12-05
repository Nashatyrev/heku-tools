package tech.consensys.linea

import tech.consensys.linea.util.libp2p.ConnectionsTracker
import tech.pegasys.heku.util.ext.toUInt64
import tech.pegasys.teku.bls.BLSKeyPair
import tech.pegasys.teku.spec.TestSpecFactory
import kotlin.time.Duration.Companion.milliseconds

fun main() {
    LineaTekuNetwork().run()
}

class LineaTekuNetwork(
    val nodeFactory: LineaTeku = LineaTeku(
        validatorsCount = 64,
        connectionLatency = 100.milliseconds,
        executionDelay = 250.milliseconds,
        spec = TestSpecFactory.createMinimalBellatrix {
        it
            .secondsPerSlot(2)
            .slotsPerEpoch(1)
            .eth1FollowDistance(1.toUInt64())
            .altairBuilder {
                it
                    // TODO: can't change NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT
                    .syncCommitteeSize(4)
            }
    },
    ),
    val nodeCount: Int = 16
) {
    val connectionsTracker = ConnectionsTracker()

    fun run() {
        nodeFactory.resetWithNewGenesis()

        val validatorsByNodes = splitValidatorsByNodes()
        val bootNode = nodeFactory.runNode(0, validatorsByNodes[0], null, true, connectionsTracker)
        val bootNodeEnr = bootNode.beaconChainService.orElseThrow().beaconChainController.p2pNetwork.enr.orElseThrow()
        val otherNodes = (1 until nodeCount)
            .map { idx ->
                nodeFactory.runNode(idx, validatorsByNodes[idx], bootNodeEnr, false, connectionsTracker)
            }
        val allNodes = listOf(bootNode) + otherNodes

        while (true) {
            Thread.sleep(3000)
            println("Connections: active: ${connectionsTracker.activeConnections.size}, total: ${connectionsTracker.allConnections.size}")
        }
    }

    private fun splitValidatorsByNodes(): List<List<BLSKeyPair>> {
        val valCnt = nodeFactory.validatorsCount
        require(nodeCount <= valCnt)

        val n = valCnt / nodeCount
        val m  = valCnt % nodeCount
        val countsByNode = List(m) { n + 1 } + List(nodeCount - m) { n }
        val valNodes = countsByNode
            .withIndex()
            .flatMap { v -> List(v.value) { v.index } }
        val ret = nodeFactory.validatorKeys
            .zip(valNodes)
            .groupBy { it.second }
            .map {
                it.value
                    .map { it.first }
            }
        return ret
    }
}