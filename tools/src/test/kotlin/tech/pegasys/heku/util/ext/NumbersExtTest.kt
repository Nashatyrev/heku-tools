package tech.pegasys.heku.util.ext

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class NumbersExtTest {

    @Test
    fun `IntRange intersect tests`() {
        assertThat((0..5) intersectRange (6..7)).isEmpty()
        assertThat((6..7) intersectRange (0..5)).isEmpty()
        assertThat((6..7) intersectRange IntRange.EMPTY).isEmpty()
        assertThat(IntRange.EMPTY intersectRange IntRange.EMPTY).isEmpty()

        assertThat((0..5) intersectRange (5..7)).isEqualTo(5..5)
        assertThat((5..7) intersectRange (0..5)).isEqualTo(5..5)
        assertThat((0..5) intersectRange (5..5)).isEqualTo(5..5)
        assertThat((0..5) intersectRange (3..3)).isEqualTo(3..3)
        assertThat((0..5) intersectRange (0..0)).isEqualTo(0..0)

        assertThat((0..5) intersectRange (1..2)).isEqualTo(1..2)
        assertThat((1..2) intersectRange (0..5)).isEqualTo(1..2)

        assertThat((2..5) intersectRange (0..4)).isEqualTo(2..4)
        assertThat((0..4) intersectRange (2..5)).isEqualTo(2..4)
    }

    @Test
    fun `IntRange subtract tests`() {
        assertThat((5..5) subtractRange (0..5)).isEmpty()
        assertThat((0..5) subtractRange (0..5)).isEmpty()
        assertThat((1..5) subtractRange (0..5)).isEmpty()
        assertThat((0..4) subtractRange (0..5)).isEmpty()
        assertThat((1..4) subtractRange (0..5)).isEmpty()

        assertThat((0..5) subtractRange (6..7)).isEqualTo(listOf(0..5))
        assertThat((6..7) subtractRange (0..5)).isEqualTo(listOf(6..7))
        assertThat((0..5) subtractRange (5..7)).isEqualTo(listOf(0..4))
        assertThat((5..7) subtractRange (0..5)).isEqualTo(listOf(6..7))
        assertThat((0..5) subtractRange (5..5)).isEqualTo(listOf(0..4))
        assertThat((0..5) subtractRange (4..7)).isEqualTo(listOf(0..3))
        assertThat((4..7) subtractRange (0..5)).isEqualTo(listOf(6..7))
        assertThat((4..7) subtractRange (4..5)).isEqualTo(listOf(6..7))
        assertThat((4..7) subtractRange (6..7)).isEqualTo(listOf(4..5))

        assertThat((0..5) subtractRange (2..3)).isEqualTo(listOf(0..1, 4..5))
        assertThat((0..5) subtractRange (1..2)).isEqualTo(listOf(0..0, 3..5))
        assertThat((0..5) subtractRange (3..4)).isEqualTo(listOf(0..2, 5..5))
    }
}