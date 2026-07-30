package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * Channel search, used to find the PeerTube half of a link.
 *
 * Two behaviours carry the feature. All-settled fan-out, so one dead server costs only its own hits;
 * and the search index as an extra lane, without which the search covers a couple of servers out of
 * thousands and routinely finds nothing.
 */
class PeerTubeChannelSearchTest {

    private val alive = PeerTubeInstance(id = "1", name = "Alive", url = "https://alive.test")
    private val alsoAlive = PeerTubeInstance(id = "2", name = "Also", url = "https://also.test")
    private val dead = PeerTubeInstance(id = "3", name = "Dead", url = "https://dead.test")
    private val index = "https://sepiasearch.org"

    private fun listDto(vararg names: Pair<String, Long>, host: String) = PTChannelListDto(
        total = names.size,
        data = names.map { (name, followers) ->
            PTChannelDetailDto(
                name = name,
                displayName = "Display $name",
                host = host,
                url = "https://$host/video-channels/$name",
                followersCount = followers,
            )
        }
    )

    private fun sourceWith(
        instances: List<PeerTubeInstance>,
        indexUrl: String? = index,
        stub: PeerTubeApi.() -> Unit,
    ): Pair<PeerTubeContentSource, PeerTubeApi> {
        val api = mockk<PeerTubeApi>()
        api.stub()
        val preferences = mockk<PeerTubePreferences>()
        coEvery { preferences.currentEnabledInstances() } returns instances
        coEvery { preferences.currentDiscoveryIndexUrl() } returns indexUrl
        return PeerTubeContentSource(api, preferences) to api
    }

    @Test
    fun `hits from several instances are merged, most followed first`() = runTest {
        val (source, _) = sourceWith(listOf(alive, alsoAlive), indexUrl = null) {
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

    /** The point of the index: a channel no configured instance knows about is still found. */
    @Test
    fun `the search index contributes hits of its own`() = runTest {
        val (source, _) = sourceWith(listOf(alive)) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                PTChannelListDto(total = 0, data = emptyList())
            coEvery { searchChannels(index, "news", any()) } returns
                listDto("news" to 7L, host = "framatube.org")
        }

        val page = source.searchChannels("news")

        assertThat(page.items).hasSize(1)
        assertThat(ContentId(page.items.first().id).instanceHost).isEqualTo("framatube.org")
    }

    /**
     * The de-duplication that the origin-host normalisation buys: the index and a mirror both report
     * the same channel, and the user sees one row rather than two identical-looking ones.
     */
    @Test
    fun `a channel reported by two sources appears once`() = runTest {
        val (source, _) = sourceWith(listOf(alive)) {
            // The mirror reports the origin host, which is what makes the ids converge.
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 5L, host = "framatube.org")
            coEvery { searchChannels(index, "news", any()) } returns
                listDto("news" to 5L, host = "framatube.org")
        }

        assertThat(source.searchChannels("news").items).hasSize(1)
    }

    @Test
    fun `two creators of the same name on different hosts stay two entries`() = runTest {
        val (source, _) = sourceWith(listOf(alive), indexUrl = null) {
            coEvery { searchChannels(alive.url, "news", any()) } returns PTChannelListDto(
                total = 2,
                data = listOf(
                    PTChannelDetailDto(name = "news", host = "framatube.org"),
                    PTChannelDetailDto(name = "news", host = "tilvids.com"),
                ),
            )
        }

        assertThat(source.searchChannels("news").items).hasSize(2)
    }

    @Test
    fun `a failing instance costs only its own hits`() = runTest {
        val (source, _) = sourceWith(listOf(alive, dead), indexUrl = null) {
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
    fun `a failing index costs only its own hits`() = runTest {
        val (source, _) = sourceWith(listOf(alive)) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 5L, host = "alive.test")
            coEvery { searchChannels(index, "news", any()) } throws IOException("index down")
        }

        val page = source.searchChannels("news")

        assertThat(page.items).hasSize(1)
        assertThat(page.errors.map { it.instanceHost }).contains("sepiasearch.org")
    }

    /** Search terms must not leave the device once the user has said no. */
    @Test
    fun `the index is not queried when global search is off`() = runTest {
        val (source, api) = sourceWith(listOf(alive), indexUrl = null) {
            coEvery { searchChannels(alive.url, "news", any()) } returns
                listDto("news" to 5L, host = "alive.test")
        }

        source.searchChannels("news")

        coVerify(exactly = 0) { api.searchChannels(index, any(), any()) }
    }

    /** With no instances configured the index alone still answers. */
    @Test
    fun `the index works without any configured instance`() = runTest {
        val (source, _) = sourceWith(emptyList()) {
            coEvery { searchChannels(index, "news", any()) } returns
                listDto("news" to 3L, host = "framatube.org")
        }

        assertThat(source.searchChannels("news").items).hasSize(1)
    }

    @Test
    fun `a blank query is not sent anywhere`() = runTest {
        val (source, api) = sourceWith(listOf(alive)) { }

        assertThat(source.searchChannels("   ").items).isEmpty()
        coVerify(exactly = 0) { api.searchChannels(any(), any(), any()) }
    }

    @Test
    fun `no instances and no index means no results rather than a crash`() = runTest {
        val (source, _) = sourceWith(emptyList(), indexUrl = null) { }

        assertThat(source.searchChannels("news").items).isEmpty()
    }
}
