package tech.pegasys.heku.util.net.discovery

import kotlinx.coroutines.flow.Flow
import org.ethereum.beacon.discovery.schema.NodeRecord

interface NodeRecordUpdateTracker {

    data class NodeRecordUpdate(
        val oldRecord: NodeRecord?,
        val newRecord: NodeRecord
    ) {
        val isInitial get() = oldRecord == null
    }

    val updateFlow: Flow<NodeRecordUpdate>
}