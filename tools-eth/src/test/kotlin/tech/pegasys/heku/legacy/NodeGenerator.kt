package tech.pegasys.heku.util

import io.libp2p.core.crypto.KeyType
import io.libp2p.core.crypto.generateKeyPair
import org.apache.tuweni.bytes.Bytes32
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.ext.toDiscNodeId
import tech.pegasys.heku.util.ext.toNodeId
import tech.pegasys.heku.util.net.discovery.discv5.system.DiscNodeId

fun main() {
    craftNearZeroNodes()
}

fun craftNearZeroNodes() {
    craftNearest(Bytes32.ZERO.toDiscNodeId(), 30 * 8)
//    .onEach { (sk, _) -> println("Id: " + sk.publicKey().toNodeId()) }
        .map { (sk, _) -> sk.bytes().toBytes() }
        .take(256)
        .forEach { println(it) }
}

fun craftNearest(peerId: DiscNodeId, maxDistanceBits: Int) =
    generateByNodeId() { it.logDistance(peerId) <= maxDistanceBits }


fun generateByNodeId(filter: (DiscNodeId) -> Boolean) =
    generateKeys().filter { filter(it.second.toNodeId()) }


fun generateKeys() = generateSequence() { generateKeyPair(KeyType.SECP256K1) }
