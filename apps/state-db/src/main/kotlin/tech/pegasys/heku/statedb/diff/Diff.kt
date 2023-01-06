package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.util.collections.SparseBytes

interface Diff {

    fun apply(parentSsz: DiffResult): DiffResult

    fun serialize(): Bytes
}