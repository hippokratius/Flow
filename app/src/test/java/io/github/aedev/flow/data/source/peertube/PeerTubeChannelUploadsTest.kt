package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceCursor
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * Paging and error behaviour of the channel page's video list.
 *
 * The end-of-list signal is the interesting part: PeerTube reports a `total`, but instances disagree
 * on whether it counts videos the caller may actually see, so a short page is what ends the list. Get
 * that wrong and the page either stops early or scrolls forever re-requesting nothing.
 */
class PeerTubeChannelUploadsTest {

    private val instance = PeerTubeInstance(id = "1", name = "TILvids", url = "https://tilvids.com")
    private val channelId = ContentId.peerTube("tilvids.com", "news")

    private fun page(count: Int, offset: Int = 0) = PTVideoListDto(
        total = count,
        data = (0 until count).map {
            PTVideoDto(
                uuid = "v${offset + it}",
                name = "video ${offset + it}",
                publishedAt = "2024-03-15T10:00:00.000Z",
                channel = PTChannelDto(displayName = "News", name = "news"),
            )
        }
    )

    private fun source(stub: PeerTubeApi.() -> Unit): PeerTubeContentSource {
        val api = mockk<PeerTubeApi>()
        api.stub()
        val preferences = mockk<PeerTubePreferences>()
        coEvery { preferences.currentEnabledInstances() } returns listOf(instance)
        return PeerTubeContentSource(api, preferences)
    }

    @Test
    fun `a full page offers a cursor one page further on`() = runTest {
        val pageSize = PeerTubeContentSource.CHANNEL_PAGE_SIZE
        val source = source {
            coEvery { channelVideos(instance.url, "news", pageSize, 0) } returns page(pageSize)
        }

        val result = source.channelUploads(channelId)

        assertThat(result.items).hasSize(pageSize)
        assertThat(result.next).isEqualTo(SourceCursor.Offset(pageSize))
    }

    @Test
    fun `a short page ends the list`() = runTest {
        val pageSize = PeerTubeContentSource.CHANNEL_PAGE_SIZE
        val source = source {
            coEvery { channelVideos(instance.url, "news", pageSize, 0) } returns page(3)
        }

        val result = source.channelUploads(channelId)

        assertThat(result.items).hasSize(3)
        assertThat(result.next).isNull()
    }

    @Test
    fun `an empty channel is not an error`() = runTest {
        val pageSize = PeerTubeContentSource.CHANNEL_PAGE_SIZE
        val source = source {
            coEvery { channelVideos(instance.url, "news", pageSize, 0) } returns page(0)
        }

        val result = source.channelUploads(channelId)

        assertThat(result.items).isEmpty()
        assertThat(result.next).isNull()
        assertThat(result.errors).isEmpty()
    }

    @Test
    fun `a cursor is passed on as the request offset`() = runTest {
        val pageSize = PeerTubeContentSource.CHANNEL_PAGE_SIZE
        val source = source {
            coEvery { channelVideos(instance.url, "news", pageSize, pageSize) } returns
                page(pageSize, offset = pageSize)
        }

        val result = source.channelUploads(channelId, SourceCursor.Offset(pageSize))

        assertThat(result.items.first().title).isEqualTo("video $pageSize")
        assertThat(result.next).isEqualTo(SourceCursor.Offset(pageSize * 2))
    }

    @Test
    fun `a mirrored channel is requested by its qualified handle`() = runTest {
        val pageSize = PeerTubeContentSource.CHANNEL_PAGE_SIZE
        val mirrored = ContentId.peerTube("tilvids.com", "news@framatube.org")
        val source = source {
            coEvery {
                channelVideos(instance.url, "news@framatube.org", pageSize, 0)
            } returns page(2)
        }

        assertThat(source.channelUploads(mirrored).items).hasSize(2)
    }

    /** An unreachable instance reports the failure instead of throwing into the ViewModel. */
    @Test
    fun `a failing request is reported as a source error`() = runTest {
        val source = source {
            coEvery { channelVideos(any(), any(), any(), any()) } throws IOException("unreachable")
        }

        val result = source.channelUploads(channelId)

        assertThat(result.items).isEmpty()
        assertThat(result.next).isNull()
        assertThat(result.errors).hasSize(1)
        assertThat(result.errors.first().instanceHost).isEqualTo("tilvids.com")
        assertThat(result.errors.first().message).contains("unreachable")
    }

    @Test
    fun `channel detail maps through to the domain model`() = runTest {
        val source = source {
            coEvery { channelDetail(instance.url, "news") } returns PTChannelDetailDto(
                name = "news",
                displayName = "The News",
                host = "tilvids.com",
                followersCount = 99,
            )
        }

        val channel = source.channel(channelId)

        assertThat(channel).isNotNull()
        assertThat(channel!!.name).isEqualTo("The News")
        assertThat(channel.subscriberCount).isEqualTo(99L)
    }

    /** Metadata is optional: a channel whose detail endpoint fails still lists its videos. */
    @Test
    fun `a failing detail request yields null rather than throwing`() = runTest {
        val source = source {
            coEvery { channelDetail(any(), any()) } throws IOException("gone")
        }

        assertThat(source.channel(channelId)).isNull()
    }

    @Test
    fun `a non federated id is refused`() = runTest {
        val source = source { }

        assertThat(source.channel(ContentId("UCabcdef"))).isNull()
        assertThat(source.channelUploads(ContentId("UCabcdef")).items).isEmpty()
    }
}
