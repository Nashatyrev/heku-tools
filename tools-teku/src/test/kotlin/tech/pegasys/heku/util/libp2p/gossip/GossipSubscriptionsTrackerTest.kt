package tech.pegasys.heku.util.libp2p.gossip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pubsub.pb.Rpc
import tech.pegasys.heku.fixtures.Waiter
import tech.pegasys.heku.fixtures.net.GossipUtil.Companion.rpcMerge
import tech.pegasys.heku.fixtures.net.GossipUtil.Companion.rpcPublishMessage
import tech.pegasys.heku.fixtures.net.GossipUtil.Companion.rpcSubscribeMessage
import tech.pegasys.heku.fixtures.net.GossipUtil.Companion.rpcUnsubscribeMessage
import tech.pegasys.heku.fixtures.net.GossipWireHandlerUtil
import tech.pegasys.heku.fixtures.net.PeerUtil.Companion.peerId
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource.*
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType.*
import tech.pegasys.heku.util.net.libp2p.gossip.GossipSubscriptionsTracker
import tech.pegasys.heku.util.net.libp2p.gossip.GossipWireEvent
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType

class GossipSubscriptionsTrackerTest {

    val handler = GossipWireHandlerUtil.TestHandler()
    val subscriptionsTracker = GossipSubscriptionsTracker(handler)
    val subscriptionsMap = subscriptionsTracker.peerSubscriptionsMap


    private fun emitEvent(time: Long, peerId: Int, type: WireEventType, source: EventSource) =
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(time), peerId(peerId), type, source))
    private fun emitMessage(time: Long, peerId: Int, source: EventSource, msg: Rpc.RPC) =
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(time), peerId(peerId), MESSAGE, source, msg))

    @Test
    fun `test simple case`() {
        val subscriptionsStateFlow = subscriptionsMap.getOrDefault(peerId(1))

        emitEvent(100, 1, CONNECTED, UNKNOWN)
        emitMessage(
            105, 1, REMOTE,
            rpcMerge(
                rpcSubscribeMessage(1),
                rpcSubscribeMessage(2)
            )
        )

        Waiter().wait {
            subscriptionsStateFlow.value.online
        }

        assertThat(subscriptionsStateFlow.value.curSubscriptions)
            .containsExactlyInAnyOrderElementsOf(listOf("topic-1", "topic-2"))
        assertThat(subscriptionsStateFlow.value.time).isEqualTo(MTime(105))

        emitMessage(199, 1, REMOTE, rpcPublishMessage(1))
        emitEvent(200, 1, DISCONNECTED, UNKNOWN)

        Waiter().wait {
            !subscriptionsStateFlow.value.online
        }

        assertThat(subscriptionsStateFlow.value.curSubscriptions)
            .containsExactlyInAnyOrderElementsOf(listOf("topic-1", "topic-2"))
        assertThat(subscriptionsStateFlow.value.time).isEqualTo(MTime(199))

        emitEvent(300, 1, CONNECTED, UNKNOWN)
        emitMessage(305, 1, REMOTE, rpcSubscribeMessage(3))

        Waiter().wait {
            subscriptionsStateFlow.value.online
        }

        assertThat(subscriptionsStateFlow.value.curSubscriptions)
            .containsExactlyInAnyOrderElementsOf(listOf("topic-3"))
        assertThat(subscriptionsStateFlow.value.time).isEqualTo(MTime(305))

        emitMessage(405, 1, REMOTE, rpcUnsubscribeMessage(3))

        Waiter().wait {
            subscriptionsStateFlow.value.curSubscriptions.isEmpty()
        }

        assertThat(subscriptionsStateFlow.value.online).isTrue()
        assertThat(subscriptionsStateFlow.value.time).isEqualTo(MTime(405))
    }
}