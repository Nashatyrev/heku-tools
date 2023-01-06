package tech.pegasys.heku.util.net.libp2p.gossip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import tech.pegasys.heku.util.ext.MAX_CONCURRENCY
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.MTimestamped
import tech.pegasys.heku.util.ext.associateToStateMap
import tech.pegasys.heku.util.flow.bufferWithError
import tech.pegasys.heku.util.net.libp2p.gossip.GossipWireMessage.MessageSource.REMOTE

class GossipSubscriptionsTracker(
    gossipWireHandler: GossipWireHandler,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {

    private operator fun PeerSubscriptions.plus(msg: GossipWireMessage): PeerSubscriptions {
        val addedSubscriptions =
            msg.message.subscriptionsList.filter { it.subscribe }.map { it.topicid }.toSet()
        val removedSubscriptions =
            msg.message.subscriptionsList.filter { !it.subscribe }.map { it.topicid }.toSet()
        return PeerSubscriptions(
            msg.time,
            true,
            this.curSubscriptions + addedSubscriptions - removedSubscriptions
        )
    }

    val peersSubscriptionsFlow = gossipWireHandler.connectionsFlow
        .bufferWithError(64 * 1024, "GossipSubscriptionsTracker.peersSubscriptionsFlow")
        .flatMapMerge(MAX_CONCURRENCY) { gossipPeer ->
            flow {
                var curSubscription = PeerSubscriptions.NONE
                var lastMessageTime = MTime.ZERO
                gossipPeer.messages
                    .onEach { lastMessageTime = it.time }
                    .filter { it.messageSource == REMOTE && it.message.subscriptionsCount > 0 }
                    .collect { msg ->
                        curSubscription += msg
                        emit(curSubscription)
                    }
                // when peer disconnected
                emit(curSubscription.copy(time = lastMessageTime, online = false))
            }
                .map { gossipPeer.peerId to it }
        }

    val peerSubscriptionsMap = peersSubscriptionsFlow
        .associateToStateMap(scope, PeerSubscriptions.NONE)
}

data class PeerSubscriptions(
    override val time: MTime,
    val online: Boolean,
    val curSubscriptions: Set<String>
) : MTimestamped {

    val attestationSubnets = curSubscriptions
        .map { BeaconGossipTopic(it) }
        .filter { it.type == BeaconGossipTopic.TopicType.ATTESTATION_SUBNET }
        .map { it.subnetId }

    val syncSubnets = curSubscriptions
        .map { BeaconGossipTopic(it) }
        .filter { it.type == BeaconGossipTopic.TopicType.SYNC_COMMITTEE_SUBNET }
        .map { it.subnetId }

    companion object {
        val NONE = PeerSubscriptions(MTime.ZERO, false, emptySet())
    }
}