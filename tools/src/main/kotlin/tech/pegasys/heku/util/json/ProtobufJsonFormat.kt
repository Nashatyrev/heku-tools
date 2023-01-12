package tech.pegasys.heku.util.json

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.PrettyPrinter
import com.googlecode.protobuf.format.JsonJacksonFormat
import java.io.OutputStream

class ProtobufJsonFormat(
    val prettyPrinterFactory: () -> PrettyPrinter? = { null }
) : JsonJacksonFormat() {
    val factory = HexBinaryJsonFactory().also {
        it.prettyPrinterFactory = prettyPrinterFactory
    }

    override fun createGenerator(output: OutputStream): JsonGenerator {
        return factory.createGenerator(output)
    }
}