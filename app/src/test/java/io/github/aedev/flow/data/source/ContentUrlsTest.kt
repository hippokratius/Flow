package io.github.aedev.flow.data.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentUrlsTest {

    @Test
    fun `youTube watch url keeps the historical shape`() {
        assertThat(watchUrl(ContentId("dQw4w9WgXcQ")))
            .isEqualTo("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    }

    @Test
    fun `peerTube watch url is built against the originating instance`() {
        val id = ContentId.peerTube("tilvids.com", "9b1deb4d-3b7d-4bad")

        assertThat(watchUrl(id)).isEqualTo("https://tilvids.com/w/9b1deb4d-3b7d-4bad")
    }

    @Test
    fun `local media has no shareable url`() {
        assertThat(watchUrl(ContentId("local_42"))).isEmpty()
    }

    @Test
    fun `channel url follows the source`() {
        assertThat(channelUrl(ContentId("dQw4w9WgXcQ"), "UC123"))
            .isEqualTo("https://www.youtube.com/channel/UC123")
        assertThat(channelUrl(ContentId.peerTube("framatube.org", "x"), "news"))
            .isEqualTo("https://framatube.org/c/news")
    }
}
