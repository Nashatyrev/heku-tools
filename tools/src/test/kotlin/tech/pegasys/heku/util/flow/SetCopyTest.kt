package tech.pegasys.heku.util.flow

import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class SetCopyTest {

    @Test
    fun `check mutableSet snapshot`() {
        val mutableSet = mutableSetOf("a", "b")
        val set = mutableSet.toSet()

        mutableSet += "c"

        Assertions.assertThat(set).hasSize(2)
    }
}