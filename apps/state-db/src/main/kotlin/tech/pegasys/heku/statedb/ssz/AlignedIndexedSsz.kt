package tech.pegasys.heku.statedb.ssz

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.ext.untilLength
import tech.pegasys.teku.infrastructure.ssz.SszData
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode

class AlignedIndexedSsz(
    val alignedSlices: List<AlignedSlice>
) {

    data class Slice(
        val sszOffset: Int,
        val sszBytes: Bytes
    ) {
        val sszEndOffset get() = sszOffset + sszBytes.size()
    }

    data class AlignedSlice(
        val gIndex: Long,
        val oldSlice: Slice,
        val newSlice: Slice
    )

    val oldSize= alignedSlices.sumOf { it.oldSlice.sszBytes.size() }
    val oldOffset = alignedSlices.firstOrNull()?.oldSlice?.sszOffset ?: 0
    val oldRange = oldOffset untilLength oldSize
    val newSize = alignedSlices.sumOf { it.newSlice.sszBytes.size() }
    val newOffset = alignedSlices.firstOrNull()?.newSlice?.sszOffset ?: 0
    val newRange = newOffset untilLength newSize

    init {
        assert(isDense())
    }

    fun isDense() =
        alignedSlices
            .zipWithNext()
            .all {
                it.second.oldSlice.sszOffset == it.first.oldSlice.sszOffset + it.first.oldSlice.sszBytes.size() &&
                it.second.newSlice.sszOffset == it.first.newSlice.sszOffset + it.first.newSlice.sszBytes.size()
            }


    fun withRelativeOffsets() = AlignedIndexedSsz(
        alignedSlices.map {
            it.copy(
                oldSlice = it.oldSlice.copy(sszOffset = it.oldSlice.sszOffset - oldOffset),
                newSlice = it.newSlice.copy(sszOffset = it.newSlice.sszOffset - newOffset)
            )
        }
    )

    fun filterDifferentSlices() = alignedSlices.filter { it.oldSlice.sszBytes != it.newSlice.sszBytes }

    fun subrange(indexes: IntRange) = AlignedIndexedSsz(alignedSlices.slice(indexes))

    companion object {
        fun create(sszSchema: SszSchema<*>, oldTree: TreeNode, newTree: TreeNode): AlignedIndexedSsz {
            val oldSsz = IndexedSsz.create(sszSchema, oldTree)
            val newSsz = IndexedSsz.create(sszSchema, newTree)
            val alignedIndexedSlices = align(oldSsz, newSsz)
//            val differentSlices = alignedIndexedSlices.filter { it.oldSlice.sszBytes != it.newSlice.sszBytes }
            return AlignedIndexedSsz(alignedIndexedSlices)
        }

        fun <T : SszData> create(oldData: T, newData: T): AlignedIndexedSsz {
            require(oldData.schema == newData.schema)
            return create(oldData.schema, oldData.backingNode, newData.backingNode)
        }

        fun create(oldSsz: IndexedSsz, newSsz: IndexedSsz): AlignedIndexedSsz =
            AlignedIndexedSsz(align(oldSsz, newSsz))

        private fun IndexedSsz.IndexedSlice.toSlice() = Slice(sszOffset, sszBytes)


        private fun align(oldSsz: IndexedSsz, newSsz: IndexedSsz): List<AlignedSlice> {
            val ret = mutableListOf<AlignedSlice>()
            val oldGIndexMap = oldSsz.slices
                .mapIndexed { index, indexedSlice ->  indexedSlice.gIndex to index}
                .toMap()
            val newGIndexMap = newSsz.slices
                .mapIndexed { index, indexedSlice ->  indexedSlice.gIndex to index}
                .toMap()

            var oldIndex = 0
            var newIndex = 0

            while(true) {
                while (oldIndex < oldSsz.slices.size) {
                    val oldSlice = oldSsz.slices[oldIndex]
                    if (oldSlice.gIndex in newGIndexMap) {
                        break
                    }
                    val newOffset = ret.lastOrNull()?.newSlice?.sszEndOffset ?: 0
                    ret += AlignedSlice(
                        oldSlice.gIndex,
                        oldSlice.toSlice(),
                        Slice(newOffset, Bytes.EMPTY)
                    )
                    oldIndex++
                }

                while (newIndex < newSsz.slices.size) {
                    val newSlice = newSsz.slices[newIndex]
                    if (newSlice.gIndex in oldGIndexMap) {
                        break
                    }
                    val oldOffset = ret.lastOrNull()?.oldSlice?.sszEndOffset ?: 0
                    ret += AlignedSlice(
                        newSlice.gIndex,
                        Slice(oldOffset, Bytes.EMPTY),
                        newSlice.toSlice()
                    )
                    newIndex++
                }

                if (newIndex in newSsz.slices.indices && oldIndex in oldSsz.slices.indices) {
                    val oldSlice = oldSsz.slices[oldIndex]
                    val newSlice = newSsz.slices[newIndex]

                    assert(oldSlice.gIndex == newSlice.gIndex)

                    ret += AlignedSlice(oldSlice.gIndex, oldSlice.toSlice(), newSlice.toSlice())
                    oldIndex++
                    newIndex++
                }

                if (newIndex !in newSsz.slices.indices && oldIndex !in oldSsz.slices.indices) {
                    break
                }
            }
            return ret
        }
    }
}
