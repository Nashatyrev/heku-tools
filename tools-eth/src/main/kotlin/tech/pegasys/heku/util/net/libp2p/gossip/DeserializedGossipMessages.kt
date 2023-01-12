package tech.pegasys.heku.util.net.libp2p.gossip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import tech.pegasys.heku.util.beacon.spec.SpecExt
import tech.pegasys.heku.util.flow.bufferWithError
import tech.pegasys.heku.util.flow.shareInCompletable
import tech.pegasys.teku.infrastructure.ssz.SszContainer
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage

class DeserializedGossipMessages(
    messages : Flow<PreparedGossipMessageAndTopic>,
    spec: SpecExt,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) {
    private val sharedMessagesFlow = messages
        .bufferWithError(64 * 1024, name = "DeserializedGossipMessages.sharedMessagesFlow")
        .shareInCompletable(scope, SharingStarted.Lazily)

    // TODO need to pick up the right schema according to the topic digest
    private val contributionAndProofSchema = spec.getInstantSpecNow().specVersion.schemaDefinitions.toVersionAltair()
        .orElseThrow().signedContributionAndProofSchema
    private val syncCommitteeMessageSchema = spec.getInstantSpecNow().specVersion.schemaDefinitions.toVersionAltair()
        .orElseThrow().syncCommitteeMessageSchema

    data class DeserializedMessage<T : SszContainer>(
        val rawMessage: PreparedGossipMessageAndTopic,
        val message: T
    )

    val syncCommitteeContributionAndProofFlow: Flow<DeserializedMessage<SignedContributionAndProof>> = sharedMessagesFlow
        .filter { it.beaconTopic.type == BeaconGossipTopic.TopicType.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF }
        .map {
            val contributionAndProof =
                contributionAndProofSchema.sszDeserialize(it.preparedMessage.decodedMessage.decodedMessageOrElseThrow)
            DeserializedMessage(it, contributionAndProof)
        }
        .shareInCompletable(scope, SharingStarted.Lazily)

    val syncCommitteeSubnetFlow: Flow<DeserializedMessage<SyncCommitteeMessage>> = sharedMessagesFlow
        .filter { it.beaconTopic.type == BeaconGossipTopic.TopicType.SYNC_COMMITTEE_SUBNET }
        .map {
            val syncCommitteeMessage =
                syncCommitteeMessageSchema.sszDeserialize(it.preparedMessage.decodedMessage.decodedMessageOrElseThrow)
            DeserializedMessage(it, syncCommitteeMessage)
        }
        .shareInCompletable(scope, SharingStarted.Lazily)

}
