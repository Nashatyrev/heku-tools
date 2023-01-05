package tech.pegasys.heku.util.collections

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.ext.*
import java.util.Collections

fun Bytes.asSparseBytes() = SparseBytes.createSingleSlice(this)
fun Collection<SparseBytes>.mergeNoOverlap() = SparseBytes.mergeNoOverlap(this)

data class SparseBytes (
    val slices: List<Slice>
) {
    init {
        require(
            slices.zipWithNext().all { (prevSlize, nextSlice) ->
                nextSlice.offset >= prevSlize.offset + prevSlize.data.size()
            }
        )
    }

    data class Slice(
        val offset: Int,
        val data: Bytes
    ) {
        val endOffset = offset + data.size()
        val bounds = offset until endOffset

        fun cropSafe(range: IntRange): Slice {
            if (range containsRange bounds) return this
            return cropStrict(range intersectRange bounds)
        }

        fun cropStrict(range: IntRange): Slice {
            if (bounds == range) return this
            require(bounds containsRange range)
            val relSliceOffset = range.first - offset
            return Slice(offset + relSliceOffset, data.slice(relSliceOffset, range.size))
        }
    }

    val size get() = slices.lastOrNull()?.endOffset ?: 0
    val offsets by lazy { slices.map { it.offset } }

    fun withOffset(offset: Int): SparseBytes = SparseBytes(slices.map { it.copy(offset = it.offset + offset) })

    fun isDense() = Companion.isDense(slices)

    fun toDenseBytes(): Bytes {
        require(isDense()) { "Ssz is not dense" }
        return slices.map { it.data }.concat()
    }

    fun slice(offset: Int, length: Int): SparseBytes = slice(offset untilLength length)

    fun slice(indexRange: IntRange): SparseBytes {
        if (indexRange.isEmpty()) return EMPTY
        require(indexRange.first >= 0 && indexRange.last < size)
        val sliceStartIndex = getSliceIndex(indexRange.first)
        val sliceLastIndex = getSliceIndex(indexRange.last)

        return slices[sliceStartIndex..sliceLastIndex]
            .map { it.cropSafe(indexRange) }
            .toSparseBytes()
            .withOffset(-indexRange.first)
    }

    fun sliceDense(offset: Int, length: Int): Bytes = sliceDense(offset untilLength length)
    fun sliceDense(indexRange: IntRange): Bytes = slice(indexRange).toDenseBytes()

    private fun getSliceIndex(offset: Int): Int {
        val sIdx = Collections.binarySearch(offsets, offset)
        return if (sIdx >= 0) sIdx
        else {
            val insertionPoint = -(sIdx + 1)
            insertionPoint - 1
        }
    }

    fun compacted() = SparseBytes(compactSlices(slices))

    fun overlapBy(frontBytes: SparseBytes): SparseBytes {
        // TODO Warn: suboptimal quadratic algo

        fun partitionBackSlice(backSlice: Slice, frontSlice: Slice): List<Slice> {
            val remainBackRanges = backSlice.bounds subtractRange frontSlice.bounds
            return remainBackRanges.map { backSlice.cropStrict(it) }
        }
        fun partitionBackSlices(backSlices: List<Slice>, frontSlice: Slice): List<Slice> =
            backSlices.flatMap { partitionBackSlice(it, frontSlice) }

        var curBackSlices = slices
        for (frontSlice in frontBytes.slices) {
            curBackSlices = partitionBackSlices(curBackSlices, frontSlice)
        }

        return SparseBytes(
            (curBackSlices + frontBytes.slices).sortedBy { it.offset }
        ).compacted()
    }

    companion object {

        val EMPTY = SparseBytes(emptyList())

        fun createSingleSlice(data: Bytes, offset: Int = 0, size: Int = offset + data.size()) =
            SparseBytes(listOf(Slice(offset, data), Slice(size, Bytes.EMPTY))).compacted()

        fun mergeNoOverlap(sszs: Collection<SparseBytes>): SparseBytes =
            SparseBytes(sszs.flatMap { it.slices }.sortedBy { it.offset }).compacted()

        fun compactSlices(slices: Collection<Slice>): List<Slice> {
            val ret = mutableListOf<Slice>()
            val curDenseSlices = mutableListOf<Slice>()
            fun mergeCurSlizes() {
                if (curDenseSlices.isNotEmpty())
                    ret += Slice(curDenseSlices.first().offset, curDenseSlices.map { it.data }.concat())
                curDenseSlices.clear()
            }
            for (slice in slices) {
                if (curDenseSlices.isNotEmpty() && curDenseSlices.last().endOffset != slice.offset) {
                    mergeCurSlizes()
                }
                curDenseSlices += slice
            }
            mergeCurSlizes()
            return ret
        }

        fun isDense(slices: List<Slice>) =
            ((slices.firstOrNull()?.offset ?: 0) == 0) &&
                    slices.zipWithNext().all { (prevSize, nextSlice) ->
                        nextSlice.offset == prevSize.offset + prevSize.data.size()
                    }

        private fun List<Slice>.toSparseBytes() = SparseBytes(this)
    }
}