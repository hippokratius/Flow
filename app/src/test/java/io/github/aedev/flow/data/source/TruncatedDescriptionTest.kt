package io.github.aedev.flow.data.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TruncatedDescriptionTest {

    @Test
    fun `three dots mark a stub`() {
        assertThat("Quellen: https://api-docs.d...".isTruncatedDescription()).isTrue()
    }

    @Test
    fun `so does a single ellipsis character`() {
        assertThat("Quellen: https://api-docs.d…".isTruncatedDescription()).isTrue()
    }

    @Test
    fun `trailing whitespace does not hide the marker`() {
        assertThat("Da macht man eine Woche keine LLM-Reviews...\n".isTruncatedDescription()).isTrue()
    }

    @Test
    fun `a description that ends normally is whole`() {
        assertThat("Kapitel:\n0:00 Intro\n2:52 GLM 5.3".isTruncatedDescription()).isFalse()
    }

    @Test
    fun `an empty description is not a stub`() {
        assertThat("".isTruncatedDescription()).isFalse()
    }
}
