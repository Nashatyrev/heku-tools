package tech.pegasys.heku.legacy

import tech.pegasys.heku.util.beacon.spec.SpecExt
import tech.pegasys.teku.networks.Eth2NetworkConfiguration
import tech.pegasys.teku.services.beaconchain.WeakSubjectivityInitializer
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint
import tech.pegasys.teku.spec.networks.Eth2Network
import java.util.*

/** for initialization only **/
private val NETWORKS_STARTED = listOf(
    Eth2Network.MAINNET,
    Eth2Network.PRATER,
    Eth2Network.ROPSTEN,
    Eth2Network.SEPOLIA,
    Eth2Network.KILN,
    Eth2Network.GNOSIS
)

private fun loadAll() = NETWORKS_STARTED.associateWith { load(it) }

private fun load(network: Eth2Network): SpecExt {
    val eth2NetworkConfiguration = Eth2NetworkConfiguration.builder()
        .applyNetworkDefaults(network)
        .build()

    val initialAnchor: Optional<AnchorPoint> = WeakSubjectivityInitializer().loadInitialAnchorPoint(
        eth2NetworkConfiguration.spec, eth2NetworkConfiguration.initialState
    )
    val anchorPoint = initialAnchor.orElseThrow()
    return SpecExt(
        eth2NetworkConfiguration.spec,
        GenesisData(anchorPoint.state.genesisTime, anchorPoint.state.genesisValidatorsRoot)
    )
}
