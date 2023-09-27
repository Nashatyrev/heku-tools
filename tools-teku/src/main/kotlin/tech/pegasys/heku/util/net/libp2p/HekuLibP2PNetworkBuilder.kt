package tech.pegasys.heku.util.net.libp2p

import io.libp2p.core.Host
import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.dsl.BuilderJ
import io.libp2p.core.dsl.SecureChannelCtor
import io.libp2p.core.multistream.ProtocolBinding
import io.libp2p.core.mux.StreamMuxer
import io.libp2p.core.mux.StreamMuxerProtocol
import io.libp2p.core.security.SecureChannel
import io.libp2p.security.noise.NoiseXXSecureChannel
import io.libp2p.transport.tcp.TcpTransport
import io.netty.handler.logging.LogLevel
import tech.pegasys.heku.util.net.libp2p.gossip.HekuLibP2PGossipNetworkBuilder
import tech.pegasys.heku.util.net.NetworkUtils
import tech.pegasys.teku.networking.p2p.libp2p.*
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetwork
import tech.pegasys.teku.networking.p2p.libp2p.gossip.LibP2PGossipNetworkBuilder
import tech.pegasys.teku.networking.p2p.libp2p.rpc.RpcHandler
import tech.pegasys.teku.networking.p2p.network.P2PNetwork
import tech.pegasys.teku.networking.p2p.peer.NodeId
import tech.pegasys.teku.networking.p2p.peer.Peer
import java.net.InetSocketAddress

class HekuLibP2PNetworkBuilder : LibP2PNetworkBuilder() {

    val asyncRunner get() = asyncRunner
    val config get() = config
    val privateKeyProvider get() = privateKeyProvider
    val reputationManager get() = reputationManager
    val metricsSystem get() = metricsSystem
    var rpcMethods get() = rpcMethods
        set(value) { rpcMethods = value }
    var peerHandlers get() = peerHandlers
        set(value) { peerHandlers = value }
    val preparedGossipMessageFactory get() = preparedGossipMessageFactory
    val gossipTopicFilter get() = gossipTopicFilter
    val firewall get() = firewall
    val muxFirewall get() = muxFirewall
    var gossipNetwork get() = gossipNetwork
        set(value) { gossipNetwork = value }
    val hostBuilderDefaults get() = hostBuilderDefaults
    var host get() = host
        set(value) { host = value }
    var rpcHandlers get() = rpcHandlers
        set(value) { rpcHandlers = value }
    var peerManager get() = peerManager
        set(value) { peerManager = value }

    var createRpcHandlersHook: () -> MutableList<out RpcHandler<*, *, *>> = { super.createRpcHandlers() }
    var createGossipNetworkHook: () -> LibP2PGossipNetwork = { super.createGossipNetwork() }
    var createPeerManagerHook: () -> PeerManager = { super.createPeerManager() }

    var createHostBuilderHook: () -> BuilderJ = { BuilderJ() }
    var hostBuilderPostModifier: (BuilderJ) -> Unit = { }
    var createHostHook: () -> Host = { createHostImpl() }

    val libP2PGossipNetworkBuilder = HekuLibP2PGossipNetworkBuilder()

    var createLibP2PNetworkHook: () -> LibP2PNetwork = { createLibP2PNetwork() }
    var buildHook: () -> P2PNetwork<Peer> = { buildImpl() }

    override fun build(): P2PNetwork<Peer> = buildHook()

    override fun createRpcHandlers(): MutableList<out RpcHandler<*, *, *>> = createRpcHandlersHook()

    override fun createGossipNetwork(): LibP2PGossipNetwork = createGossipNetworkHook()

    override fun createPeerManager(): PeerManager = createPeerManagerHook()

    override fun createHost(): Host = createHostHook()

    override fun createLibP2PGossipNetworkBuilder(): LibP2PGossipNetworkBuilder = libP2PGossipNetworkBuilder

    fun createHostImpl(): Host {
        val privKey = privateKeyProvider.get()
        val nodeId: NodeId = LibP2PNodeId(PeerId.fromPubKey(privKey.publicKey()))

        val advertisedAddr = MultiaddrUtil.fromInetSocketAddress(
            InetSocketAddress(config.advertisedIp, config.advertisedPort), nodeId
        )
        val listenAddr = NetworkUtils.fromInetSocketAddress(
            InetSocketAddress(config.networkInterface, config.listenPort)
        )

        val hostBuilder = createHostBuilderHook()

        with(hostBuilder) {
            identity.factory = { privKey }
            transports.add { TcpTransport(it) }
            secureChannels.add { privKey, _ -> NoiseXXSecureChannel(privKey) }
            muxers.add(StreamMuxerProtocol.Mplex)
            network.listen(listenAddr.toString())
            protocols.addAll(getDefaultProtocols(privKey.publicKey(), advertisedAddr) as List<ProtocolBinding<Any>>)
            protocols.add(gossipNetwork.gossip)
            protocols.addAll(rpcHandlers)
            if (config.wireLogsConfig.isLogWireCipher) {
                debug.beforeSecureHandler.addLogger(LogLevel.DEBUG, "wire.ciphered")
            }
            debug.beforeSecureHandler.addNettyHandler(firewall)
            if (config.wireLogsConfig.isLogWirePlain) {
                debug.afterSecureHandler.addLogger(LogLevel.DEBUG, "wire.plain")
            }
            if (config.wireLogsConfig.isLogWireMuxFrames) {
                debug.muxFramesHandler.addLogger(LogLevel.DEBUG, "wire.mux")
            }
            connectionHandlers.add(peerManager)
            debug.muxFramesHandler.addHandler(muxFirewall)
        }

        hostBuilderPostModifier(hostBuilder)

        return hostBuilder.build(hostBuilderDefaults)
    }

    fun buildImpl(): P2PNetwork<Peer> {
        gossipNetwork = createGossipNetworkHook()
        rpcHandlers = createRpcHandlersHook()
        peerManager = createPeerManagerHook()

        host = createHostHook()

        return createLibP2PNetworkHook()
    }

    fun createLibP2PNetwork(): LibP2PNetwork {
        val nodeId: NodeId = LibP2PNodeId(host.peerId)
        val advertisedAddr = MultiaddrUtil.fromInetSocketAddress(
            InetSocketAddress(config.advertisedIp, config.advertisedPort), nodeId
        )

        return object : LibP2PNetwork(
            host.privKey, nodeId, host, peerManager, advertisedAddr, gossipNetwork,
            config.listenPort
        ) {}
    }
}