package tech.consensys.linea.util.libp2p

import io.libp2p.core.ChannelVisitor
import io.libp2p.core.Connection
import io.libp2p.core.PeerId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

class ConnectionsTracker(
    val currentTime: () -> Instant = { Clock.System.now() }
) : ChannelVisitor<Connection> {

    override fun visit(conn: Connection) {
        connected(conn)
        conn.closeFuture()
            .thenAccept {
                disconnected(conn)
            }
    }

    data class ConnectionId(
        val from: PeerId,
        val to: PeerId
    )

    data class ConnectionRecord(
        val id: ConnectionId,
        val time: Instant,
        var duration: Duration = Duration.INFINITE
    )

    private val connectionsW = mutableListOf<ConnectionRecord>()

    val allConnections: List<ConnectionRecord>
        @Synchronized
        get() = connectionsW
    val activeConnections: List<ConnectionRecord>
        @Synchronized
        get() = allConnections.filter { it.duration.isInfinite() }
    val activeConnectionsById = activeConnections.associateBy { it.id }

    @Synchronized
    private fun connected(conn: Connection) {
        val connId = conn.connectionId
        if (connId !in activeConnectionsById) {
            connectionsW += ConnectionRecord(connId, currentTime())
        }
    }

    @Synchronized
    private fun disconnected(conn: Connection) {
        val connId = conn.connectionId
        activeConnectionsById[connId]?.apply {
            duration = currentTime() - time
        }
    }

    companion object {
        private val Connection.initiatorId get() = if (isInitiator) secureSession().localId else secureSession().remoteId
        private val Connection.responderId get() = if (isInitiator) secureSession().remoteId else secureSession().localId
        private val Connection.connectionId get() = ConnectionId(initiatorId, responderId)
    }
}