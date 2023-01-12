package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.diff.DiffResult.Companion.toDiffResult
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.util.collections.asSparseBytes
import tech.pegasys.heku.util.ext.concat

class SnapshotDiffSchema() : DiffSchema {

    class SnapshotDiff(val snapshotSsz: Bytes) : Diff {
        override fun serialize(): Bytes = snapshotSsz
        override fun apply(parentSsz: DiffResult): DiffResult = snapshotSsz.asSparseBytes().toDiffResult()
    }

    override fun diff(indexedSszDiff: AlignedIndexedSsz): Diff =
        SnapshotDiff(indexedSszDiff.alignedSlices.map { it.newSlice.sszBytes }.concat())

    override fun deserializeDiff(bytes: Bytes): Diff {
        return SnapshotDiff(bytes)
    }
}

