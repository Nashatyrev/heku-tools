package tech.pegasys.heku.util.beacon

import it.unimi.dsi.fastutil.ints.IntList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.shareIn
import tech.pegasys.heku.util.ext.callPrivateMethod
import tech.pegasys.heku.util.collections.FSet
import tech.pegasys.teku.BeaconNodeFacade
import tech.pegasys.teku.networking.eth2.ActiveEth2P2PNetwork
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer
import tech.pegasys.teku.networking.p2p.network.P2PNetwork
import tech.pegasys.teku.networking.p2p.peer.Peer
import tech.pegasys.teku.services.beaconchain.BeaconChainControllerFacade
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.datastructures.operations.Attestation
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState

fun BeaconChainControllerFacade.getExt() = BeaconChainControllerExt(this)
fun BeaconNodeFacade.getController() =
    this.beaconChainService.orElseThrow().beaconChainController.getExt()

/**
 * Wraps [BeaconChainControllerFacade] and adds useful utils to it
 */
class BeaconChainControllerExt internal constructor(
    val controller: BeaconChainControllerFacade,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val eth2P2PNetwork = controller.p2pNetwork as ActiveEth2P2PNetwork
    val p2pNetwork = eth2P2PNetwork.discoveryNetwork.orElseThrow() as P2PNetwork<Peer>
    val spec = controller.spec
    val specExt = BeaconChainUtil.chainHeadInitFuture(controller)
        .thenApply { SpecExt(spec, controller.combinedChainDataClient.genesisData.orElseThrow()) }

    val bestStateFlow =
        BestBeaconStatePoller(controller.combinedChainDataClient, scope).bestStateFlow

    val attestationTracker = bestStateFlow
        .thenApply { AttestationTracker(it, controller.spec) }

    private val eth2PeerUpdates = callbackFlow {
        val subscriptionId = eth2P2PNetwork.subscribeConnect { eth2Peer ->
            trySendBlocking(FSet.Update.createAdded(eth2Peer))
            eth2Peer.subscribeDisconnect { _, _ ->
                trySendBlocking(FSet.Update.createRemoved(eth2Peer))
            }
        }
        awaitClose {
            eth2P2PNetwork.unsubscribeConnect(subscriptionId)
        }
    }
        .shareIn(scope, SharingStarted.Eagerly)

    val activeEth2Peers: FSet<Eth2Peer> = FSet.createFromUpdates(eth2PeerUpdates)

    fun startGossip() = eth2P2PNetwork.callPrivateMethod("startGossip")
}

class AttestationTracker(
    private val stateFlow: StateFlow<BeaconState>,
    private val spec: Spec
) {

    fun getValidatorIndicesOrNull(att: Attestation): IntList? =
        try {
            getValidatorIndices(att)
        } catch (e: IllegalArgumentException) {
            null
        }

    fun getValidatorIndices(att: Attestation): IntList =
        getValidatorIndices(stateFlow.value, spec, att)

    private fun getValidatorIndices(state: BeaconState, spec: Spec, att: Attestation): IntList {
        return spec.getAttestingIndices(state, att.data, att.aggregationBits)
    }
}