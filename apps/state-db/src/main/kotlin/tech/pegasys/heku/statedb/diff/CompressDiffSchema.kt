package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.util.ext.gzipCompress
import tech.pegasys.heku.util.ext.gzipUncompress
import tech.pegasys.heku.util.ext.snappyCompress
import tech.pegasys.heku.util.ext.snappyUncompress

fun DiffSchema.gzipped() = CompressDiffSchema(this, { it.gzipCompress() }, { it.gzipUncompress()})
fun DiffSchema.snappied() = CompressDiffSchema(this, { it.snappyCompress() }, { it.snappyUncompress()})

class CompressDiffSchema(
    val delegateDiffSchema: DiffSchema,
    val compressor: (Bytes) -> Bytes,
    val uncompressor: (Bytes) -> Bytes,
) : DiffSchema by delegateDiffSchema {

    inner class GzippedDiff(
        val delegate: Diff
    ) : Diff by delegate {

        override fun serialize() = compressor(delegate.serialize())
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff =
        GzippedDiff(delegateDiffSchema.diff(indexedSszDiff))

    override fun deserializeDiff(bytes: Bytes): Diff {
        return GzippedDiff(delegateDiffSchema.deserializeDiff(uncompressor(bytes)))
    }
}