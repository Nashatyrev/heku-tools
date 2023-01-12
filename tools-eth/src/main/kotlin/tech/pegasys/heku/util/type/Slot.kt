package tech.pegasys.heku.util.type

import tech.pegasys.teku.infrastructure.unsigned.UInt64

fun UInt64.asSlot() = Slot(this)
val Number.slots get() = Slot(this.toLong())

@JvmInline
value class Slot(val value: Long) : Comparable<Slot>{

    constructor(slot: UInt64) : this(slot.longValue())

    init {
        require(value >= 0)
    }

    val epoch get() = Epoch(value / 32)
    val uint64 get() = UInt64.valueOf(value)

    operator fun plus(otherSlot: Slot): Slot = Slot(value + otherSlot.value)
    operator fun plus(inc: Epoch): Slot = Slot(value + inc.startSlot.value)
    operator fun plus(otherSlot: Number): Slot = Slot(value + otherSlot.toLong())
    operator fun minus(dec: Slot): Slot = Slot(value - dec.value)
    operator fun minus(dec: Epoch): Slot = Slot(value - dec.startSlot.value)
    operator fun minus(dec: Number): Slot = Slot(value - dec.toLong())

    operator fun rem(i: Number): Slot = Slot(value % i.toLong())
    operator fun rem(i: Slot): Slot = Slot(value % i.value)

    operator fun inc() = Slot(value + 1)
    operator fun dec() = Slot(value - 1)

    override operator fun compareTo(other: Slot) = value.compareTo(other.value)

    infix fun floorTo(i: Slot) = this - (this % i)
    infix fun until(s: Slot) = (value until s.value).map { it.slots }
    operator fun rangeTo(s: Slot) = this until (s + 1)

    override fun toString() = value.toString()
    fun toStringLong() = "$value ($epoch ep + ${this - epoch.startSlot})"
}
