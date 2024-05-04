package tech.pegasys.heku.node

import tech.pegasys.teku.BeaconNode
import tech.pegasys.teku.BeaconNodeFacade
import tech.pegasys.teku.infrastructure.async.SafeFuture

class HekuNode(
    val beaconNode: BeaconNodeFacade
) {

    fun startOnNodeAsyncRunner(): SafeFuture<Void> {
        val startupRunner =
            beaconNode.beaconChainService.orElseThrow().beaconChainController.asyncRunnerFactory.create("startup", 1)
        return startupRunner.runAsync {
            (beaconNode as BeaconNode).start()
//            startupRunner.runAsync {
//                startupRunner.shutdown()
//            }
        }
    }
}