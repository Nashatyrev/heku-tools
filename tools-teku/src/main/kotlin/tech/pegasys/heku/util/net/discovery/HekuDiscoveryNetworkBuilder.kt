package tech.pegasys.heku.util.beacon

import org.hyperledger.besu.plugin.services.MetricsSystem
import tech.pegasys.teku.infrastructure.async.AsyncRunner
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.networking.p2p.connection.ConnectionManager
import tech.pegasys.teku.networking.p2p.connection.PeerSelectionStrategy
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryService
import tech.pegasys.teku.networking.p2p.network.P2PNetwork
import tech.pegasys.teku.networking.p2p.network.PeerAddress
import tech.pegasys.teku.networking.p2p.peer.Peer
import java.util.function.Predicate

class HekuDiscoveryNetworkBuilder : DiscoveryNetworkBuilder() {

    var defaultConnectionManager: Boolean = true

    override fun createConnectionManager(): ConnectionManager {
        return if (defaultConnectionManager) {
            super.createConnectionManager()
        } else {
            NoopConnectionManager(
                metricsSystem,
                discoveryService,
                asyncRunner,
                p2pNetwork,
                peerSelectionStrategy,
                mutableListOf()
            )
        }
    }
}

class NoopConnectionManager(
    metricsSystem: MetricsSystem?,
    discoveryService: DiscoveryService?,
    asyncRunner: AsyncRunner?,
    network: P2PNetwork<out Peer>?,
    peerSelectionStrategy: PeerSelectionStrategy?,
    peerAddresses: MutableList<PeerAddress>?
) : ConnectionManager(
    metricsSystem,
    discoveryService,
    asyncRunner,
    network,
    peerSelectionStrategy,
    peerAddresses
) {
    override fun doStart(): SafeFuture<*> {
        return SafeFuture.completedFuture(null)
    }

    override fun doStop(): SafeFuture<*> {
        return SafeFuture.completedFuture(null)
    }

    override fun addStaticPeer(peerAddress: PeerAddress) {
    }

    override fun addPeerPredicate(predicate: Predicate<DiscoveryPeer>) {
    }
}