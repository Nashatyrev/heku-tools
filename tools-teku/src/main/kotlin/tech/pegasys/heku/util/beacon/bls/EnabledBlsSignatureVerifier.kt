package tech.pegasys.heku.util.beacon.bls

import com.google.common.base.Preconditions
import org.apache.tuweni.bytes.Bytes
import tech.pegasys.teku.bls.BLSPublicKey
import tech.pegasys.teku.bls.BLSSignature
import tech.pegasys.teku.bls.BLSSignatureVerifier
import tech.pegasys.teku.bls.doActuallyVerify

class EnabledBlsSignatureVerifier : BLSSignatureVerifier {

    override fun verify(publicKeys: List<BLSPublicKey>, message: Bytes, signature: BLSSignature): Boolean =
        doActuallyVerify(publicKeys, message, signature)

    override fun verify(
        publicKeys: List<List<BLSPublicKey>>,
        messages: List<Bytes>,
        signatures: List<BLSSignature>
    ): Boolean {
        Preconditions.checkArgument(
            publicKeys.size == messages.size && publicKeys.size == signatures.size,
            "Different collection sizes"
        )
        return signatures.indices
            .map { idx ->
                verify(publicKeys[idx], messages[idx], signatures[idx])
            }
            .all { it }
    }
}