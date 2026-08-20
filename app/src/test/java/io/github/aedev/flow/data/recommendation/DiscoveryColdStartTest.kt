package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A fresh install has no taste profile, and the discovery lane used to fill that gap with five
 * English keywords — which is a third of the home feed answering in English no matter which region
 * the user is in.
 */
class DiscoveryColdStartTest {

    @Test
    fun `an empty profile asks for nothing rather than for English`() {
        assertThat(coldStartQueries(preferredTopics = emptySet(), blocked = emptySet())).isEmpty()
    }

    @Test
    fun `a profile that exists is used`() {
        val queries = coldStartQueries(
            preferredTopics = setOf("fotografie", "kochen", "radfahren"),
            blocked = emptySet(),
        )

        assertThat(queries).containsExactly("fotografie", "kochen", "radfahren")
    }

    @Test
    fun `blocked topics are not searched for`() {
        val queries = coldStartQueries(
            preferredTopics = setOf("fotografie", "kochen"),
            blocked = setOf("koch"),
        )

        assertThat(queries).containsExactly("fotografie")
    }

    @Test
    fun `at most five queries`() {
        val queries = coldStartQueries(
            preferredTopics = (1..12).map { "thema$it" }.toSet(),
            blocked = emptySet(),
        )

        assertThat(queries).hasSize(5)
    }
}
