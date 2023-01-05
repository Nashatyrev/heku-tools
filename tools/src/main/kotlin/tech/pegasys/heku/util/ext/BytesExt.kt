package tech.pegasys.heku.util.ext

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.MutableBytes
import org.xerial.snappy.Snappy
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.Integer.min
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.experimental.or

fun ByteArray.toBytes() = Bytes.wrap(this)

fun Bytes.snappyCompress(): Bytes =
    Bytes.wrap(Snappy.compress(this.toArrayUnsafe()))

fun Bytes.snappyUncompress(): Bytes =
    Bytes.wrap(Snappy.uncompress(this.toArrayUnsafe()))

fun Bytes.gzipCompress(): Bytes {
    val bos = ByteArrayOutputStream()
    GZIPOutputStream(bos).use {
        it.write(this.toArrayUnsafe())
    }
    return Bytes.wrap(bos.toByteArray())
}

fun Bytes.gzipUncompress(): Bytes {
    val bis = ByteArrayInputStream(this.toArrayUnsafe())
    val bytes = GZIPInputStream(bis).use {
        it.readAllBytes()
    }
    return Bytes.wrap(bytes)
}

fun List<Bytes>.concat() =
    when {
        size == 1 -> this[0]
        size <= 4 -> Bytes.wrap(this)
        else -> this.concatFull()
    }

fun List<Bytes>.concatFull(): Bytes {
    val size = sumOf { it.size() }
    val dest = MutableBytes.create(size)
    var offset = 0
    for (bb in this) {
        bb.copyTo(dest, offset)
        offset += bb.size()
    }
    return dest
}

fun Bytes.zeroPadRight(targetLen: Int) =
    if (this.size() >= targetLen) this
    else Bytes.wrap(this, Bytes.wrap(ByteArray(targetLen - this.size())))

fun Bytes.zeroPadLeft(targetLen: Int) =
    if (this.size() >= targetLen) this
    else Bytes.wrap(Bytes.wrap(ByteArray(targetLen - this.size())), this)

fun Number.toUVariantBytes(): Bytes {
    // Create a mutable list to hold the bytes
    val bytes = mutableListOf<Byte>()

    // Convert the int to a long, since the uvariant encoding
    // supports long values
    var value = this.toLong()

    // Loop while the value is not 0
    while (value != 0L) {
        // Extract the lowest 7 bits of the value and add them
        // to the list of bytes as a signed byte
        bytes.add((value and 0x7F).toByte())

        // Shift the value right by 7 bits
        value = value shr 7

        // If the value is not 0, set the high bit of the byte
        // to indicate that more bytes follow
        if (value != 0L) {
            bytes[bytes.size - 1] = bytes[bytes.size - 1] or 0x80.toByte()
        }
    }
    if (bytes.isEmpty()) bytes += 0x00
    return Bytes.wrap(bytes.toByteArray())
}

data class UVarResult(
    val value: Long,
    val bytesLength: Int
)

fun Bytes.fromUVariantBytes(): UVarResult {
    var x: Long = 0
    var s = 0

    for (i in 0 until min(8, this.size())) {
        val b = 0xFF and this.get(i).toInt()
        if (b < 0x80) {
            return UVarResult(x or (b.toLong() shl s), i + 1)
        }
        x = x or (b.toLong() and 0x7f shl s)
        s += 7
    }

    throw IllegalArgumentException("EOF deserializing UVAR from $this")
}

fun Bytes.slice(intRange: IntRange) =
    if (intRange.isEmpty()) Bytes.EMPTY
    else this.slice(intRange.first, intRange.last - intRange.first + 1)

val Bytes.indices get() =
    0 until this.size()

fun Bytes.sliceSafe(intRange: IntRange): Bytes =
    this.slice(this.indices intersectRange intRange)

fun Bytes.sliceSafe(startOff: Int, length: Int = this.size() - startOff): Bytes =
    this.sliceSafe(startOff untilLength length)
