package tech.pegasys.heku.util.json

import com.fasterxml.jackson.core.*
import com.fasterxml.jackson.core.io.IOContext
import com.fasterxml.jackson.core.json.UTF8JsonGenerator
import com.fasterxml.jackson.core.json.WriterBasedJsonGenerator
import com.googlecode.protobuf.format.JsonJacksonFormat
import org.apache.tuweni.bytes.Bytes
import java.io.InputStream
import java.io.OutputStream
import java.io.Writer

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

class HexBinaryJsonFactory : JsonFactory() {
    var prettyPrinterFactory: () -> PrettyPrinter? = { null }
    override fun _createUTF8Generator(out: OutputStream?, ctxt: IOContext?): JsonGenerator {
        // same as super, ust create the overridden Generator instance and modify it
        val gen = UTF8HexBinaryJsonGenerator(
            ctxt,
            _generatorFeatures, _objectCodec, out, _quoteChar
        )
        if (_maximumNonEscapedChar > 0) {
            gen.setHighestNonEscapedChar(_maximumNonEscapedChar)
        }
        if (_characterEscapes != null) {
            gen.characterEscapes = _characterEscapes
        }
        val rootSep = _rootValueSeparator
        if (rootSep !== DEFAULT_ROOT_VALUE_SEPARATOR) {
            gen.setRootValueSeparator(rootSep)
        }
        modifyGenerator(gen)
        return gen
    }

    override fun _createGenerator(out: Writer?, ctxt: IOContext?): JsonGenerator {
        // same as super, ust create the overridden Generator instance and modify it
        val gen = WriterHexBinaryJsonGenerator(
            ctxt,
            _generatorFeatures, _objectCodec, out, _quoteChar
        )
        if (_maximumNonEscapedChar > 0) {
            gen.setHighestNonEscapedChar(_maximumNonEscapedChar)
        }
        if (_characterEscapes != null) {
            gen.characterEscapes = _characterEscapes
        }
        val rootSep = _rootValueSeparator
        if (rootSep !== DEFAULT_ROOT_VALUE_SEPARATOR) {
            gen.setRootValueSeparator(rootSep)
        }
        modifyGenerator(gen)
        return gen
    }

    private fun modifyGenerator(gen: JsonGenerator) {
        val prettyPrinter = prettyPrinterFactory()
        if (prettyPrinter != null) {
            gen.prettyPrinter = prettyPrinter
        }
        gen.configure(JsonGenerator.Feature.QUOTE_FIELD_NAMES, false)
        gen.configure(JsonGenerator.Feature.QUOTE_NON_NUMERIC_NUMBERS, false)
    }
}

class UTF8HexBinaryJsonGenerator(
    ctxt: IOContext?,
    features: Int,
    codec: ObjectCodec?,
    out: OutputStream?,
    quoteChar: Char
) : UTF8JsonGenerator(ctxt, features, codec, out, quoteChar) {

    override fun writeBinary(b64variant: Base64Variant, data: ByteArray, offset: Int, len: Int) {
        writeBytes(Bytes.wrap(data, offset, len))
    }

    override fun writeBinary(b64variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        val bytes = Bytes.wrap(data.readNBytes(dataLength))
        writeBytes(bytes)
        return bytes.size()
    }

    fun writeBytes(bb: Bytes) {
        writeString(bb.toString())
    }
}

class WriterHexBinaryJsonGenerator(
    ctxt: IOContext?,
    features: Int,
    codec: ObjectCodec?,
    out: Writer?,
    quoteChar: Char
) : WriterBasedJsonGenerator(ctxt, features, codec, out, quoteChar) {

    override fun writeBinary(b64variant: Base64Variant, data: ByteArray, offset: Int, len: Int) {
        writeBytes(Bytes.wrap(data, offset, len))
    }

    override fun writeBinary(b64variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        val bytes = Bytes.wrap(data.readNBytes(dataLength))
        writeBytes(bytes)
        return bytes.size()
    }

    fun writeBytes(bb: Bytes) {
        writeString(bb.toString())
    }
}