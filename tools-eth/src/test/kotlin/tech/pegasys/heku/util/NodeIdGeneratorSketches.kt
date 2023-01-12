package tech.pegasys.heku.util

import io.libp2p.core.PeerId
import io.libp2p.core.crypto.marshalPublicKey
import io.libp2p.core.crypto.unmarshalPrivateKey
import io.libp2p.crypto.keys.Secp256k1PrivateKey
import io.libp2p.crypto.keys.unmarshalSecp256k1PublicKey
import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.units.bigints.UInt64
import org.ethereum.beacon.discovery.schema.EnrField
import org.ethereum.beacon.discovery.schema.IdentitySchemaInterpreter
import org.ethereum.beacon.discovery.schema.NodeRecord
import org.ethereum.beacon.discovery.schema.NodeRecordBuilder
import tech.pegasys.heku.util.ext.toBytes
import tech.pegasys.heku.util.ext.toDiscV5SecretKey

fun main() {
    val sk = ClassLoader.getSystemResourceAsStream("nearZeroNodeIds.txt")!!.bufferedReader()
        .use { reader ->
            reader
                .lineSequence()
                .map { Bytes.fromHexString(it).toArrayUnsafe() }
                .map { unmarshalPrivateKey(it) }
                .map { it as Secp256k1PrivateKey }
                .first()
        }
    val pk = sk.publicKey()
    val pkBytes = marshalPublicKey(pk).toBytes()
    val pkRawBytes = pk.raw().toBytes()
    val peerId = PeerId.fromPubKey(pk)
    val idBytes = peerId.bytes.toBytes()


    val nodeRecord = NodeRecord.fromValues(
        IdentitySchemaInterpreter.V4,
        UInt64.ZERO,
        listOf(EnrField(EnrField.PKEY_SECP256K1, pkRawBytes))
    )
    val nodeRecord1 = NodeRecordBuilder().secretKey(sk.toDiscV5SecretKey()).build()
    val discPeerId = nodeRecord.nodeId
    val discPeerId1 = nodeRecord1.nodeId

    val ethPk =
        unmarshalSecp256k1PublicKey((nodeRecord.get(EnrField.PKEY_SECP256K1) as Bytes).toArrayUnsafe())
    val peerId1 = PeerId.fromPubKey(ethPk)
}