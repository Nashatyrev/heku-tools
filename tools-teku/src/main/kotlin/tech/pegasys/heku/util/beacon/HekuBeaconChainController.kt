package tech.pegasys.heku.util.beacon

import tech.pegasys.heku.util.net.sync.NoHistoricalBlockSyncServiceFactory
import tech.pegasys.teku.beacon.sync.SyncServiceFactory
import tech.pegasys.teku.ethereum.events.SlotEventsChannel
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.infrastructure.ssz.SszList
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork
import tech.pegasys.teku.networking.eth2.Eth2P2PNetworkBuilder
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory
import tech.pegasys.teku.networking.p2p.discovery.DiscoveryNetworkBuilder
import tech.pegasys.teku.networking.p2p.libp2p.LibP2PNetworkBuilder
import tech.pegasys.teku.service.serviceutils.ServiceConfig
import tech.pegasys.teku.services.beaconchain.BeaconChainConfiguration
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.datastructures.attestation.ValidateableAttestation
import tech.pegasys.teku.spec.datastructures.operations.Attestation
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier
import tech.pegasys.teku.statetransition.attestation.AttestationManager
import tech.pegasys.teku.statetransition.block.BlockImportNotifications
import tech.pegasys.teku.statetransition.util.FutureItems
import tech.pegasys.teku.statetransition.validation.AggregateAttestationValidator
import tech.pegasys.teku.statetransition.validation.AttestationValidator
import tech.pegasys.teku.statetransition.validation.InternalValidationResult
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel
import tech.pegasys.teku.storage.api.StorageUpdateChannel
import tech.pegasys.teku.storage.client.RecentChainData
import java.util.function.Consumer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class HekuBeaconChainController(
    serviceConfig: ServiceConfig,
    beaconConfig: BeaconChainConfiguration,
    val customLibP2PNetworkBuilder: LibP2PNetworkBuilder,
    val customDiscoveryNetworkBuilder: DiscoveryNetworkBuilder,
    val historicalBlockSyncEnabled: Boolean,
    val validateAttestations: Boolean,
    val startBeaconChainMetrics: Boolean,
    val customStatusMessageFactory: ((RecentChainData) -> StatusMessageFactory)? = null,
) : ServiceAwareBeaconChainController(serviceConfig, beaconConfig) {

    inner class HekuEth2P2PNetworkBuilder : Eth2P2PNetworkBuilder() {
        override fun createLibP2PNetworkBuilder(): LibP2PNetworkBuilder = customLibP2PNetworkBuilder
        override fun createDiscoveryNetworkBuilder(): DiscoveryNetworkBuilder = customDiscoveryNetworkBuilder

        override fun build(): Eth2P2PNetwork {
            if (customStatusMessageFactory != null) {
                statusMessageFactory = customStatusMessageFactory.invoke(recentChainData)
            }
            return super.build()
        }
    }

    override fun createEth2P2PNetworkBuilder(): Eth2P2PNetworkBuilder = HekuEth2P2PNetworkBuilder()

    override fun createSyncServiceFactory(): SyncServiceFactory =
        if (historicalBlockSyncEnabled)
            super.createSyncServiceFactory()
        else
            NoHistoricalBlockSyncServiceFactory(
                this.beaconConfig.syncConfig(),
                this.beaconConfig.eth2NetworkConfig().getGenesisState(),
                this.metricsSystem,
                this.asyncRunnerFactory,
                this.beaconAsyncRunner,
                this.timeProvider,
                this.recentChainData,
                this.combinedChainDataClient,
                eventChannels.getPublisher(StorageUpdateChannel::class.java, beaconAsyncRunner),
                this.p2pNetwork,
                this.blockImporter,
                this.pendingBlocks,
                this.beaconConfig.eth2NetworkConfig().startupTargetPeerCount,
                signatureVerificationService,
                this.beaconConfig.eth2NetworkConfig().startupTimeoutSeconds.seconds.toJavaDuration(),
                this.spec
            )

    override fun initAttestationManager() {
        val attestationValidator =
            if (validateAttestations)
                AttestationValidator(spec, recentChainData, signatureVerificationService)
            else
                NoopAttestationValidator(spec, recentChainData, signatureVerificationService)

        val aggregateAttestationValidator =
            if (validateAttestations)
                AggregateAttestationValidator(spec, attestationValidator, signatureVerificationService)
            else
                NoopAggregateAttestationValidator(spec, attestationValidator, signatureVerificationService)

        blockImporter.subscribeToVerifiedBlockAttestations { slot: UInt64?, attestations: SszList<Attestation?> ->
            attestations.forEach(
                Consumer { attestation: Attestation? ->
                    aggregateAttestationValidator.addSeenAggregate(
                        ValidateableAttestation.from(spec, attestation)
                    )
                })
        }
        val pendingAttestations = pendingPoolFactory.createForAttestations(spec)
        val futureAttestations = FutureItems.create(
            { obj: ValidateableAttestation -> obj.earliestSlotForForkChoiceProcessing },
            UInt64.valueOf(3),
            futureItemsMetric,
            "attestations"
        )
        attestationManager = AttestationManager.create(
            pendingAttestations,
            futureAttestations,
            forkChoice,
            attestationPool,
            attestationValidator,
            aggregateAttestationValidator,
            signatureVerificationService,
            eventChannels.getPublisher(ActiveValidatorChannel::class.java, beaconAsyncRunner)
        )

        eventChannels
            .subscribe(SlotEventsChannel::class.java, attestationManager)
            .subscribe(FinalizedCheckpointChannel::class.java, pendingAttestations)
            .subscribe(BlockImportNotifications::class.java, attestationManager)
    }

    override fun initMetrics() {
        if (startBeaconChainMetrics) {
            super.initMetrics()
        }
    }

    val forkChoicePublic get() = super.forkChoice

    internal class NoopAttestationValidator(
        spec: Spec,
        recentChainData: RecentChainData,
        signatureVerifier: AsyncBLSSignatureVerifier
    ) : AttestationValidator(spec, recentChainData, signatureVerifier) {
        override fun validate(validateableAttestation: ValidateableAttestation?): SafeFuture<InternalValidationResult> =
            SafeFuture.completedFuture(InternalValidationResult.ACCEPT)
    }

    internal class NoopAggregateAttestationValidator(
        spec: Spec,
        attestationValidator: AttestationValidator,
        signatureVerifier: AsyncBLSSignatureVerifier
    ) : AggregateAttestationValidator(spec, attestationValidator, signatureVerifier) {

        override fun addSeenAggregate(attestation: ValidateableAttestation) { }

        override fun validate(attestation: ValidateableAttestation?): SafeFuture<InternalValidationResult> =
            SafeFuture.completedFuture(InternalValidationResult.ACCEPT)
    }
}

