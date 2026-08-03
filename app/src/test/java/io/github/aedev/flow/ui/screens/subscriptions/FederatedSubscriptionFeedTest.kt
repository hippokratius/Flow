package io.github.aedev.flow.ui.screens.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import org.junit.Test

/**
 * Splitting subscriptions by source.
 *
 * Both halves matter. A federated id handed to the YouTube RSS endpoint is a guaranteed 404 every
 * refresh, and a YouTube id handed to the PeerTube lane would ask an instance about a channel it has
 * never heard of.
 */
class FederatedSubscriptionFeedTest {

    private val peerTube = ContentId.peerTube("tilvids.com", "news").raw
    private val mirrored = ContentId.peerTube("tilvids.com", "news@framatube.org").raw

    private val all = listOf("UCabcdef", peerTube, "UCzyx", mirrored, "local_7")

    @Test
    fun `federated ids are separated out`() {
        assertThat(federatedSubscriptionChannelIds(all)).containsExactly(peerTube, mirrored).inOrder()
    }

    @Test
    fun `youTube ids keep going to the rss lane`() {
        assertThat(youtubeSubscriptionChannelIds(all)).containsExactly("UCabcdef", "UCzyx").inOrder()
    }

    /** Local media is neither: it has no remote feed at all. */
    @Test
    fun `local ids end up in neither lane`() {
        assertThat(federatedSubscriptionChannelIds(all)).doesNotContain("local_7")
        assertThat(youtubeSubscriptionChannelIds(all)).doesNotContain("local_7")
    }

    @Test
    fun `an empty subscription list splits into two empty lanes`() {
        assertThat(federatedSubscriptionChannelIds(emptyList())).isEmpty()
        assertThat(youtubeSubscriptionChannelIds(emptyList())).isEmpty()
    }
}
