package tech.pegasys.heku.util.net.discovery.discv5

import org.apache.tuweni.bytes.Bytes
import org.ethereum.beacon.discovery.DiscoverySystem
import org.ethereum.beacon.discovery.DiscoverySystemBuilder
import org.ethereum.beacon.discovery.message.PongData
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.hyperledger.besu.plugin.services.MetricsSystem
import tech.pegasys.teku.infrastructure.async.AsyncRunner
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryConfig
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryPeer
import tech.pegasys.teku.networking.p2p.discovery.discv5.DiscV5Service
import tech.pegasys.teku.networking.p2p.discovery.discv5.NodeRecordConverter
import tech.pegasys.teku.networking.p2p.network.config.NetworkConfig
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId
import tech.pegasys.teku.spec.schemas.SchemaDefinitions
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsSupplier
import tech.pegasys.teku.storage.store.KeyValueStore
import java.net.InetSocketAddress
import java.util.*

class CustomDiscV5Service(
    metricsSystem: MetricsSystem,
    asyncRunner: AsyncRunner,
    discoConfig: DiscoveryConfig,
    p2pConfig: NetworkConfig,
    kvStore: KeyValueStore<String, Bytes>,
    privateKey: Bytes,
    currentSchemaDefinitionsSupplier: SchemaDefinitionsSupplier,
    private val discoverySystemBuilder: CustomDiscoverSystemBuilder = CustomDiscoverSystemBuilder(),

    ) : DiscV5Service(
    metricsSystem,
    asyncRunner,
    discoConfig,
    p2pConfig,
    kvStore,
    privateKey,
    currentSchemaDefinitionsSupplier,
    discoverySystemBuilder,
    CustomNodeRecordConverter()
) {

    val discoverySystem by lazy { discoverySystemBuilder.discoverySystem }

    fun ping(peer: DiscoveryPeer): SafeFuture<PongData> {
        require(peer is DiscV5Peer) { "Expected DiscV5Peer instance" }
        return SafeFuture.of(discoverySystem.ping(peer.nodeRecord))
    }
}

class CustomDiscoverSystemBuilder : DiscoverySystemBuilder() {
    lateinit var discoverySystem: DiscoverySystem
    override fun build(): DiscoverySystem {
        discoverySystem = super.build()
        return discoverySystem
    }
}

class CustomNodeRecordConverter : NodeRecordConverter() {
    override fun convertToDiscoveryPeer(
        nodeRecord: NodeRecord,
        schemaDefinitions: SchemaDefinitions
    ): Optional<DiscoveryPeer> {
        return super.convertToDiscoveryPeer(nodeRecord, schemaDefinitions).map { r ->
            DiscV5Peer(
                nodeRecord,
                r.publicKey,
                r.nodeAddress,
                r.enrForkId,
                r.persistentAttestationSubnets,
                r.syncCommitteeSubnets
            )
        }
    }
}

internal class DiscV5Peer(
    val nodeRecord: NodeRecord,
    publicKey: Bytes?,
    nodeAddress: InetSocketAddress?,
    enrForkId: Optional<EnrForkId?>?,
    persistentAttestationSubnets: SszBitvector?,
    syncCommitteeSubnets: SszBitvector?
) : DiscoveryPeer(
    publicKey,
    nodeAddress,
    enrForkId,
    persistentAttestationSubnets,
    syncCommitteeSubnets
) {
    override fun toString(): String {
        return "DiscV5Peer[${nodeRecord.toString()}]"
    }
}
