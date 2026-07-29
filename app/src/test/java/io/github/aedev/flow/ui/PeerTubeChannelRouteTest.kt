package io.github.aedev.flow.ui

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import org.junit.Test
import java.net.URLDecoder

/**
 * The dispatch that decides which channel page a tap opens.
 *
 * `navigateToYoutubeChannel` needs a `NavHostController`, so the decision itself lives in this pure
 * function and is tested here. Both directions matter: a YouTube id must keep reaching the existing
 * screen, and a federated id must not silently fall through to it.
 */
class PeerTubeChannelRouteTest {

    @Test
    fun `a federated channel id yields an encoded in-app route`() {
        val id = ContentId.peerTube("tilvids.com", "news").raw

        val route = peerTubeChannelRoute(id)

        assertThat(route).isNotNull()
        assertThat(route).startsWith("peertubeChannel?id=")
        val encoded = route!!.substringAfter("id=")
        assertThat(URLDecoder.decode(encoded, Charsets.UTF_8.name())).isEqualTo(id)
    }

    /** A mirrored channel's handle contains an `@`, which must survive the round trip. */
    @Test
    fun `a mirrored channel handle survives encoding`() {
        val id = ContentId.peerTube("tilvids.com", "news@framatube.org").raw

        val encoded = peerTubeChannelRoute(id)!!.substringAfter("id=")

        assertThat(encoded).doesNotContain("@")
        assertThat(URLDecoder.decode(encoded, Charsets.UTF_8.name())).isEqualTo(id)
    }

    @Test
    fun `the route pattern and the produced route agree on the argument name`() {
        assertThat(PEERTUBE_CHANNEL_ROUTE).isEqualTo("peertubeChannel?id={channelId}")
        assertThat(peerTubeChannelRoute(ContentId.peerTube("a.tld", "b").raw))
            .startsWith(PEERTUBE_CHANNEL_ROUTE.substringBefore("{"))
    }

    @Test
    fun `youTube and local ids are not routed here`() {
        assertThat(peerTubeChannelRoute("UCabcdef")).isNull()
        assertThat(peerTubeChannelRoute("@someone")).isNull()
        assertThat(peerTubeChannelRoute("local_42")).isNull()
        assertThat(peerTubeChannelRoute("   ")).isNull()
    }

    /** A prefix without a host or without a handle is not addressable and must not navigate. */
    @Test
    fun `malformed federated ids yield null`() {
        assertThat(peerTubeChannelRoute("peertube_")).isNull()
        assertThat(peerTubeChannelRoute("peertube_tilvids.com")).isNull()
        assertThat(peerTubeChannelRoute("peertube_tilvids.com_")).isNull()
    }
}
