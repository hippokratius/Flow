package io.github.aedev.flow.data.local.entity

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * The watch history stores no source column — the id carries it, and the video the mini player
 * restores has to arrive knowing which backend it came from.
 */
class WatchHistoryEntityTest {

    private fun entry(videoId: String, duration: Long = 5_850_000L) = WatchHistoryEntity(
        videoId = videoId,
        position = 120_000L,
        duration = duration,
        timestamp = 1_755_635_400_000L,
        title = "Die Newcomer-LLMs der letzten Woche im Test",
        thumbnailUrl = "https://tube.test/preview.jpg",
        channelName = "The Morpheus Tutorials",
        channelId = "peertube_tube.test_morpheus",
        isMusic = false,
    )

    @Test
    fun `a federated id restores as a federated video`() {
        val video = entry("peertube_tube.test_9b1deb4d").toVideo()

        assertThat(video.source).isEqualTo(SourceKind.PEERTUBE)
        assertThat(video.instanceHost).isEqualTo("tube.test")
    }

    @Test
    fun `a bare id is still YouTube, with no instance`() {
        val video = entry("dQw4w9WgXcQ").toVideo()

        assertThat(video.source).isEqualTo(SourceKind.YOUTUBE)
        assertThat(video.instanceHost).isNull()
    }

    @Test
    fun `an on-device id restores as local media`() {
        assertThat(entry("local_42").toVideo().source).isEqualTo(SourceKind.LOCAL)
    }

    @Test
    fun `the stored runtime is milliseconds and the video wants seconds`() {
        assertThat(entry("dQw4w9WgXcQ").toVideo().duration).isEqualTo(5850)
        assertThat(entry("dQw4w9WgXcQ", duration = 0L).toVideo().duration).isEqualTo(0)
    }
}
