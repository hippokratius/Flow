package io.github.aedev.flow.data.source.link

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId
import org.junit.Test

/**
 * When a tap is allowed to open a different video than the one tapped.
 *
 * This is the decision the user cannot see being made, so every way out of it is pinned here: the
 * global switch, the per-video override, and the shapes of video that could never pair anyway.
 */
class PlaybackRedirectDecisionTest {

    private val peerTubeChannel = ContentId.peerTube("framatube.org", "news").raw

    private val links = listOf(
        ChannelLink(
            youtubeChannelId = "UCabc",
            youtubeChannelName = "The News",
            peerTubeChannelId = peerTubeChannel,
        )
    )

    private fun video(
        id: String = "dQw4w9WgXcQ",
        title: String = "The same upload",
        channelId: String = "UCabc",
        duration: Int = 600,
    ) = Video(
        id = id,
        title = title,
        channelName = "The News",
        channelId = channelId,
        thumbnailUrl = "",
        duration = duration,
        viewCount = 0L,
        uploadDate = "",
    )

    private fun decide(
        video: Video,
        links: List<ChannelLink> = this.links,
        redirectEnabled: Boolean = true,
        optedOut: Set<String> = emptySet(),
    ) = peerTubeCounterpartChannelId(video, links, redirectEnabled, optedOut)

    @Test
    fun `a linked channel points at its peertube half`() {
        assertThat(decide(video())).isEqualTo(peerTubeChannel)
    }

    @Test
    fun `an unlinked channel is left alone`() {
        assertThat(decide(video(channelId = "UCzzz"))).isNull()
        assertThat(decide(video(), links = emptyList())).isNull()
    }

    @Test
    fun `the switch turns it off`() {
        assertThat(decide(video(), redirectEnabled = false)).isNull()
    }

    /** The button under the video. Without this the next tap would redirect again. */
    @Test
    fun `a video the user sent back to youtube stays there`() {
        assertThat(decide(video(id = "abc"), optedOut = setOf("abc"))).isNull()
        assertThat(decide(video(id = "abc"), optedOut = setOf("other"))).isNotNull()
    }

    /**
     * One direction only. A PeerTube video is already where the app wants the user to be, and
     * redirecting it would send them back to YouTube — the opposite of the point.
     */
    @Test
    fun `a peertube video is never redirected`() {
        val peerTubeVideo = video(id = ContentId.peerTube("framatube.org", "uuid-1").raw)

        assertThat(decide(peerTubeVideo)).isNull()
    }

    @Test
    fun `local media is never redirected`() {
        assertThat(decide(video(id = "local_7"))).isNull()
    }

    /**
     * Deep links and "previous video" hand [playVideo] a bare id with no title and no duration.
     * `findCounterpart` refuses to pair those, so waiting on a network call for them would be a
     * delay that could never pay off.
     */
    @Test
    fun `a video that could never pair does not pay for a lookup`() {
        assertThat(decide(video(title = ""))).isNull()
        assertThat(decide(video(duration = 0))).isNull()
        assertThat(decide(video(duration = -1))).isNull()
        assertThat(decide(video(channelId = ""))).isNull()
    }

    /** A link is only usable if its PeerTube half is actually addressable. */
    @Test
    fun `a link without a peertube half yields nothing`() {
        val broken = listOf(ChannelLink(youtubeChannelId = "UCabc", peerTubeChannelId = ""))

        assertThat(decide(video(), links = broken)).isNull()
    }
}
