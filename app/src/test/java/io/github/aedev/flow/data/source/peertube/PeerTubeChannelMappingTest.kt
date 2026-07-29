package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * The channel-page mapping, and in particular the handle used to address a channel.
 *
 * The federated case is the one worth guarding: a channel that an instance only mirrors is a remote
 * actor, and PeerTube's API rejects a bare name for it. Getting that wrong is invisible at the
 * mapping layer and shows up as a 404 on the channel page.
 */
class PeerTubeChannelMappingTest {

    private val instance = PeerTubeInstance(
        id = "1",
        name = "TILvids",
        url = "https://tilvids.com"
    )

    @Test
    fun `local channel maps onto the domain model`() {
        val channel = PTChannelDetailDto(
            name = "news",
            displayName = "The News",
            host = "tilvids.com",
            description = "All the news",
            followersCount = 1234,
            avatars = listOf(PTAvatarDto(path = "/a/small.png", width = 48)),
            banners = listOf(PTAvatarDto(path = "/b/wide.png", width = 1920)),
        ).toChannel(instance)

        assertThat(channel.name).isEqualTo("The News")
        assertThat(channel.description).isEqualTo("All the news")
        assertThat(channel.subscriberCount).isEqualTo(1234L)
        assertThat(channel.thumbnailUrl).isEqualTo("https://tilvids.com/a/small.png")
        assertThat(channel.bannerUrl).isEqualTo("https://tilvids.com/b/wide.png")
        assertThat(channel.source).isEqualTo(SourceKind.PEERTUBE)
        assertThat(channel.instanceHost).isEqualTo("tilvids.com")
        assertThat(channel.url).isEqualTo("https://tilvids.com/c/news")

        val id = ContentId(channel.id)
        assertThat(id.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isEqualTo("tilvids.com")
        assertThat(id.nativeId).isEqualTo("news")
    }

    @Test
    fun `mirrored channel keeps its origin host in the handle`() {
        val channel = PTChannelDetailDto(
            name = "news",
            displayName = "The News",
            host = "framatube.org",
        ).toChannel(instance)

        assertThat(ContentId(channel.id).nativeId).isEqualTo("news@framatube.org")
        assertThat(ContentId(channel.id).instanceHost).isEqualTo("tilvids.com")
    }

    @Test
    fun `display name falls back to the handle when the instance omits it`() {
        val channel = PTChannelDetailDto(name = "news", host = "tilvids.com").toChannel(instance)

        assertThat(channel.name).isEqualTo("news")
    }

    @Test
    fun `banner and avatar pick the widest image and fall back to the legacy field`() {
        val channel = PTChannelDetailDto(
            name = "news",
            host = "tilvids.com",
            avatar = PTAvatarDto(path = "/legacy-avatar.png", width = 120),
            banner = PTAvatarDto(path = "/legacy-banner.png", width = 1000),
        ).toChannel(instance)

        assertThat(channel.thumbnailUrl).isEqualTo("https://tilvids.com/legacy-avatar.png")
        assertThat(channel.bannerUrl).isEqualTo("https://tilvids.com/legacy-banner.png")
    }

    @Test
    fun `absolute artwork urls are left alone`() {
        val channel = PTChannelDetailDto(
            name = "news",
            host = "tilvids.com",
            avatars = listOf(PTAvatarDto(path = "https://cdn.example/a.png", width = 500)),
        ).toChannel(instance)

        assertThat(channel.thumbnailUrl).isEqualTo("https://cdn.example/a.png")
    }

    @Test
    fun `missing artwork stays empty instead of becoming a broken link`() {
        val channel = PTChannelDetailDto(name = "news", host = "tilvids.com").toChannel(instance)

        assertThat(channel.thumbnailUrl).isEmpty()
        assertThat(channel.bannerUrl).isEmpty()
    }

    /**
     * The video listing does not report the channel's host, only its actor URL. Reading the host
     * from there is what makes a mirrored channel openable from the home feed at all.
     */
    @Test
    fun `video mapping derives the origin host from the actor url`() {
        val mirrored = video(
            PTChannelDto(
                displayName = "The News",
                name = "news",
                url = "https://framatube.org/video-channels/news",
            )
        ).toVideo(instance)

        assertThat(ContentId(mirrored.channelId).nativeId).isEqualTo("news@framatube.org")
    }

    @Test
    fun `video mapping keeps a bare name for a local channel`() {
        val local = video(
            PTChannelDto(
                displayName = "The News",
                name = "news",
                url = "https://tilvids.com/video-channels/news",
            )
        ).toVideo(instance)

        assertThat(ContentId(local.channelId).nativeId).isEqualTo("news")
    }

    @Test
    fun `video mapping keeps a bare name when the actor url is absent`() {
        val noUrl = video(PTChannelDto(displayName = "The News", name = "news")).toVideo(instance)

        assertThat(ContentId(noUrl.channelId).nativeId).isEqualTo("news")
    }

    @Test
    fun `channel id still falls back to the host without channel data`() {
        val orphan = video(null).toVideo(instance)

        assertThat(ContentId(orphan.channelId).nativeId).isEqualTo("tilvids.com")
    }

    @Test
    fun `an already qualified name is not qualified twice`() {
        val prefixed = video(
            PTChannelDto(
                displayName = "The News",
                name = "news@framatube.org",
                url = "https://framatube.org/video-channels/news",
            )
        ).toVideo(instance)

        assertThat(ContentId(prefixed.channelId).nativeId).isEqualTo("news@framatube.org")
    }

    private fun video(channel: PTChannelDto?) = PTVideoDto(
        uuid = "9b1deb4d-3b7d-4bad",
        name = "A video",
        channel = channel,
    )
}
