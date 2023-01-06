package tech.pegasys.heku.util.ext

import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector

private fun SszBitvector.aggr(other: SszBitvector, op: (Boolean, Boolean) -> Boolean): SszBitvector {
    require(this.schema == other.schema)
    val orBits = this.asListUnboxed().zip(other.asListUnboxed())
        .map { op(it.first, it.second) }
    return this.schema.of(orBits)
}

fun SszBitvector.or(other: SszBitvector) = aggr(other){ b1, b2 -> b1 or b2}
fun SszBitvector.and(other: SszBitvector) = aggr(other){ b1, b2 -> b1 and b2}
fun SszBitvector.xor(other: SszBitvector) = aggr(other){ b1, b2 -> b1 xor b2}