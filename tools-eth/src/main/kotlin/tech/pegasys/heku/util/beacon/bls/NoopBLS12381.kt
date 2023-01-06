package tech.pegasys.heku.util.beacon.bls

import org.apache.tuweni.bytes.Bytes
import tech.pegasys.teku.bls.BatchSemiAggregate
import tech.pegasys.teku.bls.impl.BLS12381
import tech.pegasys.teku.bls.impl.PublicKey
import tech.pegasys.teku.bls.impl.PublicKeyMessagePair
import tech.pegasys.teku.bls.impl.Signature
import tech.pegasys.teku.bls.impl.blst.BlstBLS12381

class NoopBLS12381(
    private val blst: BLS12381 = BlstBLS12381()
) : BLS12381 by blst {

    class NoopBatchSemiaggregate: BatchSemiAggregate

    override fun prepareBatchVerify(
        index: Int,
        publicKeys: MutableList<out PublicKey>?,
        message: Bytes?,
        signature: Signature?
    ): BatchSemiAggregate = NoopBatchSemiaggregate()

    override fun prepareBatchVerify2(
        index: Int,
        publicKeys1: MutableList<out PublicKey>?,
        message1: Bytes?,
        signature1: Signature?,
        publicKeys2: MutableList<out PublicKey>?,
        message2: Bytes?,
        signature2: Signature?
    ): BatchSemiAggregate = NoopBatchSemiaggregate()

    override fun completeBatchVerify(preparedList: MutableList<out BatchSemiAggregate>?): Boolean  = true

    override fun signatureFromCompressed(compressedSignatureBytes: Bytes): Signature =
        NoopSignature(blst.signatureFromCompressed(compressedSignatureBytes))

    override fun aggregateSignatures(signatures: MutableList<out Signature>): Signature =
        NoopSignature(blst.aggregateSignatures(signatures))
}

class NoopSignature(delegate: Signature) : Signature by delegate {
    override fun verify(publicKey: PublicKey?, message: Bytes?, dst: String?) = true
    override fun verify(keysToMessages: MutableList<PublicKeyMessagePair>?) = true
    override fun verify(publicKeys: MutableList<PublicKey>?, message: Bytes?) = true
    override fun verify(publicKey: PublicKey?, message: Bytes?) = true
}