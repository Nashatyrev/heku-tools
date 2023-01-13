package tech.pegasys.heku.fixtures.net

import io.libp2p.etc.types.toProtobuf
import pubsub.pb.Rpc

class GossipUtil {

    companion object {

        fun rpcPublishMessage(n: Int) = Rpc.RPC.newBuilder().addPublish(
            Rpc.Message.newBuilder()
                .addTopicIDs("topic-$n")
                .setData("data-$n".toByteArray().toProtobuf())
        ).build()

        fun rpcSubscribeMessage(n: Int) = Rpc.RPC.newBuilder().addSubscriptions(
            Rpc.RPC.SubOpts.newBuilder()
                .setTopicid("topic-$n")
                .setSubscribe(true)
        ).build()

        fun rpcUnsubscribeMessage(n: Int) = Rpc.RPC.newBuilder().addSubscriptions(
            Rpc.RPC.SubOpts.newBuilder()
                .setTopicid("topic-$n")
                .setSubscribe(false)
        ).build()

        fun rpcMerge(vararg msgs: Rpc.RPC): Rpc.RPC {
            val builder = Rpc.RPC.newBuilder()
            msgs.forEach { builder.mergeFrom(it) }
            return builder.build()
        }
    }
}