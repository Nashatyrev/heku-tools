package tech.pegasys.heku.util.ext

import org.apache.tuweni.bytes.Bytes
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

fun File.writeBytesT(bb: Bytes) = this.writeBytes(bb.toArrayUnsafe())

fun File.writeBytesGzipped(bb: Bytes) {
    GZIPOutputStream(this.outputStream()).use {
        it.write(bb.toArrayUnsafe())
    }
}

fun File.readBytesT(): Bytes = Bytes.wrap(this.readBytes())

fun File.readBytesGzipped(): Bytes =
    GZIPInputStream(this.inputStream()).use {
        Bytes.wrap(it.readAllBytes())
    }


