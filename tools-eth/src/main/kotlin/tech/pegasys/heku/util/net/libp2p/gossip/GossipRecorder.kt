package tech.pegasys.heku.util.net.libp2p.gossip

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.libp2p.core.PeerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import org.apache.tuweni.bytes.Bytes
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.ext.consume
import tech.pegasys.heku.util.json.GossipRpcSerializer
import tech.pegasys.heku.util.json.ProtobufJsonFormat
import java.nio.file.Path

class GossipRecorder(
    val wireMessageFlow: Flow<GossipWireEvent>,
    val parsedMessageFlow: Flow<PreparedGossipMessageAndTopic>,
    val dataDir: Path = Path.of("gossip.record"),
    val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    internal data class JsonGossipWireEvent(
        val time: MTime,
        val type: WireEventType,
        val source: EventSource,

        @JsonSerialize(using = GossipRpcSerializer::class)
        val message: Rpc.RPC? = null
    ) {
        companion object {
            fun fromGossipWireEvent(evt: GossipWireEvent) =
                JsonGossipWireEvent(evt.time, evt.type, evt.source, evt.message)
        }
    }

    internal data class JsonPreparedGossipMessageAndTopic(
        val time: MTime,
        val topic: String,
        @JsonSerialize(using = tech.pegasys.heku.util.json.BytesSerializer::class)
        val messageId: Bytes,
        @JsonSerialize(using = tech.pegasys.heku.util.json.BytesSerializer::class)
        val originalMessage: Bytes,
        @JsonSerialize(using = tech.pegasys.heku.util.json.BytesSerializer::class)
        val decodedMessage: Bytes
    ) {
        companion object {
            fun fromOriginalObject(msg: PreparedGossipMessageAndTopic) =
                JsonPreparedGossipMessageAndTopic(
                    MTime.now(),
                    msg.beaconTopic.topic,
                    msg.messageId,
                    msg.preparedMessage.originalMessage,
                    msg.preparedMessage.decodedMessage.decodedMessageOrElseThrow
                )

        }
    }

    data class JsonStuff(
        val generator: JsonGenerator,
        val mapper: ObjectMapper
    )

    private val jsonFormat = ProtobufJsonFormat { DefaultPrettyPrinter() }
    private val generatorMap = mutableMapOf<PeerId, JsonStuff>()

    private fun createGenerator(file: String) =
        JsonStuff(
            jsonFormat.factory.createGenerator(
                dataDir.resolve(file).toFile().bufferedWriter(bufferSize = 32 * 1024)
            ),
            createMapper()
        )

    private fun createMapper() = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    private fun getJsonStuff(peer: PeerId) =
        generatorMap.computeIfAbsent(peer) { createGenerator("wire.gossip.$it.log") }

    fun start() {
        dataDir.toFile().mkdirs()
        wireMessageFlow.consume(ioScope) {
            val json = getJsonStuff(it.peerId)
            val evt = JsonGossipWireEvent.fromGossipWireEvent(it)
            json.mapper.writeValue(json.generator, evt)
        }
        val parsedMessagesJson = createGenerator("parsed.gossip.log")
        parsedMessageFlow.consume(ioScope) {
            val evt = JsonPreparedGossipMessageAndTopic.fromOriginalObject(it)
            parsedMessagesJson.mapper.writeValue(parsedMessagesJson.generator, evt)
        }
    }
}