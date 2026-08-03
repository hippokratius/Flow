package io.github.aedev.flow.ui

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import org.junit.Test

/**
 * Regression guard for a real defect: `youtubeChannelUrl` ends in a catch-all branch that treats
 * any unrecognised value as a YouTube handle. Once channel ids carried a source prefix, tapping the
 * channel of a PeerTube video navigated to `https://www.youtube.com/@peertube_host_name` — a page
 * that cannot exist.
 */
class ChannelUrlSourceGuardTest {

    @Test
    fun `a federated channel id never becomes a youtube url`() {
        val peerTubeChannel = ContentId.peerTube("tilvids.com", "news").raw

        assertThat(youtubeChannelUrl(peerTubeChannel)).isNull()
        assertThat(youtubeChannelRoute(peerTubeChannel)).isNull()
    }

    @Test
    fun `local media ids are not treated as channels either`() {
        assertThat(youtubeChannelUrl("local_42")).isNull()
    }

    @Test
    fun `youTube channel ids still resolve`() {
        assertThat(youtubeChannelUrl("UCabcdef"))
            .isEqualTo("https://www.youtube.com/channel/UCabcdef")
    }

    @Test
    fun `youTube handles still resolve`() {
        assertThat(youtubeChannelUrl("@someone")).isEqualTo("https://www.youtube.com/@someone")
        assertThat(youtubeChannelUrl("someone")).isEqualTo("https://www.youtube.com/@someone")
    }

    @Test
    fun `full youTube urls still resolve`() {
        assertThat(youtubeChannelUrl("https://www.youtube.com/channel/UCabcdef"))
            .isEqualTo("https://www.youtube.com/channel/UCabcdef")
    }

    @Test
    fun `blank input yields null`() {
        assertThat(youtubeChannelUrl("   ")).isNull()
    }
}
