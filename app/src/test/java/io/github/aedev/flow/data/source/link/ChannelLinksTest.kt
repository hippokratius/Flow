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

    /**
     * The same channel in two spellings. A subscription made on a mirroring instance stores
     * `name@originhost`; a link made through search stores the origin form. A lookup with either must
     * find the link — otherwise the hint row stays away from exactly the mirrored videos it exists for.
     */
    @Test
    fun `either spelling of a peertube id finds the link`() {
        val originForm = ContentId.peerTube("framatube.org", "news").raw
        val mirrorForm = ContentId.peerTube("tilvids.com", "news@framatube.org").raw
        val links = listOf(link("UCabc", originForm))

        assertThat(ChannelLinks.forPeerTube(links, originForm)).isNotNull()
        assertThat(ChannelLinks.forPeerTube(links, mirrorForm)).isNotNull()
        assertThat(ChannelLinks.counterpart(links, mirrorForm)).isEqualTo("UCabc")
    }

    @Test
    fun `a link stored in the mirror form is found by the origin form`() {
        val originForm = ContentId.peerTube("framatube.org", "news").raw
        val mirrorForm = ContentId.peerTube("tilvids.com", "news@framatube.org").raw
        val links = listOf(link("UCabc", mirrorForm))

        assertThat(ChannelLinks.forPeerTube(links, originForm)).isNotNull()
    }

    @Test
    fun `linking the same channel in both spellings yields one link`() {
        val originForm = ContentId.peerTube("framatube.org", "news").raw
        val mirrorForm = ContentId.peerTube("tilvids.com", "news@framatube.org").raw

        val links = ChannelLinks.add(
            ChannelLinks.add(emptyList(), link("UCabc", originForm)),
            link("UCdef", mirrorForm),
        )

        assertThat(links).hasSize(1)
    }

    @Test
    fun `normalisation leaves everything else alone`() {
        val plain = ContentId.peerTube("framatube.org", "news").raw

        assertThat(normalizePeerTubeChannelId(plain)).isEqualTo(plain)
        assertThat(normalizePeerTubeChannelId("UCabc")).isEqualTo("UCabc")
        assertThat(normalizePeerTubeChannelId("local_7")).isEqualTo("local_7")
        assertThat(normalizePeerTubeChannelId("")).isEmpty()
        assertThat(normalizePeerTubeChannelId("peertube_")).isEqualTo("peertube_")
        // An "@" that is not a host must not be mistaken for one.
        val oddName = ContentId.peerTube("tilvids.com", "news@nohost").raw
        assertThat(normalizePeerTubeChannelId(oddName)).isEqualTo(oddName)
    }

    @Test
    fun `the instance host is readable from the link`() {
        val link = link("UCabc", peerTubeNews)

        assertThat(link.peerTubeInstanceHost).isEqualTo("tilvids.com")
    }
}
