package tech.pegasys.heku.statedb.diff

import io.libp2p.etc.types.toBytesBigEndian
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.MutableBytes
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.util.collections.SparseBytes
import tech.pegasys.heku.util.ext.concat
import tech.pegasys.heku.util.ext.toBytes
import java.nio.ByteOrder

class UInt64DiffSchema : DiffSchema {

    class UInt64Diff(
        val oldOffset: Int,
        val oldSize: Int,
        val newOffset: Int,
        val diffs: LongArray
    ) : Diff {

        override fun serialize(): Bytes {
            val writer = BytesWriter()
            writer.writeInt(oldOffset)
            writer.writeInt(oldSize)
            writer.writeInt(newOffset)
            writer.writeBytes(
                diffs.map {
                    it.toBytesBigEndian().toBytes()
                }.concat()
            )
            return writer.getResult()
        }

        class UInt64DiffResult(
            val offsset: Int,
            val values: LongArray
        ) : DiffResult {

            override fun getBytes(): SparseBytes {
                val targetBytes = MutableBytes.create(values.size * 8)
                for (i in values.indices) {
                    targetBytes.setLongLittleEndian(i * 8, values[i])
                }
                return SparseBytes.createSingleSlice(targetBytes, offsset)
            }

            companion object {
                fun fromBytes(ofset: Int, bb: Bytes) = UInt64DiffResult(ofset, bb.toLongArray())
            }
        }

        override fun apply(parentSsz: DiffResult): DiffResult {
            val oldResult = parentSsz as? UInt64DiffResult
                ?: UInt64DiffResult.fromBytes(oldOffset, parentSsz.getBytes().sliceDense(oldOffset, oldSize))
            val newValues = LongArray(diffs.size)
            for (i in newValues.indices) {
                val oldVal = if (i < oldResult.values.size) oldResult.values[i] else 0L
                newValues[i] = oldVal + diffs[i]
            }
            return UInt64DiffResult(newOffset, newValues)
        }
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff {
        val oldVals = indexedSszDiff.alignedSlices.map { it.oldSlice.sszBytes }.concat().toLongArray().toList()
        val newVals = indexedSszDiff.alignedSlices.map { it.newSlice.sszBytes }.concat().toLongArray().toList()
        require(newVals.size >= oldVals.size) { "List shrinking is not yet supported" }
        val paddedOldVals = oldVals.zeroPadTrailing(newVals.size)
        val diffs = paddedOldVals.zip(newVals) { old, new ->
            new - old
        }
        return UInt64Diff(
            indexedSszDiff.oldOffset,
            indexedSszDiff.oldSize,
            indexedSszDiff.newOffset,
            diffs.toLongArray()
        )
    }

    override fun deserializeDiff(bytes: Bytes): Diff {
        val reader = BytesReader(bytes)
        val oldOffset = reader.readInt()
        val oldSize = reader.readInt()
        val newOffset = reader.readInt()
        val diffBytes = reader.readBytes()
        val diffs = LongArray(diffBytes.size() / 8)
        for (i in diffs.indices) {
            diffs[i] = getLongBigEndian(diffBytes, i * 8)
        }
//        val diffs = (0 until diffBytes.size() step 8)
//            .map {
//                diffBytes.slice(it, 8).toArrayUnsafe().toLongBigEndian()
//            }
        return UInt64Diff(oldOffset, oldSize, newOffset, diffs)
    }

    companion object {

        private fun getLongBigEndian(arr: Bytes, off: Int): Long {
            return (arr[off + 0].toLong() and 0xFF shl 56) or
                    (arr[off + 1].toLong() and 0xFF shl 48) or
                    (arr[off + 2].toLong() and 0xFF shl 40) or
                    (arr[off + 3].toLong() and 0xFF shl 32) or
                    (arr[off + 4].toLong() and 0xFF shl 24) or
                    (arr[off + 5].toLong() and 0xFF shl 16) or
                    (arr[off + 6].toLong() and 0xFF shl 8) or
                    (arr[off + 7].toLong() and 0xFF)

        }

        private fun MutableBytes.setLongLittleEndian(off: Int, l: Long) {
            this.set(off, (l and 0xFF).toByte())
            this.set(off + 1, ((l shr 8) and 0xFF).toByte())
            this.set(off + 2, ((l shr 16) and 0xFF).toByte())
            this.set(off + 3, ((l shr 24) and 0xFF).toByte())
            this.set(off + 4, ((l shr 32) and 0xFF).toByte())
            this.set(off + 5, ((l shr 40) and 0xFF).toByte())
            this.set(off + 6, ((l shr 48) and 0xFF).toByte())
            this.set(off + 7, ((l shr 56) and 0xFF).toByte())
        }

        private fun Bytes.toLongArray(): LongArray {
            val ret = LongArray(this.size() / 8)
            for (i in ret.indices) {
                ret[i] = this.getLong(i, ByteOrder.LITTLE_ENDIAN)
            }
            return ret
        }

        private fun List<Long>.zeroPadTrailing(targetSize: Int) =
            this + List(targetSize - this.size) { 0L }

    }
}