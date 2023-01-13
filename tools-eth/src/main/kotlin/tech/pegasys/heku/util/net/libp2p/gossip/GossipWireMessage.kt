package tech.pegasys.heku.util.net.libp2p.gossip

import io.libp2p.core.PeerId
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.MTimestamped

data class GossipWireMessage(
    override val time: MTime,
    val peerId: PeerId,
    val messageSource: MessageSource,
    val message: Rpc.RPC
) : MTimestamped {
    enum class MessageSource { REMOTE, LOCAL }

}