package tech.pegasys.heku.util.net.discovery.discv5.system

import org.ethereum.beacon.discovery.DiscoverySystem
import tech.pegasys.teku.infrastructure.async.SafeFuture

class DiscoverySystemExt(val system: DiscoverySystem) {
    // TODO more fancy stuff here

    fun stopAsync(): SafeFuture<Unit> {
        val stopPromise = SafeFuture<Unit>()
        // running stop() from netty EventLoop blocks shutdown
        Thread({
            system.stop()
            stopPromise.complete(null)
        }, "Discovery-stop-thread").start()
        return stopPromise
    }
}