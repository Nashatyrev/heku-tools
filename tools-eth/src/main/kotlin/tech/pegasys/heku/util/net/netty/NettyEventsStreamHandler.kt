package tech.pegasys.heku.util.net.netty

import io.netty.channel.ChannelDuplexHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelPromise
import org.apache.logging.log4j.LogManager
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.flow.BufferOverflowHekuFlowException
import tech.pegasys.heku.util.flow.SafeSharedFlow

val LOG = LogManager.getLogger(NettyEventsStreamHandler::class.java)

@Suppress("UNCHECKED_CAST")
@Sharable
/**
 * [messageCapturer] should synchronously capture mutable netty message to immutable custom message
 */
class NettyEventsStreamHandler<TNettyMessage, TImmutableMessage>(
    val messageCapturer: (TNettyMessage) -> TImmutableMessage,
    val timer: () -> MTime = { MTime.now() }
) : ChannelDuplexHandler() {
    private val eventsSink = SafeSharedFlow<NettyEvent<TImmutableMessage>>(replay = 1024, name = "NettyEventsStreamHandler.eventsSink")
    val eventsFlow = eventsSink.sharedFlow()

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        msg as TNettyMessage
        emit(ctx, NettyReadEvent(timer(), ctx.channel().id(), msg))
        super.channelRead(ctx, msg)
    }

    override fun write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise) {
        msg as TNettyMessage
        emit(ctx, NettyWriteEvent(timer(), ctx.channel().id(), msg))
        super.write(ctx, msg, promise)
    }

    private fun emit(ctx: ChannelHandlerContext, evt: NettyMessageEvent<TNettyMessage>) {
        try {
            val capturedMessage = messageCapturer(evt.message)
            eventsSink.emitOrThrow(evt.withMessage(capturedMessage))
        } catch (e: BufferOverflowHekuFlowException) {
            e.printStackTrace()
            LOG.error("Netty event sink overflow: $ctx, with event $evt")
        }
    }
}