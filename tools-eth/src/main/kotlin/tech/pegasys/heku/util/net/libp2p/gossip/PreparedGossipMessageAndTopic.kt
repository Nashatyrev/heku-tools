package tech.pegasys.heku.util.net.libp2p.gossip

import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage

data class PreparedGossipMessageAndTopic(
    val beaconTopic: BeaconGossipTopic,
    val preparedMessage: PreparedGossipMessage
) {
    // caching ID as it's expensive to calculate
    val messageId by lazy { preparedMessage.messageId }
}