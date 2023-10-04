package tech.consensys.linea

import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse

fun ExecutionLayerManager.withSkippingSlots(skipSlotPredicate: (Long) -> Boolean) =
    SlotSkippingExecutionLayerManager(this, skipSlotPredicate)

class SlotSkippingExecutionLayerManager(
    val delegate: ExecutionLayerManager,
    val skipSlotPredicate: (Long) -> Boolean
) : ExecutionLayerManager by delegate {

    override fun engineGetPayload(
        executionPayloadContext: ExecutionPayloadContext,
        slot: UInt64
    ): SafeFuture<GetPayloadResponse> {
        return if (skipSlotPredicate(slot.longValue())) {
            SafeFuture.failedFuture(WannaSkipABlockException())
        } else {
            delegate.engineGetPayload(executionPayloadContext, slot)
        }
    }
}

class WannaSkipABlockException : RuntimeException("EL wants to skip a block")

