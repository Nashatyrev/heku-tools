package tech.pegasys.heku.util.libp2p.gossip

import io.libp2p.core.PeerId
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import pubsub.pb.Rpc
import tech.pegasys.heku.fixtures.Waiter
import tech.pegasys.heku.fixtures.net.GossipUtil.Companion.rpcPublishMessage
import tech.pegasys.heku.fixtures.net.GossipWireHandlerUtil
import tech.pegasys.heku.fixtures.net.PeerUtil.Companion.peerId
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource.*
import tech.pegasys.heku.util.net.libp2p.gossip.GossipWireEvent
import tech.pegasys.heku.util.net.libp2p.gossip.GossipWireMessage
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType.*

@Disabled
class GossipWireHandlerTest {

    data class TestResult(
        val peerId: PeerId,
        val inMessages: MutableList<Rpc.RPC> = mutableListOf(),
        val outMessages: MutableList<Rpc.RPC> = mutableListOf(),
        var completed: Boolean = false
    )

    val handler = GossipWireHandlerUtil.TestHandler()
        .also { handler ->
            handler.connectionsFlow
                .onEach { gossipPeer ->
                    println("New subflow for ${gossipPeer.peerId.toHex()}")
                    val testResult = TestResult(gossipPeer.peerId)
                    results += testResult
                    gossipPeer.messages
                        .onEach {
                            println("${it.peerId.toHex()} ${it.messageSource} ${it.message}")
                            if (it.messageSource == GossipWireMessage.MessageSource.REMOTE) {
                                testResult.inMessages += it.message
                            } else {
                                testResult.outMessages += it.message
                            }
                        }
                        .onCompletion {
                            println("Completed inbound ${gossipPeer.peerId.toHex()}")
                            testResult.completed = true
                        }
                        .catch { it.printStackTrace() }
                        .launchIn(GlobalScope)
                }
                .catch { it.printStackTrace() }
                .launchIn(GlobalScope)
        }

    val results = mutableListOf<TestResult>()

    @Test
    fun sanityTest() {
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(1), CONNECTED, UNKNOWN))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(1), MESSAGE, REMOTE, rpcPublishMessage(1)))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(1), MESSAGE, LOCAL, rpcPublishMessage(2)))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(1), DISCONNECTED, UNKNOWN))

        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(2), CONNECTED, UNKNOWN))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(2), MESSAGE, REMOTE, rpcPublishMessage(3)))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(3), CONNECTED, UNKNOWN))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(2), MESSAGE, LOCAL, rpcPublishMessage(4)))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(3), DISCONNECTED, UNKNOWN))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(2), MESSAGE, LOCAL, rpcPublishMessage(5)))
        handler.emitter.emitOrThrow(GossipWireEvent(MTime(1), peerId(2), DISCONNECTED, UNKNOWN))

        Waiter().wait {
            results.size == 3 &&
                    results.all { it.completed }
        }

        val resultMap = results.associateBy { it.peerId }

        Assertions.assertThat(resultMap[peerId(1)]!!.inMessages).hasSize(1)
        Assertions.assertThat(resultMap[peerId(1)]!!.outMessages).hasSize(1)
        Assertions.assertThat(resultMap[peerId(2)]!!.inMessages).hasSize(1)
        Assertions.assertThat(resultMap[peerId(2)]!!.outMessages).hasSize(2)
        Assertions.assertThat(resultMap[peerId(3)]!!.inMessages).hasSize(0)
        Assertions.assertThat(resultMap[peerId(3)]!!.outMessages).hasSize(0)
    }
}