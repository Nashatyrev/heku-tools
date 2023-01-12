package tech.pegasys.heku.fixtures.net

import io.libp2p.core.PeerId
import org.apache.tuweni.bytes.Bytes

class PeerUtil {
    companion object {

        // test PeerId
        fun peerId(n: Int): PeerId {
            val bb4 = Bytes.ofUnsignedInt(n.toLong())
            return PeerId(ByteArray(32) { bb4.get(it % 4) })
        }
    }
}