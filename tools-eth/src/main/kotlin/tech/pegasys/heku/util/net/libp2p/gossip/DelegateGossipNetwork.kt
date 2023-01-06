package tech.pegasys.heku.util.net.libp2p.gossip

import io.libp2p.core.pubsub.ValidationResult
import org.apache.tuweni.bytes.Bytes
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel
import tech.pegasys.teku.networking.p2p.gossip.TopicHandler
import tech.pegasys.teku.networking.p2p.gossip.config.GossipTopicsScoringConfig
import tech.pegasys.teku.networking.p2p.peer.NodeId
import java.util.concurrent.CopyOnWriteArrayList

typealias TweakGossipHandler = (PreparedGossipMessageAndTopic, TopicHandler) -> SafeFuture<ValidationResult>

val GOSSIP_HANDLER_NOOP: TweakGossipHandler = { msg, delegateHandler ->
    delegateHandler.handleMessage(msg.preparedMessage)
}
val GOSSIP_HANDLER_ALWAYS_VALID_FORWARD_TO_DELEGATE: TweakGossipHandler = { msg, delegateHandler ->
    delegateHandler.handleMessage(msg.preparedMessage)
    SafeFuture.completedFuture(ValidationResult.Valid)
}
val GOSSIP_HANDLER_ALWAYS_VALID_NO_FORWARD_TO_DELEGATE: TweakGossipHandler =
    { msg, delegateHandler ->
        SafeFuture.completedFuture(ValidationResult.Valid)
    }

typealias TweakGossipListener = (PreparedGossipMessageAndTopic) -> Unit

class BroadcastingTweakGossipHandler(
    val handler: TweakGossipHandler,
    val listeners: List<TweakGossipListener> = CopyOnWriteArrayList()
) {
    val wrappingHandler: TweakGossipHandler = { msg, delegateHandler ->
        listeners.forEach { it(msg) }
        handler(msg, delegateHandler)
    }
}

open class DelegateGossipNetwork(
    private val delegateNetwork: GossipNetwork,
    private val handler: TweakGossipHandler = GOSSIP_HANDLER_NOOP
) : GossipNetwork {

    override fun subscribe(topic: String, topicHandler: TopicHandler): TopicChannel {
        val wrappedHandler = object : DelegateGossipTopicHandler(topicHandler) {
            override fun handleMessage(message: PreparedGossipMessage): SafeFuture<ValidationResult> {
                return handler(
                    PreparedGossipMessageAndTopic(BeaconGossipTopic(topic), message),
                    delegateHandler
                )
            }
        }
        return delegateNetwork.subscribe(topic, wrappedHandler)
    }

    // just forward to delegate
    override fun gossip(topic: String, data: Bytes): SafeFuture<*> =
        delegateNetwork.gossip(topic, data)

    override fun getSubscribersByTopic(): MutableMap<String, MutableCollection<NodeId>> =
        delegateNetwork.subscribersByTopic

    override fun updateGossipTopicScoring(config: GossipTopicsScoringConfig) =
        delegateNetwork.updateGossipTopicScoring(config)
}

open class DelegateGossipTopicHandler(
    protected val delegateHandler: TopicHandler
) : TopicHandler {

    override fun getMaxMessageSize() = delegateHandler.maxMessageSize
    override fun prepareMessage(payload: Bytes) = delegateHandler.prepareMessage(payload)
    override fun handleMessage(message: PreparedGossipMessage) =
        delegateHandler.handleMessage(message)
}
