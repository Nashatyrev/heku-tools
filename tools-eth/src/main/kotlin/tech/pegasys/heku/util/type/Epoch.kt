package tech.pegasys.heku.util.type

import tech.pegasys.teku.infrastructure.unsigned.UInt64

fun UInt64.asEpochs() = Epoch(this.longValue())
val Number.epochs get() = Epoch(this.toLong())

@JvmInline
value class Epoch(val value: Long) : Comparable<Epoch> {

    init {
        require(value >= 0)
    }

    val uint64 get() = UInt64.valueOf(value)

    val startSlot get() = Slot(value * 32)
    val endSlot get() = Slot((value + 1) * 32)
    val lastSlot get() = endSlot.dec()

    operator fun plus(inc: Epoch): Epoch = Epoch(value + inc.value)
    operator fun plus(inc: Number): Epoch = Epoch(value + inc.toLong())
    operator fun minus(dec: Epoch): Epoch = Epoch(value - dec.value)
    operator fun minus(dec: Number): Epoch = Epoch(value - dec.toLong())

    operator fun times(i: Int): Epoch = Epoch(value * i)

    operator fun rem(i: Number): Epoch = Epoch(value % i.toLong())
    operator fun rem(i: Epoch): Epoch = Epoch(value % i.value)

    operator fun inc() = Epoch(value + 1)
    operator fun dec() = Epoch(value - 1)

    override operator fun compareTo(other: Epoch) = value.compareTo(other.value)

    infix fun floorTo(i: Epoch) = this - (this % i)
    infix fun until(e: Epoch) = (value until e.value).map { it.epochs }
    operator fun rangeTo(e: Epoch) = this until (e + 1)

    override fun toString() = value.toString()

    companion object {
        val ZERO = Epoch(0)
    }
}