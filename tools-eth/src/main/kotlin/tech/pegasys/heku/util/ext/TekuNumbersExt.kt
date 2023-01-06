package tech.pegasys.heku.util.ext

import tech.pegasys.teku.infrastructure.unsigned.UInt64

fun Int.toUInt64() = UInt64.valueOf(this.toLong())
fun Long.toUInt64() = UInt64.valueOf(this)
