package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes

interface Diff {

    fun apply(parentSsz: DiffResult): DiffResult

    fun serialize(): Bytes
}