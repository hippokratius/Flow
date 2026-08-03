package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.ContentId
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

class PeerTubeMappingTest {

    private val instance = PeerTubeInstance(
        id = "1",
        name = "TILvids",
        url = "https://tilvids.com"
    )

    private fun dto(
        uuid: String = "9b1deb4d-3b7d-4bad",
        channel: PTChannelDto? = PTChannelDto(displayName = "Some Channel", name = "somechannel"),
        thumbnailPath: String? = "/lazy-static/thumbnails/x.jpg",
        previewPath: String? = null,
    ) = PTVideoDto(
        uuid = uuid,
        name = "A video",
        description = "desc",
        thumbnailPath = thumbnailPath,
        previewPath = previewPath,
        duration = 630,
        publishedAt = "2024-03-15T10:30:00.000Z",
        views = 4242,
        channel = channel,
    )

    @Test
    fun `id encodes the source and instance`() {
        val video = dto().toVideo(instance)

        val id = ContentId(video.id)
        assertThat(id.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isEqualTo("tilvids.com")
        assertThat(id.nativeId).isEqualTo("9b1deb4d-3b7d-4bad")
        assertThat(video.source).isEqualTo(SourceKind.PEERTUBE)
        assertThat(video.instanceHost).isEqualTo("tilvids.com")
    }

    @Test
    fun `thumbnail path is resolved against the instance`() {
        val video = dto().toVideo(instance)

        assertThat(video.thumbnailUrl)
            .isEqualTo("https://tilvids.com/lazy-static/thumbnails/x.jpg")
    }

    @Test
    fun `preview image wins over thumbnail when both are present`() {
        val video = dto(previewPath = "/static/previews/x.jpg").toVideo(instance)

        assertThat(video.thumbnailUrl).isEqualTo("https://tilvids.com/static/previews/x.jpg")
    }

    @Test
    fun `missing artwork yields an empty url rather than a broken one`() {
        val video = dto(thumbnailPath = null).toVideo(instance)

        assertThat(video.thumbnailUrl).isEmpty()
    }

    /**
     * A blank channel id would be catastrophic rather than cosmetic: FlowNeuroEngine filters
     * candidates against the user's blocked-channel set, so one blocked empty string would hide
     * every federated video at once.
     */
    @Test
    fun `channel id is never blank even without channel data`() {
        val withChannel = dto().toVideo(instance)
        val withoutChannel = dto(channel = null).toVideo(instance)

        assertThat(withChannel.channelId).isNotEmpty()
        assertThat(withoutChannel.channelId).isNotEmpty()
        assertThat(ContentId(withoutChannel.channelId).kind).isEqualTo(SourceKind.PEERTUBE)
    }

    @Test
    fun `channel id distinguishes channels on the same instance`() {
        val a = dto(channel = PTChannelDto(displayName = "A", name = "a")).toVideo(instance)
        val b = dto(channel = PTChannelDto(displayName = "B", name = "b")).toVideo(instance)

        assertThat(a.channelId).isNotEqualTo(b.channelId)
    }

    @Test
    fun `publish date is carried over so the app can format it`() {
        val video = dto().toVideo(instance)

        assertThat(video.uploadDate).isEqualTo("2024-03-15T10:30:00.000Z")
        // Parsed for sorting; the exact value depends on the JVM zone, so only sanity-check it.
        assertThat(video.timestamp).isGreaterThan(0L)
    }

    @Test
    fun `scalar fields map across`() {
        val video = dto().toVideo(instance)

        assertThat(video.title).isEqualTo("A video")
        assertThat(video.duration).isEqualTo(630)
        assertThat(video.viewCount).isEqualTo(4242L)
        assertThat(video.channelName).isEqualTo("Some Channel")
        assertThat(video.description).isEqualTo("desc")
    }

    @Test
    fun `avatar picks the largest available and resolves it`() {
        val channel = PTChannelDto(
            displayName = "C",
            name = "c",
            avatars = listOf(
                PTAvatarDto(path = "/small.png", width = 48),
                PTAvatarDto(path = "/large.png", width = 600),
            )
        )

        val video = dto(channel = channel).toVideo(instance)

        assertThat(video.channelThumbnailUrl).isEqualTo("https://tilvids.com/large.png")
    }
}
