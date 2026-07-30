package io.github.aedev.flow.data.source.link

import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.source.ContentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The subscription mirror.
 *
 * Two failure modes are worth guarding. Mirroring when the user switched it off is an unasked-for
 * subscription, and mirroring an id that is already in the wanted state churns the store on every
 * tap — the second one also matters because `SubscriptionRepository.subscribe` rewrites the ordering
 * list, so a redundant call reorders the user's subscriptions for no reason.
 */
class ChannelLinkSubscriptionMirrorTest {

    private val peerTubeId = ContentId.peerTube("tilvids.com", "news").raw
    private val youtubeId = "UCabc"

    private val link = ChannelLink(
        youtubeChannelId = youtubeId,
        youtubeChannelName = "The News",
        peerTubeChannelId = peerTubeId,
        peerTubeChannelName = "News on TILvids",
        createdAt = 1L,
    )

    private fun mirror(
        mirrorEnabled: Boolean = true,
        stored: ChannelLink? = link,
        counterpartSubscribed: Boolean = false,
        subscriptions: SubscriptionRepository = mockk(relaxed = true),
    ): Pair<ChannelLinkSubscriptionMirror, SubscriptionRepository> {
        val store = mockk<ChannelLinkStore>()
        every { store.mirrorSubscriptions } returns flowOf(mirrorEnabled)
        coEvery { store.forYouTube(any()) } returns stored
        coEvery { store.forPeerTube(any()) } returns stored
        every { subscriptions.isSubscribed(any()) } returns flowOf(counterpartSubscribed)
        return ChannelLinkSubscriptionMirror(store, subscriptions) to subscriptions
    }

    @Test
    fun `subscribing to the youtube channel subscribes the peertube one`() = runTest {
        val (mirror, subscriptions) = mirror()

        mirror.mirror(youtubeId, subscribed = true)

        coVerify(exactly = 1) {
            subscriptions.subscribe(match<ChannelSubscription> { it.channelId == peerTubeId })
        }
    }

    @Test
    fun `subscribing to the peertube channel subscribes the youtube one`() = runTest {
        val (mirror, subscriptions) = mirror()

        mirror.mirror(peerTubeId, subscribed = true)

        coVerify(exactly = 1) {
            subscriptions.subscribe(match<ChannelSubscription> { it.channelId == youtubeId })
        }
    }

    @Test
    fun `unsubscribing mirrors too`() = runTest {
        val (mirror, subscriptions) = mirror(counterpartSubscribed = true)

        mirror.mirror(youtubeId, subscribed = false)

        coVerify(exactly = 1) { subscriptions.unsubscribe(peerTubeId) }
    }

    @Test
    fun `nothing happens when the counterpart is already in the wanted state`() = runTest {
        val (subscribeMirror, subscribeRepo) = mirror(counterpartSubscribed = true)
        subscribeMirror.mirror(youtubeId, subscribed = true)
        coVerify(exactly = 0) { subscribeRepo.subscribe(any()) }

        val (unsubscribeMirror, unsubscribeRepo) = mirror(counterpartSubscribed = false)
        unsubscribeMirror.mirror(youtubeId, subscribed = false)
        coVerify(exactly = 0) { unsubscribeRepo.unsubscribe(any()) }
    }

    @Test
    fun `nothing happens when mirroring is switched off`() = runTest {
        val (mirror, subscriptions) = mirror(mirrorEnabled = false)

        mirror.mirror(youtubeId, subscribed = true)

        coVerify(exactly = 0) { subscriptions.subscribe(any()) }
        coVerify(exactly = 0) { subscriptions.unsubscribe(any()) }
    }

    @Test
    fun `nothing happens for an unlinked channel`() = runTest {
        val (mirror, subscriptions) = mirror(stored = null)

        mirror.mirror(youtubeId, subscribed = true)

        coVerify(exactly = 0) { subscriptions.subscribe(any()) }
    }

    @Test
    fun `blank and local ids are ignored`() = runTest {
        val (mirror, subscriptions) = mirror()

        mirror.mirror("", subscribed = true)
        mirror.mirror("local_7", subscribed = true)

        coVerify(exactly = 0) { subscriptions.subscribe(any()) }
    }
}
