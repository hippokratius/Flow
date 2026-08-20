package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Stream extraction asks YouTube in English on purpose. Its answer used to be written over the
 * localized title, into the media notification and, every ten seconds, into the watch history — so a
 * German title was fetched and then lost.
 */
class PlayerTitlePolicyTest {

    private val german = "Die Newcomer-LLMs der letzten Woche im Test"
    private val english = "The newcomer LLMs of the last week, tested"

    @Test
    fun `the English extraction answer does not replace a title we already have`() {
        val title = PlayerTitlePolicy.resolveDisplayTitle(
            cachedTitle = german,
            extractionTitle = english,
        )

        assertThat(title).isEqualTo(german)
    }

    @Test
    fun `it is used when there is nothing else`() {
        val title = PlayerTitlePolicy.resolveDisplayTitle(
            cachedTitle = "",
            extractionTitle = english,
        )

        assertThat(title).isEqualTo(english)
    }

    @Test
    fun `a localized answer outranks the cached one`() {
        val title = PlayerTitlePolicy.resolveDisplayTitle(
            localizedTitle = german,
            cachedTitle = "Ein alter Titel",
            extractionTitle = english,
        )

        assertThat(title).isEqualTo(german)
    }

    @Test
    fun `DeArrow outranks everything`() {
        val title = PlayerTitlePolicy.resolveDisplayTitle(
            deArrowTitle = "Ein sachlicher Titel",
            localizedTitle = german,
            cachedTitle = german,
            extractionTitle = english,
        )

        assertThat(title).isEqualTo("Ein sachlicher Titel")
    }

    @Test
    fun `blank is not a title`() {
        val title = PlayerTitlePolicy.resolveDisplayTitle(
            deArrowTitle = "",
            localizedTitle = "   ",
            cachedTitle = german,
        )

        assertThat(title).isEqualTo(german)
    }

    @Test
    fun `nothing known at all is empty rather than null`() {
        assertThat(PlayerTitlePolicy.resolveDisplayTitle()).isEmpty()
    }
}
