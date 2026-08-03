package io.github.aedev.flow.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.link.ChannelLink
import org.junit.Test

/**
 * The subscription picker that replaced the "type a UC… id" field.
 *
 * The source filter is the part that matters. `ChannelLinkSubscriptionMirror` writes PeerTube
 * subscriptions into the same store, so an unfiltered picker would offer a PeerTube channel as the
 * YouTube half — and `ChannelLink.isValid` then drops the link without a word, leaving a button that
 * appears to do nothing.
 */
class ChannelLinkPickerTest {

    private val peerTubeId = ContentId.peerTube("tilvids.com", "news").raw

    private fun sub(id: String, name: String = "Name of $id", thumb: String = "") =
        ChannelSubscription(channelId = id, channelName = name, channelThumbnail = thumb)

    @Test
    fun `peertube subscriptions are not offered as youtube channels`() {
        val channels = youtubeSubscriptionChannels(
            listOf(sub("UCabc"), sub(peerTubeId), sub("UCdef"), sub("local_7"))
        )

        assertThat(channels.map { it.id }).containsExactly("UCabc", "UCdef").inOrder()
    }

    @Test
    fun `name and thumbnail carry over`() {
        val channels = youtubeSubscriptionChannels(
            listOf(sub("UCabc", name = "The News", thumb = "https://example/a.jpg"))
        )

        assertThat(channels.single().name).isEqualTo("The News")
        assertThat(channels.single().thumbnailUrl).isEqualTo("https://example/a.jpg")
        assertThat(channels.single().isSubscribed).isTrue()
    }

    /** A blank name would render an empty row the user cannot identify. */
    @Test
    fun `a nameless subscription falls back to its id`() {
        val channels = youtubeSubscriptionChannels(listOf(sub("UCabc", name = "")))

        assertThat(channels.single().name).isEqualTo("UCabc")
    }

    @Test
    fun `filtering by name ignores case and surrounding space`() {
        val channels = youtubeSubscriptionChannels(
            listOf(sub("UCabc", name = "The Morpheus"), sub("UCdef", name = "NTV Kenya"))
        )

        assertThat(filterChannelsByName(channels, "morph").map { it.id }).containsExactly("UCabc")
        assertThat(filterChannelsByName(channels, "  KENYA ").map { it.id }).containsExactly("UCdef")
        assertThat(filterChannelsByName(channels, "zzz")).isEmpty()
    }

    @Test
    fun `an empty filter keeps everything`() {
        val channels = youtubeSubscriptionChannels(listOf(sub("UCabc"), sub("UCdef")))

        assertThat(filterChannelsByName(channels, "")).hasSize(2)
        assertThat(filterChannelsByName(channels, "   ")).hasSize(2)
    }

    @Test
    fun `a stored name wins over the subscription list`() {
        val link = ChannelLink(
            youtubeChannelId = "UCabc",
            youtubeChannelName = "As stored",
            peerTubeChannelId = peerTubeId,
        )
        val names = subscriptionNamesById(listOf(sub("UCabc", name = "As subscribed")))

        assertThat(link.youtubeDisplayName(names)).isEqualTo("As stored")
    }

    /** Links made with the old id field have no stored name; the subscriptions supply one for free. */
    @Test
    fun `a nameless link borrows the subscription name`() {
        val link = ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = peerTubeId)
        val names = subscriptionNamesById(listOf(sub("UCabc", name = "As subscribed")))

        assertThat(link.youtubeDisplayName(names)).isEqualTo("As subscribed")
    }

    @Test
    fun `an unknown channel shows its id rather than nothing`() {
        val link = ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = peerTubeId)

        assertThat(link.youtubeDisplayName(emptyMap())).isEqualTo("UCabc")
    }

    @Test
    fun `already linked channels are not suggested again`() {
        val links = listOf(ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = peerTubeId))
        val youtube = youtubeSubscriptionChannels(listOf(sub("UCabc"), sub("UCdef")))

        assertThat(withoutLinkedChannels(youtube, links).map { it.id }).containsExactly("UCdef")
    }

    @Test
    fun `the peertube half is dropped from its suggestions too`() {
        val links = listOf(ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = peerTubeId))
        val other = ContentId.peerTube("framatube.org", "other").raw
        val peerTube = peerTubeSubscriptionChannels(listOf(sub(peerTubeId), sub(other)))

        assertThat(withoutLinkedChannels(peerTube, links).map { it.id }).containsExactly(other)
    }

    /**
     * The two spellings of one channel. A subscription made on a mirroring instance stores the
     * qualified form while a link made through search stores the origin form; without normalising,
     * the linked channel would keep being suggested.
     */
    @Test
    fun `a mirrored subscription counts as linked when its origin is linked`() {
        val originForm = ContentId.peerTube("framatube.org", "news").raw
        val mirrorForm = ContentId.peerTube("tilvids.com", "news@framatube.org").raw
        val links = listOf(ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = originForm))

        val suggestions = withoutLinkedChannels(
            peerTubeSubscriptionChannels(listOf(sub(mirrorForm))),
            links,
        )

        assertThat(suggestions).isEmpty()
    }

    @Test
    fun `nothing is dropped when there are no links`() {
        val youtube = youtubeSubscriptionChannels(listOf(sub("UCabc"), sub("UCdef")))

        assertThat(withoutLinkedChannels(youtube, emptyList())).hasSize(2)
    }

    @Test
    fun `the two suggestion lists do not bleed into each other`() {
        val subscriptions = listOf(sub("UCabc"), sub(peerTubeId), sub("local_7"))

        assertThat(youtubeSubscriptionChannels(subscriptions).map { it.id }).containsExactly("UCabc")
        assertThat(peerTubeSubscriptionChannels(subscriptions).map { it.id })
            .containsExactly(peerTubeId)
    }

    /** Subscribed on two instances, one channel — the picker must not offer it twice. */
    @Test
    fun `the same peertube channel subscribed twice is suggested once`() {
        val originForm = ContentId.peerTube("framatube.org", "news").raw
        val mirrorForm = ContentId.peerTube("tilvids.com", "news@framatube.org").raw

        val suggestions = peerTubeSubscriptionChannels(listOf(sub(originForm), sub(mirrorForm)))

        assertThat(suggestions).hasSize(1)
        assertThat(suggestions.single().id).isEqualTo(originForm)
    }

    @Test
    fun `blank subscription names are not offered as display names`() {
        val names = subscriptionNamesById(listOf(sub("UCabc", name = "")))

        assertThat(names).isEmpty()
    }
}
