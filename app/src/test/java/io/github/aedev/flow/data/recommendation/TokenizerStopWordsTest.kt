package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Topics become search queries. An English-only stop word list therefore did not merely add noise to
 * a German user's profile — it sent the discovery lane looking for videos about the word "die".
 */
class TokenizerStopWordsTest {

    private val tokenizer = NeuroTokenizer()

    @Test
    fun `German function words are not topics`() {
        val tokens = tokenizer.tokenize("Die Newcomer-LLMs der letzten Woche im Test")

        assertThat(tokens).containsNoneOf("die", "der", "im")
    }

    @Test
    fun `the German content words survive`() {
        val tokens = tokenizer.tokenize("Die besten Kamera Einstellungen für Fotografie")

        assertThat(tokens).contains("fotografie")
        assertThat(tokens).containsNoneOf("die", "für")
    }

    @Test
    fun `English titles filter as before`() {
        val tokens = tokenizer.tokenize("The Best Camera Settings for Photography")

        assertThat(tokens).contains("photography")
        assertThat(tokens).containsNoneOf("the", "for", "best")
    }
}
