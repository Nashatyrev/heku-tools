package tech.consensys.linea.util.libp2p

import io.libp2p.core.ChannelVisitor
import io.libp2p.core.Stream
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import pubsub.pb.Rpc

@Sharable
class GossipTracker : ChannelDuplexHandler() {

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        onInbound(msg as Rpc.RPC)
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext?, msg: Any?, promise: ChannelPromise?) {
        onOutbound(msg as Rpc.RPC)
        super.write(ctx, msg, promise)
    }

    private fun onOutbound(msg: Rpc.RPC) {
        msg.publishList.forEach { onOutboundPubsub(it) }
    }

    @Synchronized
    fun onInbound(msg: Rpc.RPC) {
        msg.publishList.forEach { onInboundPubsub(it) }
    }

    private fun onOutboundPubsub(msg: Rpc.Message) {
        if (msg.topicIDsList.first().contains("block")) {
            println("##### OutBlock")
        }
    }

    private fun onInboundPubsub(msg: Rpc.Message) {
        if (msg.topicIDsList.first().contains("block")) {
            println("##### InBlock")
        }
    }
}