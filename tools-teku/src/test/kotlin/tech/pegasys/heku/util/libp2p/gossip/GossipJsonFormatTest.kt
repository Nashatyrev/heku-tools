package tech.pegasys.heku.util.libp2p.gossip

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.google.protobuf.ByteString
import org.apache.tuweni.bytes.Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.json.ProtobufJsonFormat
import tech.pegasys.heku.util.net.libp2p.gossip.EventSource
import tech.pegasys.heku.util.net.libp2p.gossip.GossipRecorder
import tech.pegasys.heku.util.net.libp2p.gossip.WireEventType
import java.io.StringWriter

class GossipJsonFormatTest {

    val iHave = Rpc.ControlIHave.newBuilder()
        .addMessageIDs(toByteString("0x112233"))
        .addMessageIDs(toByteString("0x11223355"))
        .addMessageIDs(toByteString("0x1122335566"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .addMessageIDs(toByteString("0x112233556677889900aabbccddee"))
        .build()
    val iWant = Rpc.ControlIWant.newBuilder()
        .addMessageIDs(toByteString("0xaabbcc"))
        .build()
    val control = Rpc.RPC.newBuilder().controlBuilder
        .addIhave(iHave)
        .addIwant(iWant)
        .build()
    val msg = Rpc.RPC.newBuilder().setControl(control).build()

    @Test
    fun testToStringJsonPretty() {
        val jsonStr = ProtobufJsonFormat { DefaultPrettyPrinter() }.printToString(msg)
        println(jsonStr)
        assertThat(jsonStr).contains("\n")
    }

    @Test
    @Disabled
    fun `test GossipWireEvent serialized correctly`() {

        val wireEvent = GossipRecorder.JsonGossipWireEvent(
            MTime.now(),
            WireEventType.MESSAGE,
            EventSource.REMOTE,
            msg
        )

        val jsonFormat = ProtobufJsonFormat { DefaultPrettyPrinter() }
//        val bos = ByteArrayOutputStream()
        val sw = StringWriter()
        val jsonGenerator = jsonFormat.factory.createGenerator(sw)

        val mapper = ObjectMapper()
            .registerModule(KotlinModule())
        mapper.writeValue(jsonGenerator, wireEvent)

//        println(String(bos.toByteArray()))
        println(sw.buffer)
    }

    private fun toByteString(hex: String) =
        ByteString.copyFrom(Bytes.fromHexString(hex).toArrayUnsafe())

}