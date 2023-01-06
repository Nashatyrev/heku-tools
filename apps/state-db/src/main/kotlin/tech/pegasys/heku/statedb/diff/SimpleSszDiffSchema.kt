package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.diff.DiffResult.Companion.toDiffResult
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.util.collections.asSparseBytes
import tech.pegasys.heku.util.ext.concat
import tech.pegasys.heku.util.ext.sliceSafe

class SimpleSszDiffSchema : DiffSchema {

    private data class DiffRichSlice(
        val oldOffset: Int,
        val oldBytes: Bytes,
        val newBytes: Bytes
    ) {
        fun toDiffSlice() = DiffSlice(oldOffset, oldBytes.size(), newBytes)
    }

    data class DiffSlice(
        val oldOffset: Int,
        val oldLength: Int,
        val newBytes: Bytes
    )

    class SimpleDiff(
        val oldRange: IntRange,
        val newRange: IntRange,
        val slices: List<DiffSlice>
    ) : Diff {

        override fun serialize(): Bytes {
            val serializer = SimpleBytesCodec.Serializer()
            serializer.writeInt(slices.size)
            serializer.writeRange(oldRange)
            serializer.writeRange(newRange)
            slices.forEach {
                serializer.writeInt(it.oldOffset)
                serializer.writeInt(it.oldLength)
                serializer.writeBytes(it.newBytes)
            }
            return serializer.getResult()
        }

        override fun apply(parentSsz: DiffResult): DiffResult {
            var oldOffset = oldRange.first
            val adjustedSlices = slices + DiffSlice(oldRange.last + 1, 0, Bytes.EMPTY)
            return adjustedSlices.flatMap { slice ->
                val ret = listOf(
                    parentSsz.getBytes().sliceDense(oldOffset, slice.oldOffset - oldOffset),
                    slice.newBytes
                )
                oldOffset = slice.oldOffset + slice.oldLength
                ret
            }
                .concat()
                .asSparseBytes()
                .withOffset(newRange.first)
                .toDiffResult()
        }
    }

    override fun deserializeDiff(bytes: Bytes): SimpleDiff {
        val reader = SimpleBytesCodec.Deserializer(bytes)
        val sliceCount = reader.readInt()
        val oldRange = reader.readRange()
        val newRange = reader.readRange()
        return (0 until sliceCount).map {
            DiffSlice(reader.readInt(), reader.readInt(), reader.readBytes())
        }
            .let { SimpleDiff(oldRange, newRange, it) }
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff {
        val differentSlices = indexedSszDiff.filterDifferentSlices()
        val diff1 = toPlainDiff(differentSlices)
        assert(run {
            val oldOffsets = diff1.map { it.oldOffset }
            oldOffsets == oldOffsets.sorted()
        })
        val diff2 = cropDuplicateBytes(diff1)
        val diff3 = mergeSlices(diff2)
        return SimpleDiff(
            indexedSszDiff.oldRange,
            indexedSszDiff.newRange,
            diff3.map { it.toDiffSlice() }
        )
    }

    companion object {

        private fun toPlainDiff(indexedDiff: List<AlignedIndexedSsz.AlignedSlice>): List<DiffRichSlice> {
            return indexedDiff.map {
                DiffRichSlice(it.oldSlice.sszOffset, it.oldSlice.sszBytes, it.newSlice.sszBytes)
            }
        }

        private fun cropDuplicateBytes(diff: List<DiffRichSlice>): List<DiffRichSlice> {
            return diff.flatMap { slice ->
                when {
                    slice.newBytes.size() <= 32 -> listOf(slice)
                    slice.oldBytes.isEmpty -> listOf(slice)
                    else -> {
                        val compactSlices = bytesDiff(slice.oldBytes, slice.newBytes, 32)
                        compactSlices.map { (relOffset, newBytes) ->
                            DiffRichSlice(
                                slice.oldOffset + relOffset,
                                slice.oldBytes.sliceSafe(relOffset, newBytes.size()),
                                newBytes
                            )
                        }
                        listOf(slice)
                    }
                }
            }
        }

        private fun mergeSlices(diff: List<DiffRichSlice>): List<DiffRichSlice> {
            val mergedSlices = mutableListOf<MutableList<DiffRichSlice>>()
            var curMerged = mutableListOf<DiffRichSlice>()
            mergedSlices += curMerged
            for (slice in diff) {
                if (curMerged.isNotEmpty()) {
                    val lastSlice = curMerged.last()
                    val lastSliceEnd = lastSlice.oldOffset + lastSlice.oldBytes.size()
                    if (slice.oldOffset > lastSliceEnd) {
                        curMerged = mutableListOf()
                        mergedSlices += curMerged
                    }
                }
                curMerged += slice
            }

            return mergedSlices
                .filter { it.isNotEmpty() }
                .map { mergedSlice ->
                    DiffRichSlice(
                        mergedSlice.first().oldOffset,
                        Bytes.wrap(mergedSlice.map { it.oldBytes }),
                        Bytes.wrap(mergedSlice.map { it.newBytes })
                    )
                }
        }

        fun bytesDiff(b1: Bytes, b2: Bytes, granularity: Int): List<Pair<Int, Bytes>> {
            val ret = mutableListOf<Pair<Int, Bytes>>()
            var off = 0
            val minLen = Integer.min(b1.size(), b2.size())
            while (true) {
                while (off < minLen && b1[off] == b2[off]) off++
                if (off == minLen) break
                val diffStart = off
                var diffEnd = diffStart
                while (off < minLen && off - diffEnd < granularity) {
                    if (b1[off] != b2[off]) diffEnd = off
                    off++
                }
                ret += diffStart to b2.slice(diffStart, diffEnd - diffStart)
            }
            if (b2.size() > b1.size()) {
                ret += b1.size() to b2.slice(b1.size())
            }
            return ret
        }
    }
}