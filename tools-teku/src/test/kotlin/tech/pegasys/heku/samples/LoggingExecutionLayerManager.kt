package tech.pegasys.heku.samples

import org.apache.tuweni.bytes.Bytes32
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
import java.util.function.Function

class LoggingExecutionLayerManager(
    val delegate: ExecutionLayerManager,
    val logger: (String) -> Unit = { println("[LoggingExecutionLayerManager] $it")}
) : ExecutionLayerManager {


    override fun onSlot(slot: UInt64) {
        logger("--> onSlot($slot)")
        delegate.onSlot(slot)
    }

    override fun eth1GetPowBlock(blockHash: Bytes32): SafeFuture<Optional<PowBlock>> {
        val sCall = "eth1GetPowBlock($blockHash)"
        logger("--> $sCall")
        return delegate.eth1GetPowBlock(blockHash)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun eth1GetPowChainHead(): SafeFuture<PowBlock> {
        val sCall = "eth1GetPowChainHead()"
        logger("--> $sCall")
        return delegate.eth1GetPowChainHead()
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun engineForkChoiceUpdated(
        forkChoiceState: ForkChoiceState,
        payloadBuildingAttributes: Optional<PayloadBuildingAttributes>
    ): SafeFuture<ForkChoiceUpdatedResult> {

        val sCall = "engineForkChoiceUpdated($forkChoiceState, $payloadBuildingAttributes)"
        logger("--> $sCall")
        return delegate.engineForkChoiceUpdated(forkChoiceState, payloadBuildingAttributes)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun engineNewPayload(newPayloadRequest: NewPayloadRequest): SafeFuture<PayloadStatus> {
        val sCall = "engineNewPayload($newPayloadRequest)"
        logger("--> $sCall")
        return delegate.engineNewPayload(newPayloadRequest)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun engineGetPayload(
        executionPayloadContext: ExecutionPayloadContext,
        slot: UInt64
    ): SafeFuture<GetPayloadResponse> {
        val sCall = "engineGetPayload($executionPayloadContext, $slot)"
        logger("--> $sCall")
        return delegate.engineGetPayload(executionPayloadContext, slot)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun builderRegisterValidators(
        signedValidatorRegistrations: SszList<SignedValidatorRegistration>,
        slot: UInt64
    ): SafeFuture<Void> {
        val sCall = "builderRegisterValidators($signedValidatorRegistrations, $slot)"
        logger("--> $sCall")
        return delegate.builderRegisterValidators(signedValidatorRegistrations, slot)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun builderGetPayload(
        signedBlockContainer: SignedBlockContainer,
        getCachedPayloadResultFunction: Function<UInt64, Optional<ExecutionPayloadResult>>
    ): SafeFuture<BuilderPayload> {
        val sCall = "builderGetPayload($signedBlockContainer, $getCachedPayloadResultFunction)"
        logger("--> $sCall")
        return delegate.builderGetPayload(signedBlockContainer, getCachedPayloadResultFunction)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }

    override fun builderGetHeader(
        executionPayloadContext: ExecutionPayloadContext,
        state: BeaconState?
    ): SafeFuture<HeaderWithFallbackData> {
        val sCall = "builderGetHeader($executionPayloadContext, $state)"
        logger("--> $sCall")
        return delegate.builderGetHeader(executionPayloadContext, state)
            .thenPeek {
                logger("$sCall --> $it")
            }
            .catchAndRethrow {
                logger("$sCall !!! $it")
            }
    }
}