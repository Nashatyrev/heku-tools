package tech.pegasys.heku.statedb.fixtures

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.util.ext.concat
import java.util.*
import kotlin.math.abs

fun IndexedSsz.allBytes(): Bytes = slices.map { it.sszBytes }.concat()

fun IndexedSsz.findBySszOffset(offset: Int): IndexedSsz.IndexedSlice {
    val searchIdx = Collections.binarySearch(slices.map { it.sszOffset }, offset)
    return slices[abs(searchIdx)]
}

fun IndexedSsz.findByGIndex(gIndex: Long): IndexedSsz.IndexedSlice =
    slices.firstOrNull { it.gIndex == gIndex } ?:
    throw IllegalArgumentException("No slice found with gIndex $gIndex")

