package tech.pegasys.heku.util.beacon.spec

import org.apache.tuweni.bytes.Bytes32
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.networks.Eth2NetworkConfiguration
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData
import tech.pegasys.teku.spec.networks.Eth2Network
import tech.pegasys.teku.spec.networks.Eth2Network.*

fun Eth2Network.spec() = PredefinedNetworks.NETWORK_SPECS[this] ?: throw IllegalArgumentException("Unknown network $this")

class PredefinedNetworks {

    companion object {
        private val PREDEFINED_GENESIS_DATA = mapOf(
            MAINNET to GenesisData(
                UInt64.valueOf(1606824023),
                Bytes32.fromHexString("0x4b363db94e286120d76eb905340fdd4e54bfe9f06bf33ff6cf5ad27f511bfe95")
            ),
            PRATER to GenesisData(
                UInt64.valueOf(1616508000),
                Bytes32.fromHexString("0x043db0d9a83813551ee2f33450d23797757d430911a9320530ad8a0eabc43efb")
            ),
            SEPOLIA to GenesisData(
                UInt64.valueOf(1655733600),
                Bytes32.fromHexString("0xd8ea171f3c94aea21ebc42a1ed61052acf3f9209c00e4efbaaddac09ed9b8078")
            ),
            GNOSIS to GenesisData(
                UInt64.valueOf(1638993340),
                Bytes32.fromHexString("0xf5dcb5564e829aab27264b9becd5dfaa017085611224cb3036f573368dbb9d47")
            ),
        )

        val NETWORK_SPECS = PREDEFINED_GENESIS_DATA
            .mapValues { (network, genesisData) ->
                val spec = Eth2NetworkConfiguration.builder()
                    .applyNetworkDefaults(network)
                    .build()
                    .spec
                SpecExt(spec, genesisData)
            }

        val FORK_DIGEST_TO_NETWORK = NETWORK_SPECS.entries
            .flatMap { (network, spec) ->
                spec.forks.allDigests.map { it to network }
            }.toMap()

    }
}
