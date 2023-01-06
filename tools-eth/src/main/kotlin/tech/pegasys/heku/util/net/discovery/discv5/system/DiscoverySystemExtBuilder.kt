package tech.pegasys.heku.util.net.discovery.discv5.system

import io.libp2p.core.crypto.PrivKey
import io.libp2p.crypto.keys.generateSecp256k1KeyPair
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.await
import org.apache.tuweni.bytes.Bytes
import org.ethereum.beacon.discovery.DiscoverySystemBuilder
import org.ethereum.beacon.discovery.message.handler.ExternalAddressSelector
import org.ethereum.beacon.discovery.network.NettyDiscoveryServer
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.storage.NodeRecordListener
import org.ethereum.beacon.discovery.util.Functions
import tech.pegasys.heku.util.ext.toDiscV5SecretKey
import tech.pegasys.teku.config.TekuConfiguration
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.spec.networks.Eth2Network
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.time.Duration

private val RND = SecureRandom(byteArrayOf(2))

typealias DiscoveryNettyServerFactory = (listenAddress: InetSocketAddress, trafficReadLimit: Int) -> NettyDiscoveryServer

class DiscoverySystemExtBuilder {
    var logger: (String) -> Unit = { println(it) }
    var network: Eth2Network? = null
    var privKey: PrivKey? = null
    var port: Int = 9000
    var tcpPort: Int? = null
    var noTcpPort = false
    var listenNetworkInterface = "0.0.0.0"
    var bootnodeEnrs: MutableList<String> = mutableListOf()

    var defaultNodeRecord: NodeRecord? = null
    var localNodeRecord: NodeRecord? = null
    var localNodeRecordListener: NodeRecordListener? = null
    var advertiseAddress: InetAddress? = null
    var eth2AttestSubnetCount = 64
    var eth2AttestSubnets: List<Int>? = listOf()
    var eth2SyncSubnetCount = 4
    var eth2SyncSubnets: List<Int>? = listOf()
    var trafficReadLimit: Int = 0

    var serverFactory: DiscoveryNettyServerFactory? = null
    var builderTweaker: (DiscoverySystemBuilder) -> Unit = { }
    var localNodeRecordTweaker: (EnrBuilder) -> Unit = { }
    var random = RND

    fun copy(): DiscoverySystemExtBuilder {
        val ret = DiscoverySystemExtBuilder()
        DiscoverySystemExtBuilder::class.declaredMemberProperties
            .map { it as KMutableProperty1<DiscoverySystemExtBuilder, Any?> }
            .forEach {
                val value = it.get(this)
                it.set(ret, value)
            }
        return ret;
    }

    fun globalSkipSignatureVerify() {
        Functions.setSkipSignatureVerify(true)
    }

    fun withNetworkBootnodes(): DiscoverySystemExtBuilder {
        checkNotNull(network) { "Should set the network first" }
        bootnodeEnrs = TekuConfiguration.builder()
            .eth2NetworkConfig { it.applyNetworkDefaults(network) }
            .build().discovery().bootnodes
        return this
    }

    fun buildLocalNodeRecord(): NodeRecord {
        if (localNodeRecord != null) return localNodeRecord!!

        if (privKey == null) {
            privKey = generateSecp256k1KeyPair(random).first
        }
        if (tcpPort == null && !noTcpPort) {
            tcpPort = port
        }
        if (advertiseAddress == null) {
            advertiseAddress = Inet4Address.getByName(listenNetworkInterface)
        }

        val builder = defaultNodeRecord?.deriveBuilder() ?: EnrBuilder()
        network?.also { builder.network(it) }
        builder
            .address(advertiseAddress!!, port, tcpPort)
            .privateKey(privKey!!)

        eth2AttestSubnets?.also {
            builder.attestSubnets(it, eth2AttestSubnetCount)
        }
        eth2SyncSubnets?.also {
            builder.syncSubnets(it, eth2SyncSubnetCount)
        }

        localNodeRecordTweaker(builder)
        return builder.build()
    }

    fun build(): DiscoverySystemExt {
        val isExplicitAdvertiseAddress = advertiseAddress != null
        val nodeRecord = buildLocalNodeRecord()
        val builder = DiscoverySystemBuilder()
            .secretKey(privKey!!.toDiscV5SecretKey())
            .listen(listenNetworkInterface, port)
            .bootnodes(*bootnodeEnrs.toTypedArray())
            .localNodeRecord(nodeRecord)
            .trafficReadLimit(trafficReadLimit)

        localNodeRecordListener?.also {
            builder.localNodeRecordListener(it)
        }
        if (isExplicitAdvertiseAddress) {
            builder.externalAddressSelector(ExternalAddressSelector.NOOP)
        }
        serverFactory?.also { serverFactory ->
            val nettyDiscoveryServer = serverFactory(InetSocketAddress(listenNetworkInterface, port), trafficReadLimit)
            builder.discoveryServer(nettyDiscoveryServer)
        }
        builderTweaker(builder)
        val system = builder.build()
        return DiscoverySystemExt(system)
    }

    fun buildAndLaunch(): SafeFuture<DiscoverySystemExt> {
        val sys = build()
        return SafeFuture.of(sys.system.start()).thenApply { sys }
    }

    fun launchHive(
        privKeys: Sequence<PrivKey>,
        ports: Sequence<Int>
    ): Flow<DiscoverySystemExt> =
        launchHive(
            privKeys.zip(ports)
                .map { (privKey, port) ->
                    copy().also { builder ->
                        builder.privKey = privKey
                        builder.port = port
                    }
                }.toList()
        )

    companion object {

        fun launchHive(
            discBuilders: List<DiscoverySystemExtBuilder>,
            creationDelay: Duration = Duration.ZERO
        ): Flow<DiscoverySystemExt> {
            val mainDiscBuilder = discBuilders[0]
            val mainDisc = mainDiscBuilder.buildAndLaunch().join()
            val mainEnr = mainDisc.system.localNodeRecord.asEnr()

            val othersFlow =
                discBuilders
                    .drop(1)
                    .asFlow()
                    .map { builder ->
                        delay(creationDelay)
                        builder.bootnodeEnrs = mutableListOf(mainEnr)
                        builder.buildAndLaunch().await()
                    }

            return flowOf(flowOf(mainDisc), othersFlow).flattenConcat()
        }

    }
}
