package tech.pegasys.heku.util.net.libp2p.gossip

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.apache.tuweni.bytes.Bytes
import pubsub.pb.Rpc
import tech.pegasys.heku.util.MTime
import tech.pegasys.heku.util.ext.consume
import tech.pegasys.heku.util.flow.SafeSharedFlow
import tech.pegasys.heku.util.flow.bufferWithError
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class GossipMessageOrGossip(val message: Rpc.Message?, val gossipMessageId: Bytes?) {
    val isFull get() = message != null
    val isGossip get() = !isFull
    override fun toString() = "GossipMessageOrGossip[" + (
            if (message != null) "messageBody=" + message.toStringM()
            else "messageID=$gossipMessageId"
            ) + "]"
}

data class ParsedMessage(
    val preparedMessage: PreparedGossipMessageAndTopic,
    val time: MTime = MTime.now()
) {
    val messageId get() = preparedMessage.messageId
    val topic get() = preparedMessage.beaconTopic
    val originalMessage get() = preparedMessage.preparedMessage.originalMessage
    val decodedMessage get() = preparedMessage.preparedMessage.decodedMessage.decodedMessageOrElseThrow
    override fun toString() =
        "ParsedMessage[$time, ${preparedMessage.beaconTopic}, id=$messageId, originalData: $originalMessage]"
}

data class WireMessage(
    val time: MTime,
    val msg: GossipMessageOrGossip,
    val origMsg: GossipWireMessage
) {
    override fun toString() = "WireMessage[$time, $msg]"
}

data class GossipMatchedMessage(
    val parsedMessage: ParsedMessage,
    val wireMessage: WireMessage
)

class GossipWireMessageMatcherFlows(
    wireMessageFlow: Flow<GossipWireMessage>,
    parsedMessageFlow: Flow<PreparedGossipMessageAndTopic>,
    dropMessageTimeout: Duration = 30.seconds,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    val matchedWireMessageCount = AtomicLong()
    val totalParsedMessageCount = AtomicLong()
    val unmatchedParsedMessageCount = AtomicLong()
    val unmatchedWireMessageCount = AtomicLong()

    private val matchFlowSink = SafeSharedFlow<GossipMatchedMessage>(1, name = "GossipWireMessageMatcherFlows.matchFlowSink")
    val matchFlow: Flow<GossipMatchedMessage> = matchFlowSink.sharedFlow()

    private val unmatchedParsedFlowSink =
        MutableSharedFlow<ParsedMessage>(1, 1024, BufferOverflow.DROP_LATEST)
    val unmatchedParsedFlow: SharedFlow<ParsedMessage> = unmatchedParsedFlowSink.asSharedFlow()

    private val unmatchedWireFlowSink =
        MutableSharedFlow<WireMessage>(1, 1024, BufferOverflow.DROP_LATEST)
    val unmatchedWireFlow: SharedFlow<WireMessage> = unmatchedWireFlowSink.asSharedFlow()

    init {
        val matcher = GossipWireMessageMatcher(
            {
                matchedWireMessageCount.incrementAndGet()
                matchFlowSink.emitOrThrow(it)
            },
            {
                unmatchedParsedFlowSink.tryEmit(it)
                unmatchedParsedMessageCount.incrementAndGet()
            },
            {
                unmatchedWireFlowSink.tryEmit(it)
                unmatchedWireMessageCount.incrementAndGet()
            },
            dropMessageTimeout
        )

        wireMessageFlow
            .bufferWithError(64 * 1024, "GossipMessageOrGossip.wireMessageFlow")
            .consume(scope) { matcher.onNewWireMessage(it) }
        parsedMessageFlow
            .bufferWithError(64 * 1024, "GossipMessageOrGossip.parsedMessageFlow")
            .consume(scope) {
                matcher.onNewParsedMessage(it)
                totalParsedMessageCount.incrementAndGet()
            }
    }
}

class GossipWireMessageMatcher(
    val matchConsumer: (GossipMatchedMessage) -> Unit,
    val unmatchedParsedConsumer: (ParsedMessage) -> Unit = { },
    val unmatchedWireConsumer: (WireMessage) -> Unit = { },
    val dropMessageTimeout: Duration = 30.seconds
) {

    private val parsedMessageById = mutableMapOf<Bytes, ParsedMessage>()
    private val parsedMessageByData = mutableMapOf<Bytes, ParsedMessage>()
    private val wireIhaveMessageById = mutableMapOf<Bytes, MutableList<WireMessage>>()
    private val wireFullMessageByData = mutableMapOf<Bytes, MutableList<WireMessage>>()

    private val matchedParsedMessageIds: MutableSet<Bytes> = mutableSetOf()


    private var lastPruneTime = MTime.now()

    private fun prune(curTime: MTime) {
        if (curTime - lastPruneTime > 5.seconds) {
            val oldParsedMessages = parsedMessageById.values
                .filter { curTime - it.time >= dropMessageTimeout }
            val oldParsedMessageIds = oldParsedMessages
                .map { it.messageId }.toSet()
            oldParsedMessages
                .filter { it.messageId !in matchedParsedMessageIds }
                .forEach { unmatchedParsedConsumer(it) }
            matchedParsedMessageIds -= oldParsedMessageIds
            parsedMessageById -= oldParsedMessageIds
            parsedMessageByData.entries
                .removeIf { curTime - it.value.time >= dropMessageTimeout }

            wireIhaveMessageById.values
                .filter { curTime - it[0].time >= dropMessageTimeout }
                .forEach { it.forEach { unmatchedWireConsumer(it) } }
            wireIhaveMessageById.entries
                .removeIf { curTime - it.value[0].time >= dropMessageTimeout }
            wireFullMessageByData.entries
                .removeIf { curTime - it.value[0].time >= dropMessageTimeout }

            lastPruneTime = curTime
        }
    }

    @Synchronized
    fun onNewParsedMessage(msg: PreparedGossipMessageAndTopic) {
        val message = ParsedMessage(msg)
        onNewParsed(message)
    }

    private fun onNewParsed(parsedMsg: ParsedMessage) {
        val wireMessages = findAndRemoveWireMessagesByParsedMessage(parsedMsg)
        wireMessages.forEach {
            matchConsumer(GossipMatchedMessage(parsedMsg, it))
        }
        if (wireMessages.isNotEmpty()) {
            matchedParsedMessageIds += parsedMsg.messageId
        }
        prune(parsedMsg.time)
        parsedMessageById[parsedMsg.messageId] = parsedMsg
        parsedMessageByData[parsedMsg.originalMessage] = parsedMsg
    }

    @Synchronized
    fun onNewWireMessage(msg: GossipWireMessage) {
        splitWireMsg(msg).forEach { onNewWire(it) }
    }

    private fun onNewWire(wireMsg: WireMessage) {
        val parsedMessage = findParsedByWireMessage(wireMsg.msg)
        if (parsedMessage != null) {
            // parsedIt.remove()
            matchConsumer(GossipMatchedMessage(parsedMessage, wireMsg))
            matchedParsedMessageIds += parsedMessage.messageId
        } else {
            prune(wireMsg.time)
            if (wireMsg.msg.message != null) {
                // full message
                wireFullMessageByData.getOrPut(
                    Bytes.wrap(wireMsg.msg.message.data.toByteArray()),
                    { mutableListOf() }) += wireMsg
            } else {
                wireIhaveMessageById.getOrPut(wireMsg.msg.gossipMessageId!!) { mutableListOf() } += wireMsg
            }
        }
    }

    private fun findAndRemoveWireMessagesByParsedMessage(parsedMessage: ParsedMessage): List<WireMessage> {
        return (wireIhaveMessageById.remove(parsedMessage.messageId) ?: emptyList()) +
                (wireFullMessageByData.remove(parsedMessage.originalMessage) ?: emptyList())
    }

    private fun findParsedByWireMessage(wireMsg: GossipMessageOrGossip): ParsedMessage? {
        return if (wireMsg.message != null) {
            // full gossip message
            parsedMessageByData[Bytes.wrap(wireMsg.message.data.toByteArray())]
        } else {
            // IHAVE gossip message
            parsedMessageById[wireMsg.gossipMessageId]
        }
    }

    companion object {

        private fun splitWireMsg(msg: GossipWireMessage): List<WireMessage> {
            val msgs = msg.message.publishList.map { GossipMessageOrGossip(it, null) }
            val gossips = if (msg.message.hasControl()) {
                msg.message.control.ihaveList.flatMap {
                    it.messageIDsList.map {
                        GossipMessageOrGossip(null, Bytes.wrap(it.toByteArray()))
                    }
                }
            } else {
                listOf()
            }
            return (msgs + gossips).map {
                WireMessage(MTime.now(), it, msg)
            }
        }

        private fun isMatch(msgParsed: ParsedMessage, msgWire: GossipMessageOrGossip) =
            if (msgWire.message != null) {
                msgParsed.originalMessage == Bytes.wrap(msgWire.message.data.toByteArray())
            } else {
                msgParsed.messageId == msgWire.gossipMessageId
            }
    }
}