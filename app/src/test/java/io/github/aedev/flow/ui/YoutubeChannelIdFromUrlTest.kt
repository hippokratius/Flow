package io.github.aedev.flow.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Recovering a channel id from the URL the YouTube channel route carries.
 *
 * The route addresses a channel by URL while a link is keyed on the `UC…` id, so this is what decides
 * whether the shared page is even considered. Returning something wrong would send the user to
 * another creator's page, so anything that is not plainly a `/channel/UC…` URL yields null and the
 * plain page is shown — exactly the behaviour that existed before links did.
 */
class YoutubeChannelIdFromUrlTest {

    @Test
    fun `a channel url yields its id`() {
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/channel/UCabc")).isEqualTo("UCabc")
    }

    @Test
    fun `trailing path segments are ignored`() {
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/channel/UCabc/videos"))
            .isEqualTo("UCabc")
    }

    /** A handle URL carries no id, so such channels are simply not gated. */
    @Test
    fun `a handle url yields null`() {
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/@someone")).isNull()
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/c/SomeName")).isNull()
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/user/SomeName")).isNull()
    }

    @Test
    fun `malformed input yields null rather than a wrong id`() {
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/")).isNull()
        assertThat(youtubeChannelIdFromUrl("https://www.youtube.com/channel/")).isNull()
        assertThat(youtubeChannelIdFromUrl("not a url")).isNull()
        assertThat(youtubeChannelIdFromUrl("")).isNull()
        assertThat(youtubeChannelIdFromUrl("   ")).isNull()
    }
}
