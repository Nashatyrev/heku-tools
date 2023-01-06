package tech.pegasys.heku.statedb

import kotlinx.coroutines.runBlocking
import org.apache.tuweni.bytes.Bytes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tech.pegasys.heku.statedb.ssz.SszPath
import tech.pegasys.heku.statedb.diff.*
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.util.ReadableSize
import tech.pegasys.heku.util.collections.asSparseBytes
import tech.pegasys.heku.util.type.asSlot
import tech.pegasys.heku.util.type.epochs
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class IncrementalSchemaTest {

    val stateLoader = FileEpochStateLoader(Eth2Network.MAINNET, File("./work.dir/state.dumper.MAINNET/states"))
    val startEpoch = 130143.epochs
    val endEpoch = startEpoch + 270

    @Test
    fun sanityTest() = runBlocking {
        val state1 = stateLoader.loadState(startEpoch)
        val state2 = stateLoader.loadState(startEpoch + 1)
        val stateSchema = state1.schema

        val sszDiffSchema = SimpleSszDiffSchema()
        val gzippedSchema = sszDiffSchema.gzipped()
        val diffSchema = gzippedSchema

        val diff1 = diffSchema.diff(AlignedIndexedSsz.create(state1, state2))

        val serializedDiff = diff1.serialize()
        val diff2 = diffSchema.deserializeDiff(serializedDiff)

//        println("Removed: " + diff2.slices.sumOf { it.oldLength })
//        println("Added  : " + diff2.slices.sumOf { it.newBytes.size() })
        println("Serialized diff size: " + ReadableSize.create(serializedDiff.size()))

        val ssz1 = state1.sszSerialize()
        val ssz2 = state2.sszSerialize()
        val ssz3 = diff2.apply(ssz1.asSparseBytes().toDiffResult()).getBytes().toDenseBytes()

        val ssz2Bytes = ssz2.toArrayUnsafe()
        val ssz3Bytes = ssz3.toArrayUnsafe()

        assertThat(ssz3Bytes.size).isEqualTo(ssz2Bytes.size)

        for (off in ssz3Bytes.indices) {
            if (ssz2Bytes[off] != ssz3Bytes[off]) {
                throw AssertionError("Arrays are not equal at off $off")
            }
        }

        assertThat(ssz3Bytes.contentEquals(ssz2Bytes)).isTrue()

        val state3 = stateSchema.sszDeserialize(ssz3)

        assertThat(state3).isEqualTo(state2)

        Unit
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun perfTest() = runBlocking {
        val state1 = stateLoader.loadState(startEpoch)
        val state2 = stateLoader.loadState(startEpoch + 1)
        while (true) {
            val t = measureTime {
                overlaySchemaTestImpl(state1, state2)
            }
            println("Time: $t")
        }
    }

    @Test
    fun sizeStats() = runBlocking {
        (0 until 269).forEach { i ->
            val state1 = stateLoader.loadState(startEpoch)
            val state2 = stateLoader.loadState(startEpoch + i + 1)
            overlaySchemaTestImpl(state1, state2)
        }
    }

    @Test
    fun slotStats() = runBlocking {
        (0 until 269).forEach { i ->
            val state = stateLoader.loadState(startEpoch + i)
            val stateSlot = state.slot.asSlot()
            val relEpoch = stateSlot.epoch - startEpoch
            val relSlot = stateSlot - stateSlot.epoch.startSlot
            println("$i: $relEpoch + $relSlot")
        }
    }

    @Test
    fun overlaySchemaTest() = runBlocking {
        val state1 = stateLoader.loadState(startEpoch + 4)
        val state2 = stateLoader.loadState(startEpoch + 5)
//        val state1 = stateLoader.loadState(startEpoch)
//        val state2 = stateLoader.loadState(startEpoch + 1)
        overlaySchemaTestImpl(state1, state2)
    }

    fun hole(vararg a: Any) {}

    @Test
    fun overlaySchemaTestImpl(state1: BeaconState, state2: BeaconState) = runBlocking {
//        val state1 = stateLoader.loadState(startEpoch)
//        val state2 = stateLoader.loadState(startEpoch + 1)
        val stateSchema = state1.schema as BeaconStateSchema<*, *>

        val balancesSelector = GIndexSelector.beaconStateFieldSelector(SpecMilestone.ALTAIR, "balances")

        val compositeSchema = CompositeDiffSchema(
            UInt64DiffSchema().toSparse(balancesSelector),
            SimpleSszDiffSchema().toSparse(balancesSelector.invert())
        )
        val gzippedSchema = compositeSchema.gzipped()
        val diffSchema = gzippedSchema

        val oldIndexedSsz = IndexedSsz.create(stateSchema, state1.backingNode)
        val newIndexedSsz = IndexedSsz.create(stateSchema, state2.backingNode)

        val alignedIndexedSsz = AlignedIndexedSsz.create(oldIndexedSsz, newIndexedSsz)
        val diff1 = diffSchema.diff(alignedIndexedSsz)

        val serializedDiff = Bytes.wrap(diff1.serialize().toArrayUnsafe())
        val diff2 = diffSchema.deserializeDiff(serializedDiff)
        println("Serialized diff size: " + ReadableSize.create(serializedDiff.size()))

        val ssz1 = state1.sszSerialize()
        val ssz2 = state2.sszSerialize()
        val ssz3 = diff2.apply(ssz1.asSparseBytes().toDiffResult()).getBytes().toDenseBytes()

        val ssz2Bytes = ssz2.toArrayUnsafe()
        val ssz3Bytes = ssz3.toArrayUnsafe()

        hole(ssz1, diff1, oldIndexedSsz, newIndexedSsz, alignedIndexedSsz)
//        println("${ssz1.size()}, ${diff2.hashCode()}")

//        assertThat(ssz3Bytes.size).isEqualTo(ssz2Bytes.size)

        for (off in ssz3Bytes.indices) {
            if (ssz2Bytes[off] != ssz3Bytes[off]) {
                val gIndex = newIndexedSsz.findBySszOffset(off).gIndex
                val sszPath = SszPath.create(stateSchema, gIndex)
                val oldSszOffset = oldIndexedSsz.findByGIndex(gIndex)
                throw AssertionError("Arrays are not equal at off new: $off, old: $oldSszOffset , path: $sszPath")
            }
        }

//        assertThat(ssz3Bytes.contentEquals(ssz2Bytes)).isTrue()
//
//        val state3 = stateSchema.sszDeserialize(ssz3)
//
//        assertThat(state3).isEqualTo(state2)
//
//        Unit
    }

    @Test
    fun `check no dublicate indexes`() {
        runBlocking {
            val state1 = stateLoader.loadState(startEpoch)
            val indexedSsz = IndexedSsz.create(state1)
            val gIndexes = indexedSsz.slices.map { it.gIndex }

            assertThat(gIndexes.toSet().size).isEqualTo(gIndexes.size)
        }
    }
}