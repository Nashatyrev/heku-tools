package tech.pegasys.teku.bls

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.teku.bls.impl.BlsException
import tech.pegasys.teku.bls.impl.PublicKey


fun doActuallyVerify(publicKeys: List<BLSPublicKey>, message: Bytes, signature: BLSSignature): Boolean {
    return try {
        if (publicKeys.isEmpty()) {
            return false
        }
        val publicKeyObjects: List<PublicKey> = publicKeys
            .map { it.publicKey }
        try {
            signature.signature.verify(publicKeyObjects, message)
        } catch (e: BlsException) {
            false
        }
    } catch (e: IllegalArgumentException) {
        throw BlsException("Failed to fastAggregateVerify", e)
    }
}
