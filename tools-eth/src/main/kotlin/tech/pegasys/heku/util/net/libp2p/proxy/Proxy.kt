package tech.pegasys.heku.util.net.libp2p.proxy

import io.libp2p.core.ChannelVisitor
import io.libp2p.core.P2PChannel
import io.netty.channel.ChannelHandler

val NO_PROXY = object : Proxy() {
    override fun createProxyNettyHandler(): ChannelHandler = throw UnsupportedOperationException()
    override fun visit(channel: P2PChannel) {}

    override fun toString() = "NO_PROXY"
}

abstract class Proxy : ChannelVisitor<P2PChannel> {

    abstract fun createProxyNettyHandler(): ChannelHandler

    override fun visit(channel: P2PChannel) {
        channel.pushHandler(createProxyNettyHandler())
    }
}