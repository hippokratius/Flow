package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.SourceKind
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * Channel search, used to find the PeerTube half of a link.
 *
 * All-settled like the feed: instances are fanned out and one that is down costs only its own hits.
 * The same creator being mirrored on several instances is normal and those must stay distinct
 * entries — the user is picking *which* instance to follow them on.
 */
class PeerTubeChannelSearchTest {

    private val alive = PeerTubeInstance(id = "1", name = "Alive", url = "https://alive.test")
    private val alsoAlive = PeerTubeInstance(id = "2", name = "Also", url = "https://also.test")
    private val dead = PeerTubeInstance(id = "3", name = "Dead", url = "https://dead.test")

    private fun listDto(vararg names: Pair<String, Long>, host: String) = PTChannelListDto(
        total = names.size,
        data = names.map { (name, followers) ->
            PTChannelDetailDto(
                name = name,
                displayName = "Display $name",
                host = host,
                followersCount = followers,
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
    fun `hits from several instances are merged, most followed first`() = runTest {
        val source = source(listOf(alive, alsoAlive)) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 10L, host = "alive.test")
            coEvery { searchChannels(alsoAlive.url, "news", any()) } returns
                listDto("news" to 900L, host = "also.test")
        }

        val page = source.searchChannels("news")

        assertThat(page.items).hasSize(2)
        assertThat(page.items.first().subscriberCount).isEqualTo(900L)
        assertThat(page.items.all { it.source == SourceKind.PEERTUBE }).isTrue()
    }

    /** Same handle, different instance: two entries, because that is the actual choice on offer. */
    @Test
    fun `the same creator on two instances stays two entries`() = runTest {
        val source = source(listOf(alive, alsoAlive)) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 1L, host = "alive.test")
            coEvery { searchChannels(alsoAlive.url, "news", any()) } returns
                listDto("news" to 1L, host = "also.test")
        }

        val ids = source.searchChannels("news").items.map { it.id }

        assertThat(ids).hasSize(2)
        assertThat(ids.toSet()).hasSize(2)
    }

    @Test
    fun `a failing instance costs only its own hits`() = runTest {
        val source = source(listOf(alive, dead)) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 5L, host = "alive.test")
            coEvery { searchChannels(dead.url, "news", any()) } throws IOException("unreachable")
        }

        val page = source.searchChannels("news")

        assertThat(page.items).hasSize(1)
        assertThat(page.errors).hasSize(1)
        assertThat(page.errors.first().instanceHost).isEqualTo("dead.test")
    }

    @Test
    fun `a blank query is not sent anywhere`() = runTest {
        val source = source(listOf(alive)) { }

        assertThat(source.searchChannels("   ").items).isEmpty()
    }

    @Test
    fun `no configured instances means no results rather than a crash`() = runTest {
        val source = source(emptyList()) { }

        assertThat(source.searchChannels("news").items).isEmpty()
    }
}
