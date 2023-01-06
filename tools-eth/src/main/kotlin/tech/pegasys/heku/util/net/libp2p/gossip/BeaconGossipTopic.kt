package tech.pegasys.heku.util.net.libp2p.gossip

class BeaconGossipTopic(val topic: String) {

    val type = parseTopicType(topic)
    val subnetId = if (type.isSubnet) extractSubnetId(topic, type.prefix) else -1

    enum class TopicType(val prefix: String, val isSubnet: Boolean) {
        ATTESTATION_SUBNET("beacon_attestation_", true),
        BEACON_BLOCK("beacon_block", false),
        BEACON_AGGREGATE_AND_PROOF("beacon_aggregate_and_proof", false),
        VOLUNTARY_EXIT("voluntary_exit", false),
        PROPOSER_SLASHING("proposer_slashing", false),
        ATTESTER_SLASHING("attester_slashing", false),

        SYNC_COMMITTEE_SUBNET("sync_committee_", true),
        SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF("sync_committee_contribution_and_proof", false),

        // TODO add other topics
        UNKNOWN("", false)
    }

    private companion object {

        private fun parseTopicType(topic: String): TopicType {
            val regTopics = TopicType.values().filter { topic.contains("/${it.prefix}/") }
            check(regTopics.size <= 1)
            if (regTopics.isNotEmpty()) {
                return regTopics[0]
            }
            val subnetTopics = TopicType.values()
                .filter { it.isSubnet }
                .filter { topic.contains("/${it.prefix}") }

            check(subnetTopics.size <= 1)
            return if (subnetTopics.isNotEmpty()) {
                val candidate = subnetTopics[0]
                try {
                    extractSubnetId(topic, candidate.prefix)
                    candidate
                } catch (e: NumberFormatException) {
                    TopicType.UNKNOWN
                }
            } else {
                TopicType.UNKNOWN
            }
        }

        private fun extractSubnetId(topic: String, prefix: String): Int {
            val suffix = topic.substringAfter("/$prefix")
            val candidate = suffix.substringBefore("/")
            return candidate.toInt()
        }

    }

    override fun toString(): String {
        return topic
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BeaconGossipTopic) return false

        if (topic != other.topic) return false

        return true
    }

    override fun hashCode(): Int {
        return topic.hashCode()
    }
}