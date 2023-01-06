package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.ext.fromUVariantBytes
import tech.pegasys.heku.util.ext.toUVariantBytes

class BytesWriter {
    private val bytesList = mutableListOf<Bytes>()

    fun getResult() = Bytes.wrap(bytesList)

    fun writeInt(i: Int) {
        bytesList += i.toUVariantBytes()
    }

    fun writeBytes(bb: Bytes) {
        writeInt(bb.size())
        bytesList += bb
    }

    fun writeRange(r: IntRange) {
        writeInt(r.first)
        writeInt(r.last)
    }
}

class BytesReader(val bytes: Bytes) {
    private var offset = 0

    fun readInt(): Int {
        val (value, bytesLength) = bytes.slice(offset).fromUVariantBytes()
        offset += bytesLength
        return value.toInt()
    }

    fun readBytes(): Bytes {
        val len = readInt()
        val ret = bytes.slice(offset, len)
        offset += len
        return ret
    }
    fun readRange(): IntRange = readInt()..readInt()
}