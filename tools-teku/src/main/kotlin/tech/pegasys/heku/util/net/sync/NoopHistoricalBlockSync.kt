package tech.pegasys.heku.util.net.sync

import org.hyperledger.besu.plugin.services.MetricsSystem
import tech.pegasys.teku.beacon.sync.DefaultSyncServiceFactory
import tech.pegasys.teku.beacon.sync.SyncConfig
import tech.pegasys.teku.beacon.sync.events.SyncStateProvider
import tech.pegasys.teku.beacon.sync.historical.HistoricalBlockSyncService
import tech.pegasys.teku.beacon.sync.historical.ReconstructHistoricalStatesService
import tech.pegasys.teku.infrastructure.async.AsyncRunner
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory
import tech.pegasys.teku.infrastructure.async.SafeFuture
import tech.pegasys.teku.infrastructure.time.TimeProvider
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer
import tech.pegasys.teku.networking.p2p.network.P2PNetwork
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager
import tech.pegasys.teku.statetransition.blobs.BlobSidecarPool
import tech.pegasys.teku.statetransition.block.BlockImporter
import tech.pegasys.teku.statetransition.util.PendingPool
import tech.pegasys.teku.statetransition.validation.signatures.SignatureVerificationService
import tech.pegasys.teku.storage.api.StorageUpdateChannel
import tech.pegasys.teku.storage.client.CombinedChainDataClient
import tech.pegasys.teku.storage.client.RecentChainData
import java.time.Duration
import java.util.*

class NoopHistoricalBlockSync(
    spec: Spec?,
    blobsSidecarManager: BlobSidecarManager,
    metricsSystem: MetricsSystem?,
    storageUpdateChannel: StorageUpdateChannel?,
    asyncRunner: AsyncRunner?,
    network: P2PNetwork<Eth2Peer>?,
    chainData: CombinedChainDataClient?,
    syncStateProvider: SyncStateProvider?,
    signatureVerifier: AsyncBLSSignatureVerifier?,
    batchSize: UInt64?,
    reconstructHistoricalStatesService: Optional<ReconstructHistoricalStatesService>?,
    fetchAllHistoricBlocks: Boolean
) : HistoricalBlockSyncService(
    spec,
    blobsSidecarManager,
    metricsSystem,
    storageUpdateChannel,
    asyncRunner,
    network,
    chainData,
    syncStateProvider,
    signatureVerifier,
    batchSize,
    reconstructHistoricalStatesService,
    fetchAllHistoricBlocks
) {
    override fun doStart(): SafeFuture<*> = SafeFuture.COMPLETE
    override fun doStop(): SafeFuture<*> = SafeFuture.COMPLETE
}

class NoHistoricalBlockSyncServiceFactory(
    syncConfig: SyncConfig?,
    genesisStateResource: Optional<String>?,
    metrics: MetricsSystem?,
    asyncRunnerFactory: AsyncRunnerFactory,
    asyncRunner: AsyncRunner?,
    timeProvider: TimeProvider?,
    recentChainData: RecentChainData?,
    combinedChainDataClient: CombinedChainDataClient?,
    storageUpdateChannel: StorageUpdateChannel?,
    p2pNetwork: Eth2P2PNetwork?,
    blockImporter: BlockImporter?,
    blobsSidecarManager: BlobSidecarManager,
    pendingBlocks: PendingPool<SignedBeaconBlock>?,
    blobSidecarPool: BlobSidecarPool,
    getStartupTargetPeerCount: Int,
    signatureVerifier: SignatureVerificationService?,
    startupTimeout: Duration?,
    spec: Spec?
) : DefaultSyncServiceFactory(
    syncConfig,
    genesisStateResource,
    metrics,
    asyncRunnerFactory,
    asyncRunner,
    timeProvider,
    recentChainData,
    combinedChainDataClient,
    storageUpdateChannel,
    p2pNetwork,
    blockImporter,
    blobsSidecarManager,
    pendingBlocks,
    blobSidecarPool,
    getStartupTargetPeerCount,
    signatureVerifier,
    startupTimeout,
    spec
) {

    val noopHistoricalBlockSync = NoopHistoricalBlockSync(
        spec,
        blobsSidecarManager,
        metrics,
        storageUpdateChannel,
        asyncRunnerFactory.create(HistoricalBlockSyncService::class.java.simpleName, 1),
        p2pNetwork,
        combinedChainDataClient,
        null,
        signatureVerifier,
        UInt64.valueOf(50L),
        Optional.empty(),
        false
    )

    override fun createHistoricalSyncService(syncStateProvider: SyncStateProvider): HistoricalBlockSyncService =
        noopHistoricalBlockSync
}