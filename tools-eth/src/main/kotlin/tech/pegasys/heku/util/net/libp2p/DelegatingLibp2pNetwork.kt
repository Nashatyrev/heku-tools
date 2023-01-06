/*
 * Copyright 2021 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.heku.util.net.libp2p;

import tech.pegasys.teku.networking.p2p.network.DelegatingP2PNetwork
import tech.pegasys.teku.networking.p2p.network.P2PNetwork
import tech.pegasys.teku.networking.p2p.peer.NodeId
import tech.pegasys.teku.networking.p2p.peer.Peer
import tech.pegasys.teku.networking.p2p.peer.PeerConnectedSubscriber
import java.util.*
import java.util.stream.Stream

open class DelegatingLibp2pNetwork(private val delegate: P2PNetwork<Peer>) :
    DelegatingP2PNetwork<Peer>(delegate) {

    override fun subscribeConnect(subscriber: PeerConnectedSubscriber<Peer>): Long {
        return delegate.subscribeConnect(subscriber)
    }

    override fun unsubscribeConnect(subscriptionId: Long) {
        delegate.unsubscribeConnect(subscriptionId)
    }

    override fun getPeer(id: NodeId): Optional<Peer> {
        return delegate.getPeer(id)
    }

    override fun streamPeers(): Stream<Peer> {
        return delegate.streamPeers()
    }
}
