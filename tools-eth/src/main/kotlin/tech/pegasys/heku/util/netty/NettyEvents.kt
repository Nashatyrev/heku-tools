package tech.pegasys.heku.util.netty

import io.netty.channel.ChannelId
import tech.pegasys.heku.util.MTime

sealed class NettyEvent<TMessage>(
    val time: MTime,
    val channelId: ChannelId
)

sealed class NettyMessageEvent<TMessage>(time: MTime, channelId: ChannelId, val message: TMessage) :
    NettyEvent<TMessage>(time, channelId) {

    abstract fun <R> withMessage(newMsg: R): NettyMessageEvent<R>
}

class NettyReadEvent<TMessage>(time: MTime, channelId: ChannelId, message: TMessage) :
    NettyMessageEvent<TMessage>(time, channelId, message) {

    override fun <R> withMessage(newMsg: R): NettyReadEvent<R> = NettyReadEvent(time, channelId, newMsg)
}

class NettyWriteEvent<TMessage>(time: MTime, channelId: ChannelId, message: TMessage) :
    NettyMessageEvent<TMessage>(time, channelId, message) {

    override fun <R> withMessage(newMsg: R): NettyWriteEvent<R> = NettyWriteEvent(time, channelId, newMsg)
}
