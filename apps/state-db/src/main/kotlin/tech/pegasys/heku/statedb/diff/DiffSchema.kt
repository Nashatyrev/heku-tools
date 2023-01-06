package tech.pegasys.heku.statedb.diff

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz

interface DiffSchema {

    fun diff(indexedSszDiff: AlignedIndexedSsz): Diff

    fun deserializeDiff(bytes: Bytes): Diff
}

