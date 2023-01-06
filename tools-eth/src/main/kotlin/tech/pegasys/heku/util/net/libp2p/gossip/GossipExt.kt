package tech.pegasys.heku.util.net.libp2p.gossip

import org.apache.tuweni.bytes.Bytes
import pubsub.pb.Rpc
import tech.pegasys.heku.util.json.ProtobufJsonFormat
import tech.pegasys.teku.networking.p2p.gossip.PreparedGossipMessage

private val protobufJsonFormat = ProtobufJsonFormat()

fun Rpc.RPC.toJson() = protobufJsonFormat.printToString(this)

fun Rpc.RPC.toStringM() = "Rpc[publishes=\n  " + this.publishList + "]"

private fun publishListToString(list: List<Rpc.Message>) =
    if (list.isEmpty()) "" else {
        "publish=" + list.joinToString(", ") { it.toStringM() }
    }

fun Rpc.Message.toStringM() = "Rpc.Message[topics=${this.topicIDsList}, data=" +
        Bytes.wrap(this.data.toByteArray()) + "]"

fun PreparedGossipMessage.toStringM() = "PreparedGossipMessage[id=" + this.messageId + ", " +
        "origMessage=" + this.originalMessage + "]"