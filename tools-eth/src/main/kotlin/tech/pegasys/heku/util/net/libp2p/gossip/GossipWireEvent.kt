package tech.pegasys.heku.util.net.libp2p.gossip

import io.libp2p.core.PeerId
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.MTimestamped
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource.LOCAL
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource.REMOTE
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType.MESSAGE

enum class WireEventType { CONNECTED, DISCONNECTED, MESSAGE }
enum class EventSource { REMOTE, LOCAL, UNKNOWN }

data class GossipWireEvent(
    override val time: MTime,
    val peerId: PeerId,
    val type: WireEventType,
    val source: EventSource,
    val message: Rpc.RPC? = null,
) : MTimestamped {

    init {
        if (type == MESSAGE) {
            requireNotNull(message)
            require(source == REMOTE || source == LOCAL)
        } else {
            require(message == null)
        }
    }

    fun toGossipWireMessage(): GossipWireMessage {
        check(type == MESSAGE)
        return GossipWireMessage(
            time,
            peerId,
            if (source == LOCAL) GossipWireMessage.MessageSource.LOCAL else GossipWireMessage.MessageSource.REMOTE,
            message!!
        )
    }
}