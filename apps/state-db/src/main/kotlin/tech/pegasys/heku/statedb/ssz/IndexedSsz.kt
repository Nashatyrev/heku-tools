package tech.pegasys.heku.statedb.ssz

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.ext.concat
import tech.pegasys.heku.util.ext.isSorted
import tech.pegasys.teku.infrastructure.ssz.SszData
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

data class AlignedIndexedSlice(
    val gIndex: Long,
    val oldSlice: IndexedSsz.Slice,
    val newSlice: IndexedSsz.Slice
) {

    companion object {
    }
}

class IndexedSsz(
    val slices: List<IndexedSlice>
) {

    init {
        assert(slices.map { it.gIndex }.distinct().size == slices.size)
        assert(slices.map { it.sszOffset }.isSorted())
        assert(isDense())
    }

    data class Slice(
        val sszOffset: Int,
        val sszBytes: Bytes
    ) {
        val sszEndOffset get() = sszOffset + sszBytes.size()
    }

    data class IndexedSlice(
        val gIndex: Long,
        val sszOffset: Int,
        val sszBytes: Bytes
    ) {
        val slice get() = Slice(sszOffset, sszBytes)
    }

    private fun isDense() =
        slices
            .zipWithNext()
            .all { it.second.sszOffset == it.first.sszOffset + it.first.sszBytes.size() }

    fun allBytes() = slices.map { it.sszBytes }.concat()

    fun findBySszOffset(offset: Int): IndexedSlice {
        val searchIdx = Collections.binarySearch(slices.map { it.sszOffset }, offset)
        return slices[abs(searchIdx)]
    }

    fun findByGIndex(gIndex: Long): IndexedSlice =
        slices.firstOrNull { it.gIndex == gIndex } ?:
        throw IllegalArgumentException("No slice found with gIndex $gIndex")

    companion object {

        val EMPTY = IndexedSsz(emptyList())

        fun create(schema: SszSchema<*>, tree: TreeNode): IndexedSsz {
            val slices = mutableListOf<IndexedSlice>()
            var offset = 0
            val gIndexCounter = mutableMapOf<Long, AtomicInteger>()
            schema.sszSerializeTree(GIndexUtil.SELF_G_INDEX, tree) { gIndex, bb, off, len ->
                if (len > 0) {
                    val bytes = Bytes.wrap(bb, off, len)
                    val counter = gIndexCounter.computeIfAbsent(gIndex) { AtomicInteger() }.getAndIncrement()
                    slices += IndexedSlice(gIndex, offset, bytes)
                    offset += len
                }
            }
            return IndexedSsz(slices)
        }

        fun create(data: SszData): IndexedSsz = create(data.schema, data.backingNode)
    }

    override fun toString(): String {
        return "IndexedSsz(${slices.size} slices)"
    }
}
