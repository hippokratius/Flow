package io.github.aedev.flow.data.source.link

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import org.junit.Test

/**
 * The link list's rules.
 *
 * The one that matters is "one channel, one counterpart": every consumer — the hint row, the
 * subscription mirror — asks for *the* counterpart and would otherwise have to pick one of several
 * arbitrarily, which reads as a bug wherever it surfaces.
 */
class ChannelLinksTest {

    private val peerTubeNews = ContentId.peerTube("tilvids.com", "news").raw
    private val peerTubeOther = ContentId.peerTube("framatube.org", "news").raw

    private fun link(youtube: String, peerTube: String) = ChannelLink(
        youtubeChannelId = youtube,
        youtubeChannelName = "Name of $youtube",
        peerTubeChannelId = peerTube,
        peerTubeChannelName = "PeerTube $peerTube",
        createdAt = 1L,
    )

    @Test
    fun `a link is found from either side`() {
        val links = listOf(link("UCabc", peerTubeNews))

        assertThat(ChannelLinks.forYouTube(links, "UCabc")).isNotNull()
        assertThat(ChannelLinks.forPeerTube(links, peerTubeNews)).isNotNull()
        assertThat(ChannelLinks.counterpart(links, "UCabc")).isEqualTo(peerTubeNews)
        assertThat(ChannelLinks.counterpart(links, peerTubeNews)).isEqualTo("UCabc")
    }

    @Test
    fun `an unknown channel has no counterpart`() {
        val links = listOf(link("UCabc", peerTubeNews))

        assertThat(ChannelLinks.counterpart(links, "UCzzz")).isNull()
        assertThat(ChannelLinks.counterpart(links, peerTubeOther)).isNull()
        assertThat(ChannelLinks.counterpart(links, "")).isNull()
        assertThat(ChannelLinks.counterpart(links, "local_7")).isNull()
    }

    @Test
    fun `relinking the same youtube channel replaces the old pairing`() {
        val links = ChannelLinks.add(
            ChannelLinks.add(emptyList(), link("UCabc", peerTubeNews)),
            link("UCabc", peerTubeOther),
        )

        assertThat(links).hasSize(1)
        assertThat(ChannelLinks.counterpart(links, "UCabc")).isEqualTo(peerTubeOther)
    }

    /** Pointing a second YouTube channel at an already-linked PeerTube channel replaces it too. */
    @Test
    fun `relinking the same peertube channel replaces the old pairing`() {
        val links = ChannelLinks.add(
            ChannelLinks.add(emptyList(), link("UCabc", peerTubeNews)),
            link("UCdef", peerTubeNews),
        )

        assertThat(links).hasSize(1)
        assertThat(ChannelLinks.counterpart(links, peerTubeNews)).isEqualTo("UCdef")
        assertThat(ChannelLinks.counterpart(links, "UCabc")).isNull()
    }

    @Test
    fun `distinct pairs coexist, newest first`() {
        val links = ChannelLinks.add(
            ChannelLinks.add(emptyList(), link("UCabc", peerTubeNews)),
            link("UCdef", peerTubeOther),
        )

        assertThat(links).hasSize(2)
        assertThat(links.first().youtubeChannelId).isEqualTo("UCdef")
    }

    @Test
    fun `removal works from either side`() {
        val links = ChannelLinks.add(emptyList(), link("UCabc", peerTubeNews))

        assertThat(ChannelLinks.remove(links, "UCabc")).isEmpty()
        assertThat(ChannelLinks.remove(links, peerTubeNews)).isEmpty()
        assertThat(ChannelLinks.remove(links, "UCzzz")).hasSize(1)
        assertThat(ChannelLinks.remove(links, "")).hasSize(1)
    }

    /**
     * A link whose halves point at the wrong source is refused rather than stored. Two YouTube ids
     * would make `counterpart` return a YouTube id for a YouTube channel, and the hint row would
     * offer to open the channel the user is already on.
     */
    @Test
    fun `a malformed link is not stored`() {
        assertThat(ChannelLinks.add(emptyList(), link("UCabc", "UCdef"))).isEmpty()
        assertThat(ChannelLinks.add(emptyList(), link(peerTubeNews, peerTubeOther))).isEmpty()
        assertThat(ChannelLinks.add(emptyList(), link("", peerTubeNews))).isEmpty()
        assertThat(ChannelLinks.add(emptyList(), link("UCabc", ""))).isEmpty()
    }

    @Test
    fun `the instance host is readable from the link`() {
        val link = link("UCabc", peerTubeNews)

        assertThat(link.peerTubeInstanceHost).isEqualTo("tilvids.com")
    }
}
