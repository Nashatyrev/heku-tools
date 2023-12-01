package tech.consensys.linea.execution

import io.libp2p.etc.types.forward
import org.apache.tuweni.bytes.Bytes32
import tech.pegasys.heku.util.ext.schedule
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.infrastructure.ssz.SszList
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer
import tech.pegasys.teku.spec.datastructures.builder.BuilderPayload
import tech.pegasys.teku.spec.datastructures.builder.SignedValidatorRegistration
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadResult
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse
import tech.pegasys.teku.spec.datastructures.execution.HeaderWithFallbackData
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest
import tech.pegasys.teku.spec.datastructures.execution.PowBlock
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes
import tech.pegasys.teku.spec.executionlayer.PayloadStatus
import java.util.Optional
import java.util.concurrent.ScheduledExecutorService
import java.util.function.Function
import kotlin.time.Duration

fun ExecutionLayerManager.withDelay(
    delayNewPayload: Duration,
    delayGetPayload: Duration,
    scheduler: ScheduledExecutorService
) = DelayExecutionLayerManager(this, delayNewPayload, delayGetPayload, scheduler)

class DelayExecutionLayerManager(
    val delegate: ExecutionLayerManager,
    val delayNewPayload: Duration,
    val delayGetPayload: Duration,
    val scheduler: ScheduledExecutorService
) : ExecutionLayerManager {


    override fun onSlot(slot: UInt64) {
        delegate.onSlot(slot)
    }

    override fun eth1GetPowBlock(blockHash: Bytes32): SafeFuture<Optional<PowBlock>> {
        return delegate.eth1GetPowBlock(blockHash)
    }

    override fun eth1GetPowChainHead(): SafeFuture<PowBlock> {
        return delegate.eth1GetPowChainHead()
    }

    override fun engineForkChoiceUpdated(
        forkChoiceState: ForkChoiceState,
        payloadBuildingAttributes: Optional<PayloadBuildingAttributes>
    ): SafeFuture<ForkChoiceUpdatedResult> {
        return delegate.engineForkChoiceUpdated(forkChoiceState, payloadBuildingAttributes)
    }

    override fun engineNewPayload(newPayloadRequest: NewPayloadRequest): SafeFuture<PayloadStatus> {
        val ret = SafeFuture<PayloadStatus>()
        scheduler.schedule(delayNewPayload) {
            delegate.engineNewPayload(newPayloadRequest).forward(ret)
        }
        return ret
    }

    override fun engineGetPayload(
        executionPayloadContext: ExecutionPayloadContext,
        slot: UInt64
    ): SafeFuture<GetPayloadResponse> {
        val ret = SafeFuture<GetPayloadResponse>()
        scheduler.schedule(delayGetPayload) {
            delegate.engineGetPayload(executionPayloadContext, slot).forward(ret)
        }
        return ret
    }

    override fun builderRegisterValidators(
        signedValidatorRegistrations: SszList<SignedValidatorRegistration>,
        slot: UInt64
    ): SafeFuture<Void> {
        return delegate.builderRegisterValidators(signedValidatorRegistrations, slot)
    }

    override fun builderGetPayload(
        signedBlockContainer: SignedBlockContainer,
        getCachedPayloadResultFunction: Function<UInt64, Optional<ExecutionPayloadResult>>
    ): SafeFuture<BuilderPayload> {
        return delegate.builderGetPayload(signedBlockContainer, getCachedPayloadResultFunction)
    }

    override fun builderGetHeader(
        executionPayloadContext: ExecutionPayloadContext,
        state: BeaconState
    ): SafeFuture<HeaderWithFallbackData> {
        return delegate.builderGetHeader(executionPayloadContext, state)
    }
}