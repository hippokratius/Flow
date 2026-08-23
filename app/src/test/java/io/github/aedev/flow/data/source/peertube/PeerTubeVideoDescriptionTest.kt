package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

/**
 * PeerTube serves a video's description cut to 250 characters and keeps the rest behind its own
 * endpoint. A description that ends mid-link is what the player showed for every federated video
 * until this was fetched.
 */
class PeerTubeVideoDescriptionTest {

    private val instance = PeerTubeInstance(id = "1", name = "Morpheus", url = "https://tube.test")
    private val id = ContentId.peerTube("tube.test", "9b1deb4d")

    private fun source(stub: PeerTubeApi.() -> Unit): Pair<PeerTubeContentSource, PeerTubeApi> {
        val api = mockk<PeerTubeApi>()
        api.stub()
        val preferences = mockk<PeerTubePreferences>()
        coEvery { preferences.currentEnabledInstances() } returns listOf(instance)
        return PeerTubeContentSource(api, preferences) to api
    }

    private fun detail(description: String?) = PTVideoDto(
        uuid = "9b1deb4d",
        name = "Die Newcomer-LLMs der letzten Woche im Test",
        description = description,
        duration = 5850,
        publishedAt = "2026-08-19T20:30:00.000Z",
        channel = PTChannelDto(displayName = "The Morpheus Tutorials", name = "morpheus"),
    )

    @Test
    fun `a truncated description is replaced by the whole text`() = runTest {
        val (source, _) = source {
            coEvery { videoDetail(instance.url, "9b1deb4d") } returns detail("Quellen: https://api-docs.d...")
            coEvery { videoDescription(instance.url, "9b1deb4d") } returns
                PTVideoDescriptionDto("Quellen: https://api-docs.deepseek.com\n\nKapitel:\n0:00 Intro")
        }

        val video = source.video(id)

        assertThat(video?.description)
            .isEqualTo("Quellen: https://api-docs.deepseek.com\n\nKapitel:\n0:00 Intro")
    }

    @Test
    fun `a complete description costs no second request`() = runTest {
        val (source, api) = source {
            coEvery { videoDetail(instance.url, "9b1deb4d") } returns detail("Kurz und vollständig.")
        }

        val video = source.video(id)

        assertThat(video?.description).isEqualTo("Kurz und vollständig.")
        coVerify(exactly = 0) { api.videoDescription(any(), any()) }
    }

    @Test
    fun `the stub survives when the description endpoint fails`() = runTest {
        val (source, _) = source {
            coEvery { videoDetail(instance.url, "9b1deb4d") } returns detail("Quellen: https://api-docs.d...")
            coEvery { videoDescription(instance.url, "9b1deb4d") } throws IOException("HTTP 404")
        }

        val video = source.video(id)

        assertThat(video?.description).isEqualTo("Quellen: https://api-docs.d...")
    }

    @Test
    fun `a blank answer is not an improvement on the stub`() = runTest {
        val (source, _) = source {
            coEvery { videoDetail(instance.url, "9b1deb4d") } returns detail("Quellen: https://api-docs.d...")
            coEvery { videoDescription(instance.url, "9b1deb4d") } returns PTVideoDescriptionDto("")
        }

        val video = source.video(id)

        assertThat(video?.description).isEqualTo("Quellen: https://api-docs.d...")
    }
}
