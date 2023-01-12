package tech.pegasys.heku.util.beacon.spec

import org.apache.tuweni.bytes.Bytes32
import tech.pegasys.teku.infrastructure.unsigned.UInt64
import tech.pegasys.teku.spec.Spec
import tech.pegasys.teku.spec.config.SpecConfig
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.EnrForkId
import tech.pegasys.teku.spec.datastructures.state.ForkInfo

class Forks(
    private val genesisValidatorsRoot: Bytes32,
    private val spec: Spec
) {

    private val digestToMilestone = spec.forkSchedule
        .activeMilestones.associate {
            val fork = it.fork
            val forkInfo = ForkInfo(fork, genesisValidatorsRoot)
            val forkDigest = forkInfo.getForkDigest(spec)
            forkDigest to it.specMilestone
        }

    val milestoneToFork = spec.forkSchedule.activeMilestones
        .associate {
            it.specMilestone to it.fork
        }

    val allDigests get() = digestToMilestone.keys

    fun createEnrForkId(epoch: UInt64): EnrForkId {
        val fork = spec.forkSchedule.getFork(epoch)
        val nextFork = spec.forkSchedule.getNextFork(fork.epoch)
        val nextVersion = nextFork
            .map { it.currentVersion }
            .orElse(fork.currentVersion)
        val nextForkEpoch: UInt64 = nextFork
            .map { it.epoch }
            .orElse(SpecConfig.FAR_FUTURE_EPOCH)

        val forkDigest = spec.computeForkDigest(fork.currentVersion, genesisValidatorsRoot)
        return EnrForkId(forkDigest, nextVersion, nextForkEpoch)
    }

    companion object {
        fun fromSpec(spec: Spec, genesisValidatorsRoot: Bytes32)= Forks(genesisValidatorsRoot, spec)
    }
}