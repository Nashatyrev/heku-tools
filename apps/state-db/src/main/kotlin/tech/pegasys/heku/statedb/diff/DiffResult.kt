package tech.pegasys.heku.statedb.diff

import tech.pegasys.heku.util.collections.SparseBytes

fun interface DiffResult {

    fun getBytes(): SparseBytes

    companion object {

        fun SparseBytes.toDiffResult() = DiffResult { this }
    }
}