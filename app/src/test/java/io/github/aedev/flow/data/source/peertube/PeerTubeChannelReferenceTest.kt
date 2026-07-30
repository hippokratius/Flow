package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * Parsing a pasted PeerTube channel address.
 *
 * This is the dependable half of manual linking: instance search may be restricted to local content
 * or switched off, so a channel that plainly exists can be unfindable. Pasting must therefore work
 * for every address form a user is likely to have in their clipboard — and must refuse anything it
 * cannot resolve, because a wrong link silently sends them to a stranger's videos.
 */
class PeerTubeChannelReferenceTest {

    @Test
    fun `the short channel url is understood`() {
        val id = PeerTubeChannelReference.parse("https://framatube.org/c/news")

        assertThat(id).isNotNull()
        assertThat(id!!.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("news")
    }

    @Test
    fun `the actor url is understood`() {
        val id = PeerTubeChannelReference.parse("https://framatube.org/video-channels/news")

        assertThat(id!!.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("news")
    }

    /** An account actor is served by the same API path fallback, so it is accepted too. */
    @Test
    fun `account urls are understood`() {
        assertThat(PeerTubeChannelReference.parse("https://framatube.org/a/someone")?.nativeId)
            .isEqualTo("someone")
        assertThat(PeerTubeChannelReference.parse("https://framatube.org/accounts/someone")?.nativeId)
            .isEqualTo("someone")
    }

    @Test
    fun `trailing path segments are ignored`() {
        val id = PeerTubeChannelReference.parse("https://framatube.org/c/news/videos")

        assertThat(id!!.nativeId).isEqualTo("news")
    }

    @Test
    fun `a url can already carry a federated handle`() {
        val id = PeerTubeChannelReference.parse("https://tilvids.com/c/news@framatube.org")

        assertThat(id!!.instanceHost).isEqualTo("tilvids.com")
        assertThat(id.nativeId).isEqualTo("news@framatube.org")
    }

    @Test
    fun `the fediverse handle form is understood`() {
        val id = PeerTubeChannelReference.parse("news@framatube.org")

        assertThat(id!!.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("news")
    }

    @Test
    fun `a leading at sign is tolerated`() {
        val id = PeerTubeChannelReference.parse("@news@framatube.org")

        assertThat(id!!.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("news")
    }

    @Test
    fun `hosts are lowercased and whitespace trimmed`() {
        val id = PeerTubeChannelReference.parse("  news@FramaTube.ORG  ")

        assertThat(id!!.instanceHost).isEqualTo("framatube.org")
    }

    /**
     * A bare handle is refused on purpose. There is no host to query, and picking one of the user's
     * instances would link to whichever happens to have a channel of that name.
     */
    @Test
    fun `a bare handle is refused`() {
        assertThat(PeerTubeChannelReference.parse("news")).isNull()
        assertThat(PeerTubeChannelReference.parse("@news")).isNull()
    }

    @Test
    fun `nonsense is refused`() {
        assertThat(PeerTubeChannelReference.parse("")).isNull()
        assertThat(PeerTubeChannelReference.parse("   ")).isNull()
        assertThat(PeerTubeChannelReference.parse("not a url")).isNull()
        assertThat(PeerTubeChannelReference.parse("news@nohost")).isNull()
        assertThat(PeerTubeChannelReference.parse("a@b@c@d")).isNull()
    }

    /** A YouTube URL must not be mistaken for a PeerTube one. */
    @Test
    fun `unrelated urls are refused`() {
        assertThat(PeerTubeChannelReference.parse("https://www.youtube.com/@someone")).isNull()
        assertThat(PeerTubeChannelReference.parse("https://framatube.org/w/abcdef")).isNull()
        assertThat(PeerTubeChannelReference.parse("https://framatube.org/")).isNull()
        assertThat(PeerTubeChannelReference.parse("https://framatube.org/c/")).isNull()
    }
}
