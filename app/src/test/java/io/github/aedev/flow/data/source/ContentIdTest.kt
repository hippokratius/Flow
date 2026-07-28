package io.github.aedev.flow.data.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ContentIdTest {

    @Test
    fun `bare id is treated as YouTube`() {
        val id = ContentId("dQw4w9WgXcQ")

        assertThat(id.kind).isEqualTo(SourceKind.YOUTUBE)
        assertThat(id.nativeId).isEqualTo("dQw4w9WgXcQ")
        assertThat(id.instanceHost).isNull()
    }

    @Test
    fun `peerTube round-trips host and native id`() {
        val id = ContentId.peerTube("tilvids.com", "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")

        assertThat(id.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isEqualTo("tilvids.com")
        assertThat(id.nativeId).isEqualTo("9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d")
    }

    @Test
    fun `host is normalised to lower case`() {
        val id = ContentId.peerTube("TILvids.COM", "abc")

        assertThat(id.instanceHost).isEqualTo("tilvids.com")
        assertThat(id.raw).isEqualTo("peertube_tilvids.com_abc")
    }

    /**
     * PeerTube short UUIDs are base64url and may contain an underscore. Only the *first*
     * underscore after the prefix separates host from id, which is safe because host names
     * cannot contain one.
     */
    @Test
    fun `native id may itself contain the separator`() {
        val id = ContentId.peerTube("framatube.org", "kkGM_x8_pM7U")

        assertThat(id.instanceHost).isEqualTo("framatube.org")
        assertThat(id.nativeId).isEqualTo("kkGM_x8_pM7U")
    }

    @Test
    fun `local media is not hijacked by the peertube branch`() {
        val id = ContentId("local_1234")

        assertThat(id.kind).isEqualTo(SourceKind.LOCAL)
        assertThat(id.nativeId).isEqualTo("local_1234")
        assertThat(id.instanceHost).isNull()
    }

    @Test
    fun `youTube factory leaves the id untouched`() {
        assertThat(ContentId.youTube("abc123").raw).isEqualTo("abc123")
    }

    @Test
    fun `malformed peertube id degrades without throwing`() {
        val id = ContentId("peertube_")

        assertThat(id.kind).isEqualTo(SourceKind.PEERTUBE)
        assertThat(id.instanceHost).isNull()
        assertThat(id.nativeId).isEmpty()
    }

    @Test
    fun `string extension matches direct construction`() {
        assertThat("peertube_a.tld_x".contentId.kind).isEqualTo(SourceKind.PEERTUBE)
    }
}
