package tech.pegasys.heku.util.ext

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.core.multiformats.MultiaddrComponent
import io.libp2p.core.multiformats.Protocol
import org.apache.tuweni.bytes.Bytes

fun Multiaddr.removeFirstComponent(protocol: Protocol) =
    Multiaddr(
        this.components.takeWhile { it.protocol != protocol } +
                this.components.dropWhile { it.protocol != protocol }.drop(1)
    )


fun Multiaddr.replaceFirstComponent(protocol: Protocol, newValue: Bytes) =
    Multiaddr(
        this.components.takeWhile { it.protocol != protocol } +
                MultiaddrComponent(protocol, newValue.toArrayUnsafe()) +
                this.components.dropWhile { it.protocol != protocol }.drop(1)
    )

fun Multiaddr.replacePeerId(peerId: PeerId) =
    this.replaceFirstComponent(Protocol.P2P, peerId.bytes.toBytes())

