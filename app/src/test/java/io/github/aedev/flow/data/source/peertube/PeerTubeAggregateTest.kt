package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.SourceKind
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * Aggregation must be all-settled, not all-or-nothing: one unreachable instance may cost its own
 * videos but must never blank the feed.
 */
class PeerTubeAggregateTest {

    private val alive = PeerTubeInstance(id = "1", name = "Alive", url = "https://alive.test")
    private val alsoAlive = PeerTubeInstance(id = "2", name = "Also", url = "https://also.test")
    private val dead = PeerTubeInstance(id = "3", name = "Dead", url = "https://dead.test")

    private fun listDto(vararg uuids: String, publishedAt: String) = PTVideoListDto(
        total = uuids.size,
        data = uuids.map {
            PTVideoDto(
                uuid = it,
                name = "video-$it",
                publishedAt = publishedAt,
                channel = PTChannelDto(displayName = "c", name = "c"),
            )
        }
    )

    private fun source(
        instances: List<PeerTubeInstance>,
        stub: PeerTubeApi.() -> Unit,
    ): PeerTubeContentSource {
        val api = mockk<PeerTubeApi>()
        api.stub()
        val preferences = mockk<PeerTubePreferences>()
        coEvery { preferences.currentEnabledInstances() } returns instances
        return PeerTubeContentSource(api, preferences)
    }

    @Test
    fun `a failing instance does not take the others down`() = runTest {
        val source = source(listOf(alive, dead, alsoAlive)) {
            coEvery { videos(alive.url, any(), any(), any()) } returns
                listDto("a1", publishedAt = "2024-03-15T10:00:00.000Z")
            coEvery { videos(alsoAlive.url, any(), any(), any()) } returns
                listDto("b1", publishedAt = "2024-03-14T10:00:00.000Z")
            coEvery { videos(dead.url, any(), any(), any()) } throws IOException("unreachable")
        }

        val page = source.feed(limit = 20)

        assertThat(page.items).hasSize(2)
        assertThat(page.items.all { it.source == SourceKind.PEERTUBE }).isTrue()
    }

    @Test
    fun `the failure is reported rather than swallowed`() = runTest {
        val source = source(listOf(alive, dead)) {
            coEvery { videos(alive.url, any(), any(), any()) } returns
                listDto("a1", publishedAt = "2024-03-15T10:00:00.000Z")
            coEvery { videos(dead.url, any(), any(), any()) } throws IOException("HTTP 503")
        }

        val page = source.feed(limit = 20)

        assertThat(page.errors).hasSize(1)
        assertThat(page.errors.single().instanceHost).isEqualTo("dead.test")
        assertThat(page.errors.single().message).contains("503")
    }

    @Test
    fun `every instance failing yields an empty page, not an exception`() = runTest {
        val source = source(listOf(alive, dead)) {
            coEvery { videos(any(), any(), any(), any()) } throws IOException("down")
        }

        val page = source.feed(limit = 20)

        assertThat(page.items).isEmpty()
        assertThat(page.errors).hasSize(2)
        assertThat(page.next).isNull()
    }

    @Test
    fun `results are merged newest first across instances`() = runTest {
        val source = source(listOf(alive, alsoAlive)) {
            coEvery { videos(alive.url, any(), any(), any()) } returns
                listDto("older", publishedAt = "2024-01-01T00:00:00.000Z")
            coEvery { videos(alsoAlive.url, any(), any(), any()) } returns
                listDto("newer", publishedAt = "2024-06-01T00:00:00.000Z")
        }

        val page = source.feed(limit = 20)

        assertThat(page.items.map { it.title }).containsExactly("video-newer", "video-older").inOrder()
    }

    @Test
    fun `no configured instances means no work and no errors`() = runTest {
        val source = source(emptyList()) {}

        val page = source.feed(limit = 20)

        assertThat(page.items).isEmpty()
        assertThat(page.errors).isEmpty()
    }

    @Test
    fun `channel and playlist searches are declined rather than answered wrongly`() = runTest {
        val source = source(listOf(alive)) {}

        val channels = source.search("x", io.github.aedev.flow.data.model.SearchFilter.CHANNELS)

        assertThat(channels.items).isEmpty()
    }
}
