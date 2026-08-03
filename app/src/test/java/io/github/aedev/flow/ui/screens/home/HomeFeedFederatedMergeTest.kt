package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

class HomeFeedFederatedMergeTest {

    private fun yt(id: String) = Video(
        id = id,
        title = "yt-$id",
        channelName = "channel",
        channelId = "UC$id",
        thumbnailUrl = "",
        duration = 100,
        viewCount = 1,
        uploadDate = ""
    )

    private fun pt(uuid: String, host: String = "tilvids.com") = Video(
        id = ContentId.peerTube(host, uuid).raw,
        title = "pt-$uuid",
        channelName = "channel",
        channelId = ContentId.peerTube(host, "chan").raw,
        thumbnailUrl = "",
        duration = 100,
        viewCount = 1,
        uploadDate = "",
        source = SourceKind.PEERTUBE,
        instanceHost = host
    )

    @Test
    fun `no federated videos leaves the feed untouched`() {
        val base = List(5) { yt("y$it") }

        val result = mergeFederatedVideos(base, emptyList())

        assertThat(result.videos).isEqualTo(base)
        assertThat(result.leftover).isEmpty()
    }

    @Test
    fun `federated videos land at the configured cadence`() {
        val base = List(8) { yt("y$it") }
        val federated = listOf(pt("p0"), pt("p1"))

        val result = mergeFederatedVideos(base, federated, everyNth = 4)

        assertThat(result.videos.map { it.id }).containsExactly(
            "y0", "y1", "y2", "y3",
            ContentId.peerTube("tilvids.com", "p0").raw,
            "y4", "y5", "y6", "y7",
            ContentId.peerTube("tilvids.com", "p1").raw,
        ).inOrder()
    }

    @Test
    fun `relative order of each list is preserved`() {
        val base = List(12) { yt("y$it") }
        val federated = List(3) { pt("p$it") }

        val result = mergeFederatedVideos(base, federated, everyNth = 4)

        assertThat(result.videos.filter { it.source == SourceKind.YOUTUBE }.map { it.id })
            .isEqualTo(base.map { it.id })
        assertThat(result.videos.filter { it.source == SourceKind.PEERTUBE }.map { it.id })
            .isEqualTo(federated.map { it.id })
    }

    @Test
    fun `surplus goes to leftover rather than being appended in a clump`() {
        val base = List(4) { yt("y$it") }
        val federated = List(5) { pt("p$it") }

        val result = mergeFederatedVideos(base, federated, everyNth = 4)

        assertThat(result.videos).hasSize(5)
        assertThat(result.leftover.map { it.id })
            .isEqualTo(federated.drop(1).map { it.id })
    }

    /**
     * An empty result would make HomeViewModel treat the feed as failed and replace it with
     * trending, so federated-only must still produce a feed.
     */
    @Test
    fun `federated videos alone still produce a feed`() {
        val federated = List(3) { pt("p$it") }

        val result = mergeFederatedVideos(emptyList(), federated)

        assertThat(result.videos.map { it.id }).isEqualTo(federated.map { it.id })
        assertThat(result.leftover).isEmpty()
    }

    @Test
    fun `videos already in the feed are not duplicated`() {
        val duplicate = pt("p0")
        val base = List(4) { yt("y$it") } + duplicate

        val result = mergeFederatedVideos(base, listOf(duplicate, pt("p1")), everyNth = 4)

        assertThat(result.videos.count { it.id == duplicate.id }).isEqualTo(1)
        assertThat(result.videos.map { it.id }).contains(ContentId.peerTube("tilvids.com", "p1").raw)
    }

    @Test
    fun `duplicates within the federated list itself are dropped`() {
        val base = List(8) { yt("y$it") }

        val result = mergeFederatedVideos(base, listOf(pt("p0"), pt("p0")), everyNth = 4)

        assertThat(result.videos.count { it.source == SourceKind.PEERTUBE }).isEqualTo(1)
        assertThat(result.leftover).isEmpty()
    }

    @Test
    fun `a cadence of zero does not hang or crash`() {
        val base = List(3) { yt("y$it") }

        val result = mergeFederatedVideos(base, listOf(pt("p0")), everyNth = 0)

        assertThat(result.videos).hasSize(4)
    }
}
