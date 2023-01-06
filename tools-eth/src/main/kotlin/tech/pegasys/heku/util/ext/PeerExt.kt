package tech.pegasys.heku.util.ext

import io.libp2p.core.PeerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PPeer
import tech.pegasys.teku.networking.p2p.libp2p.PeerClientType
import tech.pegasys.teku.networking.p2p.peer.Peer
import java.util.*
import kotlin.time.Duration.Companion.milliseconds

private val asyncScope = CoroutineScope(Dispatchers.Default)

fun Peer.getClientTypeFuture() = asyncScope.async {
    for (i in 1..100) {
        if (this@getClientTypeFuture.peerClientType != PeerClientType.UNKNOWN) {
            return@async this@getClientTypeFuture.peerClientType
        }
        delay(100.milliseconds)
    }
    return@async PeerClientType.UNKNOWN
}

fun Peer.getAgentStringFuture(): SafeFuture<Optional<String>> {
    return if (this is LibP2PPeer) {
        this.callPrivateMethod("getAgentVersionFromIdentity") as SafeFuture<Optional<String>>
    } else {
        SafeFuture.completedFuture(Optional.empty())
    }
}

fun PeerId.toStringMedium() = toBase58().let { it.take(14) + "..." + it.takeLast(6) }
fun PeerId.toStringShort() = toBase58().takeLast(8)