package tech.pegasys.heku.util.net.discovery.discv5.system

import io.libp2p.core.PeerId
import io.libp2p.crypto.keys.unmarshalSecp256k1PublicKey
import org.apache.tuweni.bytes.Bytes
import org.ethereum.beacon.discovery.schema.EnrField
import org.ethereum.beacon.discovery.schema.NodeRecord
import tech.pegasys.heku.util.beacon.PredefinedNetworks
import tech.pegasys.heku.util.ext.toDiscNodeId
import tech.pegasys.heku.util.ext.toStringMedium
import tech.pegasys.teku.infrastructure.bytes.Bytes4
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetwork.*
import tech.pegasys.teku.spec.networks.Eth2Network
import java.util.*
import kotlin.streams.toList

fun NodeRecord.deriveBuilder() = EnrBuilder(this)

fun NodeRecord.getPeerId(): PeerId {
    val pubKey =
        unmarshalSecp256k1PublicKey((this.get(EnrField.PKEY_SECP256K1) as Bytes).toArrayUnsafe())
    return PeerId.fromPubKey(pubKey)

}

fun NodeRecord.getDiscNodeId() = this.nodeId.toDiscNodeId()

val NodeRecord.isEth2Network get() = containsKey(ETH2_ENR_FIELD)

val NodeRecord.eth2Network: Eth2Network?
    get() {
        val forkIdBytes = get(ETH2_ENR_FIELD) as Bytes?
        return if (forkIdBytes == null || forkIdBytes.size() < 4)
            null
        else
            PredefinedNetworks.FORK_DIGEST_TO_NETWORK[Bytes4(forkIdBytes.slice(0, 4))]
    }

private fun getBitIndices(bb: Bytes): List<Int> =
    BitSet.valueOf(bb.toArrayUnsafe()).stream().toList()


val NodeRecord.syncSubnets: List<Int>
    get() = get(SYNC_COMMITTEE_SUBNET_ENR_FIELD)?.let {
        getBitIndices(it as Bytes)
    } ?: listOf()

val NodeRecord.attestSubnets: List<Int>
    get() = get(ATTESTATION_SUBNET_ENR_FIELD)?.let {
        getBitIndices(it as Bytes)
    } ?: listOf()

fun NodeRecord.toMultiaddr(): String =
    tcpAddress.map { "/ip4/" + it.hostString  + "/tcp/" + it.port}.orElse("") +
    "/p2p/" + getPeerId().toBase58()

val NodeRecord.addressesString: String
    get() =
        if (tcpAddress.isEmpty && udpAddress.isEmpty) "<no address>"
        else if (tcpAddress.isEmpty) "UDP: " + udpAddress.get().toString()
        else if (udpAddress.isEmpty) "TCP: " + tcpAddress.get().toString()
        else if (tcpAddress.get() == udpAddress.get()) tcpAddress.get().toString()
        else if (tcpAddress.get().address == udpAddress.get().address)
            "" + tcpAddress.get().address + ":" + tcpAddress.get().port + "/" + udpAddress.get().port
        else "" + tcpAddress.get() + "/" + udpAddress.get()


val NodeRecord.descr
    get() =
        "NodeRecord[nodeId: ${getDiscNodeId().toShortString()} " +
                "peerID: " + getPeerId().toStringMedium() + " " +
                (eth2Network?.toString() ?: "---").take(3) + ", " +
                "attNets=" + get(ATTESTATION_SUBNET_ENR_FIELD) + ", " +
                "syncNets=" + get(SYNC_COMMITTEE_SUBNET_ENR_FIELD) + ", " +
                addressesString +
                "]"