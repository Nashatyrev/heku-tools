package tech.pegasys.heku.util.ext

import java.lang.Long.min

fun IntRange.chunked(maxSize: Int): List<IntRange> =
    LongRange(start.toLong(), endInclusive.toLong())
        .chunked(maxSize)
        .map { IntRange(it.first.toInt(), it.last.toInt()) }

fun LongRange.chunked(maxSize: Int): List<LongRange> {
    val ret = mutableListOf<LongRange>()
    var start = this.first
    while (start <= this.last) {
        val endIncl = min(this.last, start + maxSize - 1)
        ret += start .. endIncl
        start = endIncl + 1
    }
    return ret
}

infix fun IntRange.intersectRange(other: IntRange): IntRange =
        Integer.max(this.first, other.first)..Integer.min(this.last, other.last)

infix fun IntRange.containsRange(other: IntRange): Boolean = this.first <= other.first && this.last >= other.last

infix fun IntRange.subtractRange(other: IntRange): List<IntRange> {
    val intersection = this.intersectRange(other)
    return if (intersection.isEmpty()) {
        listOf(this)
    } else {
        listOf(
            this.first until intersection.first,
            (intersection.last + 1)..this.last
        ).filter { !it.isEmpty() }
    }
}

infix fun Int.untilLength(length: Int) = this until this + length

val IntRange.size get() = endInclusive - start + 1