package tech.pegasys.heku.util.net.libp2p.gossip

import com.google.common.annotations.VisibleForTesting
import io.libp2p.core.PeerId
import io.libp2p.etc.STREAM
import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import org.apache.logging.log4j.LogManager
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTimeSupplier
import tech.pegasys.heku.util.ext.associateBy
import tech.pegasys.heku.util.flow.CompletableSharedFlow
import tech.pegasys.heku.util.flow.SafeSharedFlow
import tech.pegasys.heku.util.flow.bufferWithError
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource.*
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType.*
import java.util.concurrent.atomic.AtomicLong

private val LOG = LogManager.getLogger()

@Sharable
open class GossipWireHandler(
    private val timeSupplier: MTimeSupplier = MTimeSupplier.SYSTEM_TIME,
) : ChannelDuplexHandler() {

    @VisibleForTesting
    protected val eventsPriv = SafeSharedFlow<GossipWireEvent>(replay = 1024, name = "GossipWireHandler.eventsPriv")
    val eventsFlow = eventsPriv.sharedFlow()

    val gossipWireMessageFlow = eventsFlow
        .bufferWithError(64 * 1024, name = "GossipWireHandler.gossipWireMessageFlow")
        .filter { it.type == MESSAGE }
        .map { it.toGossipWireMessage() }

    val connectionsFlow = eventsFlow
        .bufferWithError(64 * 1024, name = "GossipWireHandler.connectionsFlow")
        .associateBy(
            { it.peerId },
            { it.type == DISCONNECTED },
            { CompletableSharedFlow(replay = 16, onBufferOverflow = DROP_OLDEST) })
        .map { peerFlow ->
            val messagesFlow = peerFlow.flow
                .filter { it.type == MESSAGE }
            GossipPeer(
                peerFlow.key,
                messagesFlow.map { it.toGossipWireMessage() }
            )
        }

    override fun channelActive(ctx: ChannelHandlerContext) {
        emitEvent(ctx, CONNECTED, UNKNOWN)
        super.channelActive(ctx)
    }

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        emitEvent(ctx, MESSAGE, REMOTE, msg as Rpc.RPC)
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        emitEvent(ctx, MESSAGE, LOCAL, msg as Rpc.RPC)
        super.write(ctx, msg, promise)
    }

    override fun channelUnregistered(ctx: ChannelHandlerContext) {
        emitEvent(ctx, DISCONNECTED, UNKNOWN)
        super.channelUnregistered(ctx)
    }

    private val emitFailureCount = AtomicLong(0)
    private fun emitEvent(
        ctx: ChannelHandlerContext,
        type: WireEventType,
        source: EventSource,
        message: Rpc.RPC? = null,
    ) {
        try {
            val peerId = ctx.channel().attr(STREAM).get().remotePeerId()
            val emitRes =
                eventsPriv.emitOrThrow(
                    GossipWireEvent(
                        timeSupplier.getTime(),
                        peerId,
                        type,
                        source,
                        message
                    )
                )
        } catch (e: Exception) {
            LOG.warn("Unexpected exception", e)
        }
    }
}

data class GossipPeer(
    val peerId: PeerId,
    val messages: Flow<GossipWireMessage>,
)

