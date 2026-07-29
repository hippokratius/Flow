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
    fun `federated channel url points at the originating instance`() {
        val channel = ContentId.peerTube("framatube.org", "news")

        assertThat(federatedChannelUrl(channel)).isEqualTo("https://framatube.org/c/news")
    }

    @Test
    fun `youTube and local channels have no federated url`() {
        assertThat(federatedChannelUrl(ContentId("UC123"))).isNull()
        assertThat(federatedChannelUrl(ContentId("local_5"))).isNull()
    }

    @Test
    fun `a malformed federated channel id yields null rather than a broken host`() {
        assertThat(federatedChannelUrl(ContentId("peertube_"))).isNull()
    }
}
