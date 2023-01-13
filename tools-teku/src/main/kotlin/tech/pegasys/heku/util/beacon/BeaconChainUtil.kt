package tech.pegasys.heku.util.beacon

import tech.pegasys.teku.BeaconNodeFacade
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade

fun BeaconNodeFacade.waitForHead(): BeaconNodeFacade {
    BeaconChainUtil.chainHeadInitFuture(this).join()
    return this
}

class BeaconChainUtil {

    companion object {

        fun chainHeadInitFuture(beaconChainController: BeaconChainControllerFacade): SafeFuture<Unit> {
            val ret = SafeFuture<Unit>()
            beaconChainController.recentChainData.subscribeBestBlockInitialized {
                ret.complete(null)
            }
            return ret
        }

        fun chainHeadInitFuture(node: BeaconNodeFacade): SafeFuture<Unit> =
            chainHeadInitFuture(node.beaconChainService.orElseThrow().beaconChainController)
    }
}
