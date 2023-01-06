package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.collections.SparseBytes

fun SparseBytes.toDiffResult() = DiffResult.fromBytes(this)

fun interface DiffResult {

    fun getBytes(): SparseBytes

    companion object {
        fun fromBytes(bb: SparseBytes) = DiffResult { bb }
    }
}

interface Diff {

    fun apply(parentSsz: DiffResult): DiffResult

    fun serialize(): Bytes
}