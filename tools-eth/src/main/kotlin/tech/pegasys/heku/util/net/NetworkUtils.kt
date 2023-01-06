package tech.pegasys.heku.util.net

import io.libp2p.core.PeerId
import io.libp2p.core.multiformats.Multiaddr
import org.ethereum.beacon.discovery.storage.NodeRecordListener
import tech.pegasys.heku.util.net.discovery.discv5.system.DiscoverySystemExtBuilder
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.networking.p2p.peer.NodeId
import tech.pegasys.teku.spec.networks.Eth2Network
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL


class NetworkUtils {
    companion object {

        fun getExternalIPWithAWS(): SafeFuture<InetAddress> {
            val ret = SafeFuture<InetAddress>()
            Thread {
                try {
                    val ipString = URL("http://checkip.amazonaws.com").readText().trim()
                    ret.complete(InetAddress.getByName(ipString))
                } catch (e: Exception) {
                    ret.completeExceptionally(e)
                }
            }.start()
            return ret
        }

        fun getExternalIPWithDiscV5(): SafeFuture<InetAddress> {
            val addrPromise = SafeFuture<InetAddress>()
            return DiscoverySystemExtBuilder().apply {
                network = Eth2Network.MAINNET
                withNetworkBootnodes()
                localNodeRecordListener = NodeRecordListener { _, newRec ->
                    newRec.udpAddress.ifPresent {
                        if (!it.address.isLinkLocalAddress) {
                            addrPromise.complete(it.address)
                        }
                    }
                }
            }
                .buildAndLaunch()
                .thenCombine(addrPromise) { system, addr ->
                    system.stopAsync()
                    addr
                }
        }

        fun fromInetSocketAddress(address: InetSocketAddress): Multiaddr {
            return fromInetSocketAddress(address, "tcp")
        }

        fun fromInetSocketAddress(address: InetSocketAddress, protocol: String): Multiaddr {
            val addrString = String.format(
                "/%s/%s/%s/%d",
                protocol(address.address),
                address.address.hostAddress,
                protocol,
                address.port
            )
            return Multiaddr.fromString(addrString)
        }

        fun fromInetSocketAddress(address: InetSocketAddress, nodeId: NodeId): Multiaddr {
            return addPeerId(fromInetSocketAddress(address, "tcp"), nodeId)
        }

        private fun addPeerId(addr: Multiaddr, nodeId: NodeId): Multiaddr {
            return addr.withP2P(PeerId.fromBase58(nodeId.toBase58()))
        }


        private fun protocol(address: InetAddress): String {
            return if (address is Inet6Address) "ip6" else "ip4"
        }

    }
}