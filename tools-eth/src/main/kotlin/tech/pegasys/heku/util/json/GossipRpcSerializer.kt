package tech.pegasys.heku.util.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.googlecode.protobuf.format.JsonJacksonFormat
import pubsub.pb.Rpc

class GossipRpcSerializer : JsonSerializer<Rpc.RPC>() {
    val format = JsonJacksonFormat()
    override fun serialize(value: Rpc.RPC, gen: JsonGenerator, serializers: SerializerProvider) {
        format.print(value, gen)
    }
}