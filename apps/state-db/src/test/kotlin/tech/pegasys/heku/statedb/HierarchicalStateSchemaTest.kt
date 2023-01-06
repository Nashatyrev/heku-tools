package tech.pegasys.heku.statedb

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tech.pegasys.heku.statedb.db.DiffStoreFactory
import tech.pegasys.heku.statedb.db.MemDiffStore
import tech.pegasys.heku.statedb.db.StateAppender
import tech.pegasys.heku.statedb.diff.*
import tech.pegasys.heku.statedb.runner.FileEpochStateLoader
import tech.pegasys.heku.statedb.schema.*
import tech.pegasys.heku.statedb.ssz.*
import tech.pegasys.heku.util.type.epochs
import tech.pegasys.teku.spec.SpecMilestone
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

class HierarchicalStateSchemaTest {

    val stateLoader = FileEpochStateLoader(Eth2Network.MAINNET, File("./work.dir/state.dumper.MAINNET/states"))
    val startEpoch = 130143.epochs
    val endEpoch = startEpoch + 270

    val sszSource = IndexedSszSource { stateId ->
        val slot = stateId.slot
        require(slot.epoch.startSlot == slot) { "Non epoch slots not supported: $slot" }
        val state = stateLoader.loadState(slot.epoch)
        IndexedSsz.create(state)
    }.cached(32)

    class DiffStoreFactory {
        val stores = mutableListOf<MemDiffStore>()
        fun create() = MemDiffStore().also { stores += it }
    }

    val diffStore = DiffStoreFactory()

    val schemaBuilder: (IndexedSszSource) -> AbstractSchema = { sszSource ->
        SchemasBuilder.build {
            indexedSszSource = sszSource
            diffStoreFactory = DiffStoreFactory { diffStore.create() }
            minimalSlot = startEpoch.startSlot

            val balancesFieldSelector = GIndexSelector.beaconStateFieldSelector(SpecMilestone.ALTAIR, "balances")
            val nonBalancesFieldSelector = balancesFieldSelector.invert()

            val balancesCompositeSchema =
                CompositeDiffSchema(
                    SimpleSszDiffSchema()
                        .toSparse(nonBalancesFieldSelector)
                        .gzipped(),
                    UInt64DiffSchema()
                        .toSparse(balancesFieldSelector)
                        .gzipped()
                )

            val rootSchema = newHierarchicalSchema {
                asRootSchema()
                diffSchema = SnapshotDiffSchema().gzipped()
                stateIdCalculator = StateIdCalculator { StateId(minimalSlot) }
            }

            val x64Schema = newHierarchicalSchema {
                diffSchema = balancesCompositeSchema
                parentSchema = rootSchema
                stateIdCalculator = StateIdCalculator.everyNEpochs(64.epochs)
            }

            val x16Schema = newHierarchicalSchema {
                diffSchema = balancesCompositeSchema
                parentSchema = x64Schema
                stateIdCalculator = StateIdCalculator.everyNEpochs(16.epochs)
            }

            newMergeSchema {
                parentDelegate = x16Schema

                addHierarchicalSchema {
                    diffSchema = SimpleSszDiffSchema()
                        .toSparse(nonBalancesFieldSelector)
                        .gzipped()
                    parentSchema = x16Schema
                    stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
                }

                addHierarchicalSchema {
                    diffSchema = UInt64DiffSchema()
                        .toSparse(balancesFieldSelector)
                        .gzipped()
                    parentSchema = x16Schema
                    sameSchemaUntilParent { StateId(it.slot - 1.epochs) }
                    stateIdCalculator = StateIdCalculator.everyNEpochs(1.epochs)
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun stateAppenderTest() {
        val stateAppender = StateAppender(Eth2Network.MAINNET)
        val indexedSszSource = stateAppender
            .selfFeedingIndexedSszSource
            .cached(128)
        val schema = schemaBuilder(indexedSszSource)
        stateAppender.initStorageSchema(schema)

        runBlocking {
            var prevSize = mapOf<Int, Int>()
            (0 until 269).forEach {
                val state = stateLoader.loadState(startEpoch + it)
                val stateId = StateId((startEpoch + it).startSlot)

                val t = measureTime {
                    stateAppender.append(stateId, IndexedSsz.Companion.create(state))
                }

                val curSize = diffStore.stores
                    .withIndex()
                    .map { (index, diffStore) ->
                        index to diffStore.map.values.sumOf { it.size() }
                    }.toMap()



                println("Saved $it in $t:")
                diffStore.stores.forEachIndexed { index, diffStore ->
                    val sizeDiff = curSize[index]!! - (prevSize[index] ?: 0)
                    val sizeDiffStr = if (sizeDiff == 0) "" else "+" + sizeDiff
                    println("  $index: ${diffStore.map.size}, " + diffStore.map.values.sumOf { it.size() } + "\t$sizeDiffStr")
                }
                println()

                prevSize = curSize

//                val slotSszBytes = schema.load(stateId)
//                assertThat(slotSszBytes.isDense()).isTrue()
//                assertThat(slotSszBytes.toDenseBytes()).isEqualTo(sszSource.loadSsz(stateId).allBytes())
            }

            println("Verifying")

            (0 until 269).forEach {
                val t = measureTime {
                    val stateId = StateId((startEpoch + it).startSlot)
                    val stateBytes = schema.load(stateId).getBytes().toDenseBytes()
                }
                println("Loaded $it within $t")
            }
        }
    }

    @Test
    fun saveLoadTest() {
        val schema = schemaBuilder(sszSource)

        runBlocking {
            var prevSize = mapOf<Int, Int>()
            (0 until 269).forEach {
                val stateId = StateId((startEpoch + it).startSlot)
                schema.save(stateId)

                val curSize = diffStore.stores
                    .withIndex()
                    .map { (index, diffStore) ->
                        index to diffStore.map.values.sumOf { it.size() }
                    }.toMap()



                println("Saved $it:")
                diffStore.stores.forEachIndexed { index, diffStore ->
                    val sizeDiff = curSize[index]!! - (prevSize[index] ?: 0)
                    val sizeDiffStr = if (sizeDiff == 0) "" else "+" + sizeDiff
                    println("  $index: ${diffStore.map.size}, " + diffStore.map.values.sumOf { it.size() } + "\t$sizeDiffStr")
                }
                println()

                prevSize = curSize

                val slotSszBytes = schema.load(stateId).getBytes()
                assertThat(slotSszBytes.isDense()).isTrue()
                assertThat(slotSszBytes.toDenseBytes() == sszSource.loadSsz(stateId).allBytes()).isTrue()
            }

        }
    }
}