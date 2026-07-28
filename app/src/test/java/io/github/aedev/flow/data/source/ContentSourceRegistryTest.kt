package io.github.aedev.flow.data.source

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.SearchFilter
import io.github.aedev.flow.data.model.Video
import org.junit.Test

class ContentSourceRegistryTest {

    private class FakeSource(
        override val key: String,
        override val kind: SourceKind,
    ) : ContentSource {
        override suspend fun feed(cursor: SourceCursor?, limit: Int) =
            SourcePage<Video>(emptyList(), null)

        override suspend fun search(query: String, filter: SearchFilter, cursor: SourceCursor?) =
            SourcePage<Video>(emptyList(), null)

        override suspend fun video(id: ContentId): Video? = null
    }

    private val youtube = FakeSource(ContentSourceRegistry.KEY_YOUTUBE, SourceKind.YOUTUBE)
    private val peertube = FakeSource(ContentSourceRegistry.KEY_PEERTUBE, SourceKind.PEERTUBE)

    private fun registry(vararg sources: ContentSource) =
        ContentSourceRegistry(sources.associateBy { it.key })

    @Test
    fun `forId dispatches on the id prefix`() {
        val registry = registry(youtube, peertube)

        assertThat(registry.forId(ContentId("dQw4w9WgXcQ"))).isSameInstanceAs(youtube)
        assertThat(registry.forId(ContentId.peerTube("tilvids.com", "abc")))
            .isSameInstanceAs(peertube)
    }

    @Test
    fun `forId returns null when no source handles the kind`() {
        val registry = registry(youtube)

        assertThat(registry.forId(ContentId.peerTube("tilvids.com", "abc"))).isNull()
        assertThat(registry.forId(ContentId("local_5"))).isNull()
    }

    @Test
    fun `byKey resolves known keys only`() {
        val registry = registry(youtube, peertube)

        assertThat(registry.byKey("peertube")).isSameInstanceAs(peertube)
        assertThat(registry.byKey("nebula")).isNull()
    }

    @Test
    fun `blank order string falls back to the default order`() {
        val registry = registry(youtube, peertube)

        assertThat(registry.deserializeOrder("")).containsExactly("youtube", "peertube").inOrder()
    }

    @Test
    fun `unknown keys are dropped from a stored order`() {
        val registry = registry(youtube, peertube)

        assertThat(registry.deserializeOrder("peertube, nebula ,youtube"))
            .containsExactly("peertube", "youtube").inOrder()
    }

    @Test
    fun `default order only lists sources that are actually bound`() {
        val registry = registry(youtube)

        assertThat(registry.defaultOrder()).containsExactly("youtube")
    }

    @Test
    fun `ordered appends sources missing from the stored order`() {
        val registry = registry(youtube, peertube)

        val ordered = registry.ordered("peertube")

        assertThat(ordered.map { it.key }).containsExactly("peertube", "youtube").inOrder()
    }

    @Test
    fun `order serialization round-trips`() {
        val registry = registry(youtube, peertube)
        val order = listOf("peertube", "youtube")

        assertThat(registry.deserializeOrder(registry.serializeOrder(order))).isEqualTo(order)
    }
}
