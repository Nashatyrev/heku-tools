package tech.pegasys.heku.fixtures.net

import tech.pegasys.heku.util.net.libp2p.gossip.GossipWireHandler

class GossipWireHandlerUtil {

    class TestHandler : GossipWireHandler() {
        val emitter = eventsPriv
    }
}