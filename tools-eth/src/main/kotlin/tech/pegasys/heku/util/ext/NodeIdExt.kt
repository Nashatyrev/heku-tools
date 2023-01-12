package tech.pegasys.heku.util.ext

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.PrivKey
import io.libp2p.core.crypto.PubKey
import io.libp2p.core.multiformats.Multiaddr
import io.libp2p.crypto.keys.Secp256k1PrivateKey
import io.libp2p.crypto.keys.Secp256k1PublicKey
import io.libp2p.crypto.keys.unmarshalSecp256k1PublicKey
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.crypto.SECP256K1
import org.apache.tuweni.units.bigints.UInt64
import org.ethereum.beacon.discovery.schema.EnrField
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.util.Functions
import tech.pegasys.heku.util.net.discovery.discv5.system.DiscNodeId
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNodeId
import tech.pegasys.teku.networking.p2p.libp2p.MultiaddrPeerAddress
import tech.pegasys.teku.networking.p2p.peer.NodeId
import tech.pegasys.teku.networking.p2p.peer.Peer

fun Bytes.toDiscNodeId() = DiscNodeId(this)

fun PubKey.toLibP2PPeerId() = PeerId.fromPubKey(this)
fun PubKey.toNodeId(): DiscNodeId {
    require(this is Secp256k1PublicKey)
    return NodeRecord.fromValues(
        IdentitySchemaInterpreter.V4,
        UInt64.ZERO,
        listOf(EnrField(EnrField.PKEY_SECP256K1, this.raw().toBytes()))
    ).nodeId.toDiscNodeId()
}

fun PrivKey.toDiscV5SecretKey(): SECP256K1.SecretKey {
    require(this is Secp256k1PrivateKey)
    val rawBytes = this.raw()
    require(rawBytes.size <= 33)
    val noSignRawBytes =
        if (rawBytes.size == 33)
            rawBytes.slice(1..32).toByteArray()
        else rawBytes
    return Functions.createSecretKey(Bytes32.leftPad(Bytes.wrap(noSignRawBytes)))
}

fun NodeRecord.getLibP2PPublicKey() =
    unmarshalSecp256k1PublicKey((this.get(EnrField.PKEY_SECP256K1) as Bytes).toArrayUnsafe())

fun NodeId.toLibP2PPeerId(): PeerId = PeerId(this.toBytes().toArrayUnsafe())

fun Peer.desc() = this.id.toLibP2PPeerId()

fun Peer.getMultiAddr(): Multiaddr {
    val address = ((this.address as? MultiaddrPeerAddress)
        ?: throw IllegalArgumentException("Peer.address is not MultiaddrPeerAddress: ${this.address.javaClass}")).multiaddr
    val peerId = this.id.toLibP2PPeerId()
    return address.withP2P(peerId)
}

fun PeerId.toTekuNodeId() = LibP2PNodeId(this)