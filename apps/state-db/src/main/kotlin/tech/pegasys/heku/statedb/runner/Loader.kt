package tech.pegasys.heku.statedb.runner

import kotlinx.coroutines.runBlocking
import tech.pegasys.heku.statedb.db.IncSszStateLoader
import tech.pegasys.heku.util.ext.writeBytesT
import tech.pegasys.heku.util.log
import tech.pegasys.heku.util.type.epochs
import java.io.File
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

fun main1() {
    val stateLoader = IncSszStateLoader(dbPath = "./work.dir/incssz.dump.MAINNET/inc.state.db")
    val epoch = 131073.epochs
    runBlocking {
        log("Loading state...")

        val state = stateLoader.loadState(epoch.startSlot)

        log("Loaded state @ $epoch")

        File("./work.dir/$epoch.epoch.state.ssz").writeBytesT(state.sszSerialize())

        log("State saved")
    }
}

@OptIn(ExperimentalTime::class)
fun main() {
    val stateLoader = IncSszStateLoader()

    println("States stored: ${stateLoader.firstEpoch}..${stateLoader.lastEpoch}")

//    runBlocking {
//        for (epoch in stateLoader.firstEpoch..stateLoader.lastEpoch) {
//        for (epoch in 131072.epochs..stateLoader.lastEpoch) {
//            val t = measureTime {
//                stateLoader.loadState(epoch.startSlot)
//            }
//
//            log("Loaded state @ $epoch in ${t.inWholeMilliseconds}")
//        }
//    }

    runBlocking {
        while (true) {
            val t = measureTimedValue {
//                stateLoader.loadState(74241.epochs.startSlot)
//                stateLoader.loadState(131120.epochs.startSlot)
//                stateLoader.loadState(131122.epochs.startSlot)
                stateLoader.loadState((74241.epochs..131122.epochs).random().startSlot)
            }

            log("Loaded state ${t.value.slot} in ${t.duration.inWholeMilliseconds}")
        }
    }
}