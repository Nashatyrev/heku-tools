package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.statedb.SimpleBytesCodec
import tech.pegasys.heku.util.collections.mergeNoOverlap
import tech.pegasys.heku.util.ext.distinctConsecutiveBy

fun DiffSchema.toSparse(selector: GIndexSelector) = SparseDiffSchema(this, selector)

class SparseDiffSchema(
    val diffSchema: DiffSchema,
    val selector: GIndexSelector
) : DiffSchema {

    class SparseDiff(
        val slices: List<Diff>
    ) : Diff {

        override fun apply(parentSsz: DiffResult): DiffResult {
            val result = slices
                .map {
                    it.apply(parentSsz)
                }

            return if (result.size == 1) {
                result[0]
            } else {
                result
                    .map { it.getBytes() }
                    .mergeNoOverlap()
                    .toDiffResult()
            }
        }

        override fun serialize(): Bytes {
            val writer = SimpleBytesCodec.Serializer()
            writer.writeInt(slices.size)
            for (slice in slices) {
                writer.writeBytes(slice.serialize())
            }
            return writer.getResult()
        }
    }

    override fun deserializeDiff(bytes: Bytes): Diff {
        val reader = SimpleBytesCodec.Deserializer(bytes)
        val size = reader.readInt()
        return (0 until size)
            .map { diffSchema.deserializeDiff(reader.readBytes()) }
            .let { SparseDiff(it) }
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff {
        return indexedSszDiff.alignedSlices
            .distinctConsecutiveBy(
                { selector.isSelected(it.gIndex) },
                { selected, ssz ->
                    if (selected) ssz
                    else null
                }
            )
            .filterNotNull()
            .map {
                diffSchema.diff(AlignedIndexedSsz(it))
            }
            .let { SparseDiff(it) }
    }
}