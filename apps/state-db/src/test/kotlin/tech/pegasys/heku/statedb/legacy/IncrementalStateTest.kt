package tech.pegasys.heku.statedb.legacy

import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.statedb.runner.FileEpochStateLoader
import tech.pegasys.heku.util.ext.writeBytesGzipped
import tech.pegasys.heku.util.ext.writeBytesT
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.type.Epoch
import tech.pegasys.heku.util.type.Slot
import tech.pegasys.teku.infrastructure.ssz.SszData
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64
import tech.pegasys.teku.infrastructure.ssz.tree.*
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.networks.Eth2Network
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

fun main() {
    runBlocking {
        IncrementalStateTest().test1()
    }
}

class IncrementalStateTest(
    val eth2Network: Eth2Network = Eth2Network.MAINNET,
) {

//    val stateLoader = TekuDbStateLoader(eth2Network, "./work.dir/incremental.state.$eth2Network")
    val stateLoader = FileEpochStateLoader(eth2Network, File("./work.dir/state.dumper.$eth2Network/states"))
//    val stateLoader = NodeApiStateLoader(eth2Network.spec().spec, URI("http://localhost:15051"))

    val treeDiff = TreeDiff()

    val zeroHashes = TreeUtil.ZERO_TREES_BY_ROOT.keys

    fun TreeNode.iterateLeafs(visitor: (LeafDataNode) -> Unit) {
        when {
            this is LeafDataNode -> visitor(this)
            this.hashTreeRoot() in zeroHashes -> {}
            this is BranchNode -> {
                this.left().iterateLeafs(visitor)
                this.right().iterateLeafs(visitor)
            }
        }
    }

    fun TreeNode.getAllLeafs() = mutableListOf<LeafDataNode>()
        .also { list ->
            iterateLeafs { n: LeafDataNode ->
                list += n
            }
        }

    fun Collection<LeafDataNode>.dataSize() = this.sumOf { it.data.size() }

    fun getStateFieldTrees(state: BeaconState): Map<String, TreeNode> {
        val schema = state.schema
        return (0 until schema.fieldsCount)
            .associate { fieldIndex ->
                val fieldName = schema.fieldNames[fieldIndex]
                val tree = state.getAny<SszData>(fieldIndex).backingNode
                fieldName to tree
            }
    }

//    suspend fun test2() {
//        val startEpoch = 130143.epochs
//
//        listOf(1,2,3,4,16,32,64, 128, 256).forEach {
//            println("============== $it epoch(s) =============")
//            testDiff(startEpoch, startEpoch + it)
//        }
//    }
//
//    suspend fun testDiff(epoch1: Epoch, epoch2: Epoch) {
//        val state1 = stateLoader.loadState(epoch1)
//        val state2 = stateLoader.loadState(epoch2)
//
//        val sszDiff = IndexedSszDiff
//            .create(state1, state2)
//            .deduplicateLargeSlices()
//            .compactUInt64Diffs {
//                SszPath.create(state1.schema, it.gIndex).elements[0].toStringShort().contains("/balances")
//            }
//
//        fun compactedSize(diff: List<IndexedSszDiff.DiffSlice>) =
//            diff.sumOf {
//                it.compactNewSlice.sumOf {
//                    it.sszBytes.size()
//                }
//            }
//
//        fun zippedSize(diff: List<IndexedSszDiff.DiffSlice>) =
//            diff.flatMap {
//                it.compactNewSlice.map { it.sszBytes }
//            }
//                .concat()
//                .gzipCompress()
//                .size()
//
//        fun diffStat(diff: List<IndexedSszDiff.DiffSlice>): String {
//            return "Size: " + compactedSize(diff) +
//                    ", compressed: " + zippedSize(diff) +
//                    " (new: " + compactedSize(diff.filter { it.oldSlice == null }) + ")"
//        }
//
//        println("Total diff: ${diffStat(sszDiff.diffSlices)}")
//
////        sszDiff.diffSlices.forEach {
////            println("" + SszPath.create(state1.schema, it.gIndex) +
////                    "\n    " + (it.oldSlice?.sszBytes ?: "") +
////                    "\n    " + it.newSlice.sszBytes)
////            if (it.compactNewSlice[0].sszBytes != it.newSlice.sszBytes) {
////                println("    Compacted (off: ${it.newSlice.sszOffset}):")
////                it.compactNewSlice.forEach {
////                    println("        $it")
////                }
////            }
////        }
//
//        println("-----------------------------------------")
//
//        sszDiff.diffSlices
//            .groupBy { SszPath.create(state1.schema, it.gIndex).elements[0] }
//            .forEach { (field, diff) ->
//                println("${diffStat(diff)}\t${field.toStringShort()}")
//            }
//
//
////        println("-----------------------------------------")
////        val indexedSsz = IndexedSsz.create(state1)
////        val stateFieldSizes = indexedSsz.slices
////            .groupingBy {
////                val sszPath = SszPath.create(state1.schema, it.gIndex)
////                sszPath.elements[0]
////            }
////            .fold(0) { size, el -> size + el.sszBytes.size() }
////        stateFieldSizes.forEach { field, size ->
////            println("$size\t$field")
////        }
//    }

    suspend fun test1() {
//        val slot1 = Slot(4163744) // first epoch slot

        val epoch1 = Epoch(130143)

        log("Starting")
        val state1 = stateLoader.loadState(epoch1)
        val state2 = stateLoader.loadState(epoch1.inc())
        val state3 = stateLoader.loadState(epoch1 + 16)

        val state1Altair = state1.toVersionAltair().orElseThrow()


        println("State field sizes:")
        getStateFieldTrees(state1).forEach { fName, fTree ->
            println("    $fName\t${fTree.getAllLeafs().dataSize()}")
        }

        File("state.ssz").writeBytesT(state1.sszSerialize())
        File("state.ssz.gz").writeBytesGzipped(state1.sszSerialize())
        File("participations.ssz").writeBytesT(state1Altair.previousEpochParticipation.sszSerialize())
        File("participations.ssz.gz").writeBytesGzipped(state1Altair.previousEpochParticipation.sszSerialize())
        File("balances.ssz").writeBytesT(state1Altair.balances.sszSerialize())
        File("balances.ssz.gz").writeBytesGzipped(state1Altair.balances.sszSerialize())

        val balancesDiff = state1.balances.zip(state3.balances)
            .map { (b1, b2) -> b2.longValue() - b1.longValue() }
            .map { SszUInt64.of(UInt64.fromLongBits(it)) }
        val sszBalancesDiff = state1.balances.schema.createFromElements(balancesDiff)
        File("balance-diff.ssz").writeBytesT(sszBalancesDiff.sszSerialize())
        File("balance-diff.ssz.gz").writeBytesGzipped(sszBalancesDiff.sszSerialize())

//        val participationsDiff = state1Altair.currentEpochParticipation.zip(state1_1Altair.currentEpochParticipation)
//            .map { (b1, b2) -> b2.get() - b1.get() }
//            .map { SszByte.of(it) }
//        val sszParticipationsDiff = state1Altair.currentEpochParticipation.schema.createFromElements(participationsDiff)
//        File("participations-diff.ssz").writeBytesT(sszParticipationsDiff.sszSerialize())
//        File("participations-diff.ssz.gz").writeBytesGzipped(sszParticipationsDiff.sszSerialize())

        val balanceChanges = mutableMapOf<Long, AtomicInteger>()
        val effectiveBalances = mutableMapOf<Long, AtomicInteger>()
        for (i in state1.balances.asList().indices) {
            val balanceDiff = state3.balances[i].longValue() - state1.balances[i].longValue()
            balanceChanges.computeIfAbsent(balanceDiff) { AtomicInteger() }.incrementAndGet()
            effectiveBalances.computeIfAbsent(state1.validators[i].effectiveBalance.longValue())
            { AtomicInteger() }.incrementAndGet()
        }

        println(balanceChanges.size)
        println(effectiveBalances.size)
//        state1.validators.take(1024).forEach {
//            println(it.effectiveBalance)
//        }

        for (slot2i in 4163745L..(4163744 + 64)) {
            val slot2 = Slot(slot2i)
//            println("Loading state $slot2...")
            val state2 = stateLoader.loadState(slot2)
            val leafs2 = state2.backingNode.getAllLeafs()

//            log("Calculating diff")
            val diff = treeDiff.calcLeafDiff(state1.backingNode, state2.backingNode)

            println(
                "At $slot2 Count: total: ${leafs2.size}" +
                        ", super: " + leafs2.count { it is SszSuperNode } +
                        ", total removed/added: " + diff.removed.size + "/" + diff.added.size +
                        ", super removed/added: " + diff.removed.count { it is SszSuperNode } + "/" + diff.added.count { it is SszSuperNode }
            )
            println(
                "           Size: total: ${leafs2.dataSize()}" +
                        ", super: " + leafs2.filter { it is SszSuperNode }.dataSize() +
                        ", total removed/added: " + diff.removed.dataSize() + "/" + diff.added.dataSize() +
                        ", super removed/added: " + diff.removed.filter { it is SszSuperNode }.dataSize() +
                        "/" + diff.added.filter { it is SszSuperNode }.dataSize()
            )
        }
    }
}