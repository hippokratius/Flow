package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * Mapping a channel-search hit, where two different hosts are in play.
 *
 * A hit from SepiaSearch describes a channel on, say, framatube.org while the response came from
 * sepiasearch.org. Getting that backwards produces `peertube_sepiasearch.org_name`, which looks
 * perfectly fine in a list and then 404s the moment the channel is opened — so it is worth pinning.
 */
class PeerTubeDiscoveryMappingTest {

    private val index = "https://sepiasearch.org"

    private fun hit(
        name: String = "news",
        displayName: String = "The News",
        host: String = "framatube.org",
        url: String? = "https://framatube.org/video-channels/news",
        avatars: List<PTAvatarDto> = emptyList(),
        banners: List<PTAvatarDto> = emptyList(),
        followers: Long = 42,
    ) = PTChannelDetailDto(
        name = name,
        displayName = displayName,
        host = host,
        url = url,
        followersCount = followers,
        avatars = avatars,
        banners = banners,
    )

    @Test
    fun `the id belongs to the channel's own host, not the index`() {
        val channel = hit().toDiscoveredChannel(index)

        assertThat(channel).isNotNull()
        val id = ContentId(channel!!.id)
        assertThat(id.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("news")
        assertThat(channel.instanceHost).isEqualTo("framatube.org")
        assertThat(channel.url).isEqualTo("https://framatube.org/c/news")
    }

    /** Artwork is the one thing that *does* belong to whoever answered. */
    @Test
    fun `artwork resolves against the answering index`() {
        val channel = hit(
            avatars = listOf(PTAvatarDto(path = "/lazy-static/avatars/a.jpg", width = 120)),
            banners = listOf(PTAvatarDto(path = "/lazy-static/banners/b.jpg", width = 1920)),
        ).toDiscoveredChannel(index)

        assertThat(channel!!.thumbnailUrl).isEqualTo("https://sepiasearch.org/lazy-static/avatars/a.jpg")
        assertThat(channel.bannerUrl).isEqualTo("https://sepiasearch.org/lazy-static/banners/b.jpg")
    }

    @Test
    fun `absolute artwork urls are left alone`() {
        val channel = hit(
            avatars = listOf(PTAvatarDto(path = "https://cdn.example/a.png", width = 500)),
        ).toDiscoveredChannel(index)

        assertThat(channel!!.thumbnailUrl).isEqualTo("https://cdn.example/a.png")
    }

    @Test
    fun `the host falls back to the actor url`() {
        val channel = hit(host = "").toDiscoveredChannel(index)

        assertThat(ContentId(channel!!.id).instanceHost).isEqualTo("framatube.org")
    }

    @Test
    fun `without any host there is nothing to address`() {
        assertThat(hit(host = "", url = null).toDiscoveredChannel(index)).isNull()
        assertThat(hit(host = "", url = "not a url").toDiscoveredChannel(index)).isNull()
    }

    @Test
    fun `without a name there is nothing to address either`() {
        assertThat(hit(name = "   ").toDiscoveredChannel(index)).isNull()
    }

    @Test
    fun `display name falls back to the handle`() {
        val channel = hit(displayName = "").toDiscoveredChannel(index)

        assertThat(channel!!.name).isEqualTo("news")
    }

    /**
     * The normalisation that makes de-duplication work: whichever route found the channel, the id is
     * the same, because it is built from the channel's own host either way.
     */
    @Test
    fun `the same channel found via index and via a mirror gets one id`() {
        val viaIndex = hit().toDiscoveredChannel(index)
        val viaMirror = hit(
            // A mirror reports the origin host too, and PeerTube may hand back a qualified name.
            name = "news@framatube.org",
        ).toDiscoveredChannel("https://tilvids.com")

        assertThat(viaMirror!!.id).isEqualTo(viaIndex!!.id)
    }

    @Test
    fun `two creators sharing a name keep separate ids`() {
        val a = hit(host = "framatube.org", url = "https://framatube.org/video-channels/news")
            .toDiscoveredChannel(index)
        val b = hit(host = "tilvids.com", url = "https://tilvids.com/video-channels/news")
            .toDiscoveredChannel(index)

        assertThat(a!!.id).isNotEqualTo(b!!.id)
    }

    @Test
    fun `scalar fields carry over`() {
        val channel = hit(followers = 1234).toDiscoveredChannel(index)

        assertThat(channel!!.name).isEqualTo("The News")
        assertThat(channel.subscriberCount).isEqualTo(1234L)
        assertThat(channel.source).isEqualTo(SourceKind.PEERTUBE)
    }
}
