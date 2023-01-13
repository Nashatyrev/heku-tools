package tech.pegasys.heku.util.beacon.bls

import tech.pegasys.teku.bls.BLSConstants
import tech.pegasys.teku.spec.logic.common.block.AbstractBlockProcessor

class BlsUtils {

    companion object {

        fun globallyDisableBls(leaveDepositVerificationEnabled: Boolean = true) {
            BLSConstants.disableBLSVerification()
            if (leaveDepositVerificationEnabled) {
                AbstractBlockProcessor.depositSignatureVerifier = EnabledBlsSignatureVerifier()
            }
        }
    }
}