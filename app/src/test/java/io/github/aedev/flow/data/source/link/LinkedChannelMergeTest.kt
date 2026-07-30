package io.github.aedev.flow.data.source.link

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * The single list a linked pair of channels turns into.
 *
 * The property that carries the feature: a video published on both platforms appears once, as the
 * PeerTube copy. Everything unpaired from either side survives — nothing may vanish just because the
 * other platform does not have it.
 */
class LinkedChannelMergeTest {

    private fun video(
        id: String,
        title: String,
        duration: Int = 600,
        timestamp: Long = 0L,
        source: SourceKind = SourceKind.YOUTUBE,
    ) = Video(
        id = id,
        title = title,
        channelName = "",
        channelId = "",
        thumbnailUrl = "",
        duration = duration,
        viewCount = 0,
        uploadDate = "",
        timestamp = timestamp,
        source = source,
    )

    private val youtube = listOf(
        video("y1", "Mirrored", 600, timestamp = 300),
        video("y2", "YouTube only", 700, timestamp = 200),
    )

    private val peerTube = listOf(
        video("p1", "mirrored!", 601, timestamp = 290, source = SourceKind.PEERTUBE),
        video("p2", "PeerTube only", 800, timestamp = 100, source = SourceKind.PEERTUBE),
    )

    @Test
    fun `a video on both platforms appears once, as the peertube copy`() {
        val merged = mergeLinkedChannelVideos(youtube, peerTube)

        assertThat(merged.map { it.id }).contains("p1")
        assertThat(merged.map { it.id }).doesNotContain("y1")
    }

    @Test
    fun `unpaired videos from both sides are kept`() {
        val merged = mergeLinkedChannelVideos(youtube, peerTube)

        assertThat(merged.map { it.id }).containsAtLeast("y2", "p2")
        assertThat(merged).hasSize(3)
    }

    @Test
    fun `the list is newest first`() {
        val merged = mergeLinkedChannelVideos(youtube, peerTube)

        assertThat(merged.map { it.timestamp }).isInOrder(compareByDescending<Long> { it })
    }

    @Test
    fun `one empty side leaves the other untouched`() {
        assertThat(mergeLinkedChannelVideos(youtube, emptyList()).map { it.id })
            .containsExactly("y1", "y2").inOrder()
        assertThat(mergeLinkedChannelVideos(emptyList(), peerTube).map { it.id })
            .containsExactly("p1", "p2").inOrder()
        assertThat(mergeLinkedChannelVideos(emptyList(), emptyList())).isEmpty()
    }

    /** The list is keyed by id in a LazyColumn, where a duplicate key crashes rather than looks odd. */
    @Test
    fun `duplicate ids collapse`() {
        val merged = mergeLinkedChannelVideos(
            listOf(video("same", "A", 1, timestamp = 5)),
            listOf(video("same", "B", 9, timestamp = 5, source = SourceKind.PEERTUBE)),
        )

        assertThat(merged).hasSize(1)
    }

    /** Nothing is paired when durations are unknown, so nothing may be dropped either. */
    @Test
    fun `videos with unknown durations are all kept`() {
        val merged = mergeLinkedChannelVideos(
            listOf(video("y", "Same title", duration = 0)),
            listOf(video("p", "Same title", duration = 0, source = SourceKind.PEERTUBE)),
        )

        assertThat(merged.map { it.id }).containsExactly("y", "p")
    }
}
