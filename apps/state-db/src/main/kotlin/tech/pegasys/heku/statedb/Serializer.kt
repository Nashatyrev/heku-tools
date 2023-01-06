package tech.pegasys.heku.statedb

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.ext.fromUVariantBytes
import tech.pegasys.heku.util.ext.toUVariantBytes

interface Codec {

    interface Serializer {
        fun writeInt(i: Int)
        fun writeRange(r: IntRange) {
            writeInt(r.first)
            writeInt(r.last)
        }
        fun writeBytes(bb: Bytes)
    }

    interface Deserializer {
        fun readInt(): Int
        fun readRange(): IntRange = readInt()..readInt()
        fun readBytes(): Bytes
    }
}

interface SimpleBytesCodec : Codec {

    class Serializer : Codec.Serializer {
        private val bytesList = mutableListOf<Bytes>()

        fun getResult() = Bytes.wrap(bytesList)

        override fun writeInt(i: Int) {
            bytesList += i.toUVariantBytes()
        }

        override fun writeBytes(bb: Bytes) {
            writeInt(bb.size())
            bytesList += bb
        }
    }

    class Deserializer(val bytes: Bytes) : Codec.Deserializer {
        private var offset = 0

        override fun readInt(): Int {
            val (value, bytesLength) = bytes.slice(offset).fromUVariantBytes()
            offset += bytesLength
            return value.toInt()
        }

        override fun readBytes(): Bytes {
            val len = readInt()
            val ret = bytes.slice(offset, len)
            offset += len
            return ret
        }
    }
}