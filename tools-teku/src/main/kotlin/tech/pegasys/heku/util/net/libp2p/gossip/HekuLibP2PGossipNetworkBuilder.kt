package tech.pegasys.heku.util.net.libp2p.gossip

import com.google.common.base.Preconditions
import io.libp2p.core.PeerId
import io.libp2p.core.pubsub.ValidationResult
import io.libp2p.etc.types.toLongBigEndian
import io.libp2p.etc.util.netty.nettyInitializer
import io.libp2p.pubsub.*
import io.libp2p.pubsub.gossip.GossipRouter
import io.libp2p.pubsub.gossip.GossipScore
import io.libp2p.pubsub.gossip.GossipTopicScoreParams
import io.libp2p.pubsub.gossip.builders.GossipRouterBuilder
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import org.apache.tuweni.bytes.Bytes
import pubsub.pb.Rpc
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler
import tech.pegasys.teku.networking.p2p.gossip.config.GossipConfig
import tech.pegasys.teku.networking.p2p.libp2p.config.LibP2PParamsFactory
import tech.pegasys.teku.networking.p2p.libp2p.gossip.*
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*

class HekuLibP2PGossipNetworkBuilder : LibP2PGossipNetworkBuilder() {
    var calculateGossipScore = true
    var gossipSlowerSafeSeenCache = true
    var gossipProcessIHaves = true
    var gossipEmitGossips = true


    val superBuildHook: () -> LibP2PGossipNetwork = { buildImpl() }
    var buildHook: () -> LibP2PGossipNetwork = superBuildHook

    override fun createGossipRouter(
        gossipConfig: GossipConfig,
        gossipTopicFilter: GossipTopicFilter,
        topicHandlers: GossipTopicHandlers
    ): GossipRouter {

        // copy of the super method with handling of noGossipScore
        val gossipParams = LibP2PParamsFactory.createGossipParams(gossipConfig)
        val scoreParams = LibP2PParamsFactory.createGossipScoreParams(gossipConfig.scoringConfig)

        val subscriptionFilter: TopicSubscriptionFilter = MaxCountTopicSubscriptionFilter(
            LibP2PParamsFactory.MAX_SUBSCRIPTIONS_PER_MESSAGE,
            MAX_SUBSCRIBED_TOPICS,
            object:TopicSubscriptionFilter {
                override fun canSubscribe(topic: Topic): Boolean =
                    gossipTopicFilter.isRelevantTopic(topic)

            })

        val builder = GossipRouterBuilder()
        val seenCache: SeenCache<Optional<ValidationResult>> = TTLSeenCache(
            FastIdSeenCache { msg: PubsubMessage ->
                Bytes.wrap(
                    msg.messageSha256()
                )
            },
            gossipParams.seenTTL,
            builder.currentTimeSuppluer
        )

        builder.params = gossipParams
        builder.scoreParams = scoreParams
        builder.protocol = PubsubProtocol.Gossip_V_1_1
        builder.subscriptionTopicSubscriptionFilter = subscriptionFilter
        builder.seenCache = seenCache
        builder.messageFactory = { msg: Rpc.Message ->
            Preconditions.checkArgument(
                msg.topicIDsCount == 1,
                "Unexpected number of topics for a single message: " + msg.topicIDsCount
            )
            val topic = msg.getTopicIDs(0)
            val payload = Bytes.wrap(msg.data.toByteArray())
            val preparedMessage = topicHandlers
                .getHandlerForTopic(topic)
                .map { handler: TopicHandler ->
                    handler.prepareMessage(
                        payload
                    )
                }
                .orElse(defaultMessageFactory.create(topic, payload, networkingSpecConfig))
            PreparedPubsubMessage(msg, preparedMessage)
        }
        builder.messageValidator = GossipWireValidator()
        if (!calculateGossipScore) {
            builder.scoreFactory = { _,_,_,_ ->
                NoopGossipScore()
            }
        }
        val gossipRouter = builder.build()
//        gossipRouter.processIHaves = gossipProcessIHaves
//        gossipRouter.emitGossips = gossipEmitGossips

        return gossipRouter
    }

    fun createFastIdFunction(): (PubsubMessage) -> Any =
        if (!gossipSlowerSafeSeenCache)
            { msg: PubsubMessage ->
                val msgBytes = msg.protobufMessage.toByteArray()
                val d = max(msgBytes.size / 8, 1)
                val target = ByteArray(8) { idx ->
                    val idxCapped = d * min(idx, msgBytes.size - 1)
                    msgBytes[idxCapped]
                }
                target.toLongBigEndian()
            }
        else
            { msg: PubsubMessage ->
                Bytes.wrap(msg.messageSha256())
            }

    override fun build(): LibP2PGossipNetwork = buildHook()

    fun buildImpl(): LibP2PGossipNetwork {
        if (logWireGossip && debugGossipHandler != null) {
            // fix the issues that just one handler could be added
            val logWireGossipHandler = LoggingHandler("wire.gossip", LogLevel.DEBUG)
            val customHandler = debugGossipHandler
            debugGossipHandler = nettyInitializer {
                it.addLastLocal(logWireGossipHandler)
                it.addLastLocal(customHandler)
            }
            logWireGossip = false
        }
        return super.build()
    }

    internal class NoopGossipScore : GossipScore {

        override fun score(peerId: PeerId): Double = 0.0
        override fun getCachedScore(peerId: PeerId): Double = 0.0
        override fun updateTopicParams(topicScoreParams: Map<String, GossipTopicScoreParams>) {}
    }
}

