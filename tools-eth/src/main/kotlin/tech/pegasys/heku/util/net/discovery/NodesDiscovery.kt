package tech.pegasys.heku.util.net.discovery

import kotlinx.coroutines.flow.Flow
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.schema.NodeRecord
import tech.pegasys.heku.util.net.discovery.discv5.system.NodeSearcher
import tech.pegasys.heku.util.net.discovery.discv5.system.getBootnodes
import tech.pegasys.heku.util.ext.parallelFilter
import tech.pegasys.teku.spec.networks.Eth2Network

fun interface NodesDiscovery {

    /**
     * Emits new discovered node records
     * The flow could be either:
     * - _finite_: the implementation searches for all available nodes at the moment
     * - _infinite_: same a 'finite' but then keeps searching for a new nodes appearing in the network
     */
    fun discover(): Flow<NodeRecord>

    companion object {

        fun createBasic(): NodesDiscovery = createFromDiscoverySystem(NodeSearcher.launchDiscoverySystem())

        fun createFromDiscoverySystem(
            discoverySystem: DiscoverySystem,
            bootnodes: Collection<NodeRecord> = getBootnodes(Eth2Network.MAINNET) + getBootnodes(Eth2Network.PRATER)
        ): NodesDiscovery {
            val nodeSearcher = NodeSearcher(discoverySystem, bootnodes)
            return NodesDiscovery { nodeSearcher.searchAll() }
        }
    }
}

fun NodesDiscovery.withLiveCheck(pingFunction: PingFunction) = LiveNodesDiscovery(this, pingFunction)
fun Flow<NodeRecord>.withLiveCheck(pingFunction: PingFunction) = this
    .parallelFilter {
        try {
            pingFunction.ping(it)
            true
        } catch (e: PingTimeoutException) {
            false
        }
    }
class LiveNodesDiscovery(
    private val delegate: NodesDiscovery,
    private val pingFunction: PingFunction
) : NodesDiscovery {

    override fun discover(): Flow<NodeRecord> = delegate.discover().withLiveCheck(pingFunction)
}