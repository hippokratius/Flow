package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * A federated video is only ever as complete as whatever put it into the player, and the player has
 * no extraction of its own to fall back on. These are the cases that decide whether it asks the
 * source — and the one that started it: a video restored from the watch history, which used to be
 * declared complete on the strength of a title alone.
 */
class FederatedMetadataPolicyTest {

    private val complete = Video(
        id = "peertube_tube.test_9b1deb4d",
        title = "Die Newcomer-LLMs der letzten Woche im Test",
        channelName = "The Morpheus Tutorials",
        channelId = "peertube_tube.test_morpheus",
        thumbnailUrl = "https://tube.test/preview.jpg",
        duration = 5850,
        viewCount = 39,
        likeCount = 4,
        uploadDate = "2026-08-19T20:30:00.000Z",
        timestamp = 1_755_635_400_000L,
        description = "Kapitel:\n0:00 Intro",
        source = SourceKind.PEERTUBE,
        instanceHost = "tube.test",
    )

    /** What [io.github.aedev.flow.data.local.entity.WatchHistoryEntity.toVideo] can reconstruct. */
    private val fromHistory = Video(
        id = complete.id,
        title = complete.title,
        channelName = complete.channelName,
        channelId = complete.channelId,
        thumbnailUrl = complete.thumbnailUrl,
        duration = complete.duration,
        viewCount = 0,
        uploadDate = "",
        timestamp = 1_777_000_000_000L, // the Video default: "now", which renders as "just now"
        source = SourceKind.PEERTUBE,
        instanceHost = "tube.test",
    )

    @Test
    fun `a video restored from the watch history needs its detail`() {
        assertThat(FederatedMetadataPolicy.needsDetail(fromHistory)).isTrue()
    }

    @Test
    fun `so does a listing row whose description was cut short`() {
        val fromFeed = complete.copy(description = "Quellen: https://api-docs.d...")

        assertThat(FederatedMetadataPolicy.needsDetail(fromFeed)).isTrue()
    }

    @Test
    fun `a video that has everything does not`() {
        assertThat(FederatedMetadataPolicy.needsDetail(complete)).isFalse()
    }

    private fun shouldFetch(
        video: Video? = fromHistory,
        isEnriched: Boolean = false,
        isInFlight: Boolean = false,
        failedAttempts: Int = 0,
    ) = FederatedMetadataPolicy.shouldFetchDetail(video, isEnriched, isInFlight, failedAttempts)

    @Test
    fun `an incomplete video is fetched`() {
        assertThat(shouldFetch()).isTrue()
    }

    @Test
    fun `a video answered for once is not asked again`() {
        // Still incomplete by the field test — a fresh upload with no views and no description never
        // stops being — so only the flag can end this.
        assertThat(FederatedMetadataPolicy.needsDetail(fromHistory)).isTrue()
        assertThat(shouldFetch(isEnriched = true)).isFalse()
    }

    @Test
    fun `a fetch already running is not doubled`() {
        assertThat(shouldFetch(isInFlight = true)).isFalse()
    }

    @Test
    fun `failures are bounded`() {
        assertThat(shouldFetch(failedAttempts = FederatedMetadataPolicy.MAX_ATTEMPTS - 1)).isTrue()
        assertThat(shouldFetch(failedAttempts = FederatedMetadataPolicy.MAX_ATTEMPTS)).isFalse()
    }

    @Test
    fun `nothing to enrich without a video`() {
        assertThat(shouldFetch(video = null)).isFalse()
    }

    @Test
    fun `the detail fills in what the history row never had`() {
        val merged = FederatedMetadataPolicy.merge(fromHistory, complete)

        assertThat(merged.viewCount).isEqualTo(39L)
        assertThat(merged.likeCount).isEqualTo(4L)
        assertThat(merged.uploadDate).isEqualTo("2026-08-19T20:30:00.000Z")
        assertThat(merged.timestamp).isEqualTo(1_755_635_400_000L)
        assertThat(merged.description).isEqualTo("Kapitel:\n0:00 Intro")
    }

    @Test
    fun `what the user is already looking at is not replaced`() {
        val differentlyTitled = complete.copy(
            title = "A translated title",
            channelName = "Another name",
            thumbnailUrl = "https://tube.test/other.jpg",
        )

        val merged = FederatedMetadataPolicy.merge(complete, differentlyTitled)

        assertThat(merged.title).isEqualTo(complete.title)
        assertThat(merged.channelName).isEqualTo(complete.channelName)
        assertThat(merged.thumbnailUrl).isEqualTo(complete.thumbnailUrl)
    }

    @Test
    fun `a video with no description does not lose the one it has`() {
        val merged = FederatedMetadataPolicy.merge(complete, complete.copy(description = ""))

        assertThat(merged.description).isEqualTo(complete.description)
    }

    @Test
    fun `an unknown publication date takes neither the date nor the timestamp`() {
        // PeerTubeMapper falls back to "now" when the instance reports no publishedAt, which is the
        // same wrong value the history row already carries.
        val undated = complete.copy(uploadDate = "", timestamp = 1_777_000_000_000L)

        val merged = FederatedMetadataPolicy.merge(fromHistory, undated)

        assertThat(merged.uploadDate).isEmpty()
        assertThat(merged.timestamp).isEqualTo(fromHistory.timestamp)
    }

    @Test
    fun `merging twice changes nothing the second time`() {
        val once = FederatedMetadataPolicy.merge(fromHistory, complete)

        assertThat(FederatedMetadataPolicy.merge(once, complete)).isEqualTo(once)
    }
}
