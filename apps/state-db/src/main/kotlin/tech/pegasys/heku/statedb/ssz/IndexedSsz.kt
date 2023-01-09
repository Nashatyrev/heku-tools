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

class IndexedSsz(
    val slices: List<IndexedSlice>
) {

    init {
        assert(slices.map { it.gIndex }.distinct().size == slices.size)
        assert(slices.map { it.sszOffset }.isSorted())
        assert(isDense(slices))
    }

    data class IndexedSlice(
        val gIndex: Long,
        val sszOffset: Int,
        val sszBytes: Bytes
    )

    companion object {

        val EMPTY = IndexedSsz(emptyList())

        fun create(schema: SszSchema<*>, tree: TreeNode): IndexedSsz {
            val slices = mutableListOf<IndexedSlice>()
            var offset = 0
            schema.sszSerializeTree(GIndexUtil.SELF_G_INDEX, tree) { gIndex, bb, off, len ->
                if (len > 0) {
                    val bytes = Bytes.wrap(bb, off, len)
                    slices += IndexedSlice(gIndex, offset, bytes)
                    offset += len
                }
            }
            return IndexedSsz(slices)
        }

        fun create(data: SszData): IndexedSsz = create(data.schema, data.backingNode)

        private fun isDense(slices: List<IndexedSlice>) =
            slices
                .zipWithNext()
                .all { it.second.sszOffset == it.first.sszOffset + it.first.sszBytes.size() }
    }

    override fun toString(): String {
        return "IndexedSsz(${slices.size} slices)"
    }
}
