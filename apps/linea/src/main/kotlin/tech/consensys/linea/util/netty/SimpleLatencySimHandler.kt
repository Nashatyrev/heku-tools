package tech.consensys.linea.util.netty

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandler
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.util.concurrent.EventExecutorGroup
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

@Sharable
class SimpleLatencySimHandler(
    val messageDelay: Duration
) : ChannelInboundHandlerAdapter() {

    private fun EventExecutorGroup.delay(run: () -> Unit) =
        this.schedule(run, messageDelay.inWholeMilliseconds, TimeUnit.MILLISECONDS)

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        ctx.executor().delay { ctx.fireChannelRead(msg) }
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable?) {
        ctx.executor().delay { ctx.fireExceptionCaught(cause) }
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        ctx.executor().delay { ctx.fireChannelActive() }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        ctx.executor().delay { ctx.fireChannelInactive() }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        ctx.executor().delay { ctx.fireChannelReadComplete() }
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        ctx.executor().delay { ctx.fireUserEventTriggered(evt) }
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        ctx.executor().delay { ctx.fireChannelWritabilityChanged() }
    }
}