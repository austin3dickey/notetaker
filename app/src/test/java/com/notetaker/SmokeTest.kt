package com.notetaker

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Confirms the unit-test harness wires up before feature tests arrive. */
class SmokeTest {
    @Test
    fun `truth library is available`() {
        assertThat(2 + 2).isEqualTo(4)
    }
}
