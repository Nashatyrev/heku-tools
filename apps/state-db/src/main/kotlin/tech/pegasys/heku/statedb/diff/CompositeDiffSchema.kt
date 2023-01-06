package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.statedb.SimpleBytesCodec
import tech.pegasys.heku.statedb.diff.DiffResult.Companion.toDiffResult
import tech.pegasys.heku.util.collections.mergeNoOverlap

class CompositeDiffSchema(
    val components: List<DiffSchema>
) : DiffSchema {

    constructor(vararg components: DiffSchema) : this(components.toList())

    class CompositeDiff(
        val componentDiffs: List<Diff>
    ) : Diff {

        override fun apply(parentSsz: DiffResult): DiffResult =
            componentDiffs
                .map {
                    it.apply(parentSsz).getBytes()
                }
                .mergeNoOverlap()
                .toDiffResult()

        override fun serialize(): Bytes {
            val writer = SimpleBytesCodec.Serializer()
            writer.writeInt(componentDiffs.size)
            for (componentDiff in componentDiffs) {
                writer.writeBytes(componentDiff.serialize())
            }
            return writer.getResult()
        }
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff =
        components
            .map { it.diff(indexedSszDiff) }
            .let { CompositeDiff(it) }


    override fun deserializeDiff(bytes: Bytes): Diff {
        val reader = SimpleBytesCodec.Deserializer(bytes)
        val size = reader.readInt()
        return (0 until size)
            .map { components[it].deserializeDiff(reader.readBytes()) }
            .let { CompositeDiff(it) }
    }
}