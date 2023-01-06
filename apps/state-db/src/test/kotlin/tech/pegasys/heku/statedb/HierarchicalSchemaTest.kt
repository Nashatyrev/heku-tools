package tech.pegasys.heku.statedb

import kotlinx.coroutines.runBlocking
import org.apache.tuweni.bytes.Bytes.fromHexString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import tech.pegasys.heku.statedb.ssz.IndexedSsz.IndexedSlice
import tech.pegasys.heku.statedb.db.DiffStore
import tech.pegasys.heku.statedb.db.DiffStoreFactory
import tech.pegasys.heku.statedb.db.MemDiffStore
import tech.pegasys.heku.statedb.diff.*
import tech.pegasys.heku.statedb.schema.*
import tech.pegasys.heku.statedb.ssz.AlignedIndexedSsz
import tech.pegasys.heku.statedb.ssz.IndexedSsz
import tech.pegasys.heku.util.collections.asSparseBytes
import tech.pegasys.heku.util.type.slots

class HierarchicalSchemaTest {

    val sszData = mapOf(
        StateId(100.slots) to mapOf(
            10 to "0x11111111",
            11 to "0xffffffff",
            20 to "0x22222222",
            30 to "0x3333333333333333",
            40 to "0x44444444",
        ),

        StateId(120.slots) to mapOf(
            10 to "0x11111111",
            20 to "0x22222222aaaa",
            30 to "0x3433333333333333",
            31 to "0x5555555555555555",
            41 to "0xbbbbbbbb",
            40 to "0x44444444",
        ),

        StateId(121.slots) to mapOf(
            10 to "0x11111111",
            20 to "0x22222222aaaa",
            30 to "0x3733333333333333",
            31 to "0x6555555555555555",
            41 to "0xbbbbbbbb",
            40 to "0x44444444",
        ),

        StateId(122.slots) to mapOf(
            10 to "0x11111111",
            20 to "0x22222222aaaa",
            30 to "0x3833333333333333",
            31 to "0x6055555555555555",
            41 to "0xbbbbbbbb",
            40 to "0x44444444",
        )
    ).mapValues { (_, value) -> createIndexedSsz(value) }

    class DiffStoreFactory {
        val stores = mutableListOf<DiffStore>()
        fun create() = MemDiffStore().also { stores += it }
    }

    val diffStore = DiffStoreFactory()
    val sszSource = IndexedSszSource { sszData[it] ?: throw IllegalArgumentException("Ssz not found for $it") }

    val schema = SchemasBuilder.build {
        indexedSszSource = sszSource
        diffStoreFactory = DiffStoreFactory { diffStore.create() }

        val uintSchemaSelector = GIndexSelector { it / 10 == 3L }
        val otherSchemaSelector = GIndexSelector.ALL subtract uintSchemaSelector

        val x100Schema = newHierarchicalSchema {
            name = "x100"
            asRootSchema()
            diffSchema = SnapshotDiffSchema()
            stateIdCalculator = StateIdCalculator.everyNSlots(100.slots)
        }

        val x10Schema = newHierarchicalSchema {
            name = "x10"
            diffSchema =
                CompositeDiffSchema(
                    UInt64DiffSchema().toSparse(uintSchemaSelector),
                    SimpleSszDiffSchema().toSparse(otherSchemaSelector)
                )
            parentSchema = x100Schema
            stateIdCalculator = StateIdCalculator.everyNSlots(10.slots)
        }

        newMergeSchema {
            parentDelegate = x10Schema

            addHierarchicalSchema {
                name = "x1Rest"
                diffSchema = SimpleSszDiffSchema().toSparse(otherSchemaSelector)
                parentSchema = x10Schema
                stateIdCalculator = StateIdCalculator.everyNSlots(1.slots)
            }

            addHierarchicalSchema {
                name = "x1UInt"
                diffSchema = UInt64DiffSchema().toSparse(uintSchemaSelector)
                parentSchema = x10Schema
                sameSchemaUntilParent { StateId(it.slot - 1) }
                stateIdCalculator = StateIdCalculator.everyNSlots(1.slots)
            }
        }
    }

//    val x100Schema = object : HierarchicalSchema(
//        SnapshotDiffSchema()/*.gzipped()*/,
//        diffStore.create(),
//        sszSource
//    ) {
//        override fun getParent(stateId: StateId): DagSchemaVertex? = null
//    }
//
//    val uintSchemaSelector = GIndexSelector { it / 10 == 3L }
//    val otherSchemaSelector = GIndexSelector.ALL subtract uintSchemaSelector
//
//    val x10Schema = object : HierarchicalSchema(
//        CompositeDiffSchema(
//            UInt64DiffDiffSchema().toSparse(uintSchemaSelector),
//            SimpleSszDiffDiffSchema().toSparse(otherSchemaSelector)
//        ),
//        diffStore.create(),
//        sszSource
//    ) {
//        override fun getParent(stateId: StateId) = DagSchemaVertex(StateId(stateId.slot floorTo 100.slots), x100Schema)
//    }
//
//    val x1RestSchema = object : HierarchicalSchema(
//        SimpleSszDiffDiffSchema().toSparse(otherSchemaSelector),
//        diffStore.create(),
//        sszSource
//    ) {
//        override fun getParent(stateId: StateId) = DagSchemaVertex(StateId(stateId.slot floorTo 10.slots), x10Schema)
//    }
//
//    val x1UIntSchema = object : HierarchicalSchema(
//        UInt64DiffDiffSchema().toSparse(uintSchemaSelector),
//        diffStore.create(),
//        sszSource
//    ) {
//        private fun isParentSchemaSlot(slot: Slot) = slot floorTo 10.slots == slot
//
//        override fun getParent(stateId: StateId): DagSchemaVertex {
//            val parentStateId = StateId(stateId.slot - 1)
//            return DagSchemaVertex(
//                parentStateId,
//                if (isParentSchemaSlot(parentStateId.slot)) {
//                    x10Schema
//                } else {
//                    this
//                }
//            )
//        }
//    }
//
//    val x1Schema = object : MergeSchema(listOf(
//        x1RestSchema,
//        x1UIntSchema
//    )) {
//        override fun getParents(stateId: StateId): List<DagSchemaVertex> {
//            val maybeParent = DagSchemaVertex(StateId(stateId.slot floorTo 10.slots), x10Schema)
//            return if (maybeParent.stateId == stateId) {
//                listOf(maybeParent)
//            } else {
//                super.getParents(stateId)
//            }
//        }
//    }

    @Disabled
    @Test
    fun sanityTest() {
        val oldSsz = sszData[StateId(100.slots)]!!
        val newSsz = sszData[StateId(120.slots)]!!
        val ssz = AlignedIndexedSsz.create(oldSsz, newSsz)

        val schema = SparseDiffSchema(SimpleSszDiffSchema()) { it / 10 != 3L}
        val uintSchema = SparseDiffSchema(UInt64DiffSchema()) { it / 10 == 3L}



        val diff = schema.diff(ssz)
        val diffSerialized = diff.serialize()
        val diff2 = schema.deserializeDiff(diffSerialized)
        val newSsz2 = diff2.apply(oldSsz.allBytes().asSparseBytes().toDiffResult())

        assertThat(newSsz2.getBytes().toDenseBytes()).isEqualTo(newSsz.allBytes())
    }

    @Test
    fun saveLoadTest() {
        runBlocking {
            val stateId = StateId(122.slots)
            schema.save(stateId)
            val slotSszBytes = schema.load(stateId).getBytes()

            assertThat(slotSszBytes.isDense()).isTrue()
            assertThat(slotSszBytes.toDenseBytes()).isEqualTo(sszSource.loadSsz(stateId).allBytes())
        }
    }

    companion object {
        fun createIndexedSsz(data: Map<Int, String>): IndexedSsz {

            val dataBytes = data.mapValues { fromHexString(it.value) }
            var off = 0
            val offsets = dataBytes
                .map { it.value.size() }
                .map {
                    val curOff = off
                    off += it
                    curOff
                }
            return dataBytes.entries.zip(offsets) { e, off ->
                IndexedSlice(e.key.toLong(), off, e.value)
            }.let { IndexedSsz(it) }
        }
    }
}