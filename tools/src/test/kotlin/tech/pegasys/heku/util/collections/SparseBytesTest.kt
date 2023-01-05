package tech.pegasys.heku.util.collections

import org.apache.tuweni.bytes.Bytes.fromHexString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SparseBytesTest {

    @Test
    fun sanityTest() {
        val bb1 = fromHexString("0x00112233445566778899")
        val sbytes1 = SparseBytes.createSingleSlice(bb1)

        assertThat(sbytes1.isDense()).isTrue()
        assertThat(sbytes1.compacted()).isEqualTo(sbytes1)
        assertThat(sbytes1.toDenseBytes()).isEqualTo(bb1)

        val sbytes2 = SparseBytes.createSingleSlice(fromHexString("0xaabb"), 2)
        assertThat(sbytes2.isDense()).isFalse()

        run {
            val sbytes3 = sbytes1.overlapBy(sbytes2)
            assertThat(sbytes3.isDense()).isTrue()
            assertThat(sbytes3.slices).hasSize(1)
            assertThat(sbytes3.toDenseBytes()).isEqualTo(fromHexString("0x0011aabb445566778899"))
        }

        run {
            val sbytes4 = SparseBytes.createSingleSlice(fromHexString("0xcc"), 4)
            val sbytes5 = listOf(sbytes4, sbytes2).mergeNoOverlap()
            assertThat(sbytes5.isDense()).isFalse()
            assertThat(sbytes5.slices).hasSize(1)
            assertThat(sbytes5.slices[0].offset).isEqualTo(2)
            assertThat(sbytes5.slices[0].data).isEqualTo(fromHexString("0xaabbcc"))
        }

        run {
            val sbytes4 = SparseBytes.createSingleSlice(fromHexString("0xdd"), 5)
            val sbytes5 = listOf(sbytes4, sbytes2).mergeNoOverlap()
            assertThat(sbytes5.isDense()).isFalse()
            assertThat(sbytes5.slices).hasSize(2)

            val sbytes6 = sbytes1.overlapBy(sbytes5)
            assertThat(sbytes6.toDenseBytes()).isEqualTo(fromHexString("0x0011aabb44dd66778899"))
        }
    }
}