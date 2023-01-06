package tech.pegasys.heku.util.ext

import tech.pegasys.heku.util.MTime
import tech.pegasys.teku.infrastructure.unsigned.UInt64

fun UInt64.toMTime() = MTime(this.longValue() * 1000)
fun MTime.toEthSeconds(): UInt64 = UInt64.valueOf(millis / 1000)
