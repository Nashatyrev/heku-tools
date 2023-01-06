package tech.pegasys.heku.util.net.libp2p

import io.libp2p.etc.types.seconds
import io.netty.channel.ChannelHandlerContext
import tech.pegasys.teku.networking.p2p.libp2p.Firewall
import java.net.InetSocketAddress

/**
 * Drops any inbound connections
 */
class NoInboundFirewall(val listenTcpPort: Int) : Firewall(1.seconds) {
    override fun channelActive(ctx: ChannelHandlerContext) {
        if ((ctx.channel().localAddress() as InetSocketAddress).port == listenTcpPort) {
            ctx.close()
        } else {
            super.channelActive(ctx)
        }
    }
}