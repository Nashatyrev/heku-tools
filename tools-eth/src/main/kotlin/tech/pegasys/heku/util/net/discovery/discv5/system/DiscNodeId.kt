package tech.pegasys.heku.util.net.discovery.discv5.system

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.ethereum.beacon.discovery.util.Functions

class DiscNodeId(private val raw: Bytes32) : Bytes32 by raw {
    constructor(bb: Bytes) : this(Bytes32.wrap(bb))

    fun logDistance(other: DiscNodeId) = Functions.logDistance(this, other)

    override fun toString(): String {
        return "DiscNodeId[$raw]"
    }

    fun toShortString() = raw.toString().let { it.take(10) + "..." + it.takeLast(6) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DiscNodeId) return false

        if (raw != other.raw) return false

        return true
    }

    override fun hashCode(): Int {
        return raw.hashCode()
    }

    companion object {
        val ZERO = DiscNodeId(Bytes32.ZERO)

        fun startingWith(idPrefix: Bytes): DiscNodeId =
            DiscNodeId(
                Bytes32.wrap(
                    Bytes.concatenate(
                        idPrefix,
                        Bytes32.ZERO.slice(0, 32 - idPrefix.size())
                    )
                )
            )
    }
}