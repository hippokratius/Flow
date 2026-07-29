package io.github.aedev.flow.fediverse

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The MiAuth contract. Every part of it fails at the instance rather than here when it is wrong,
 * with an error the user cannot act on, so it is pinned.
 */
class MiAuthTest {

    @Test
    fun `authorization url carries name, permissions and callback`() {
        val url = MiAuth.authorizationUrl("https://misskey.example", "abc-123")

        assertThat(url).startsWith("https://misskey.example/miauth/abc-123?")
        assertThat(url).contains("name=TubeHub")
        assertThat(url).contains("permission=${MiAuth.PERMISSIONS}")
        assertThat(url).contains("callback=tubehub%3A%2F%2Fmiauth")
    }

    @Test
    fun `a trailing slash on the instance does not double up`() {
        val url = MiAuth.authorizationUrl("https://misskey.example/", "abc")

        assertThat(url).startsWith("https://misskey.example/miauth/abc")
        assertThat(url).doesNotContain("//miauth")
    }

    /**
     * Every scope is load-bearing: read:account reads the username back after authorisation, and
     * dropping a write scope silently disables the matching action rather than erroring.
     */
    @Test
    fun `the permission set is exactly the one the instance is asked for`() {
        assertThat(MiAuth.PERMISSIONS).isEqualTo(
            "read:account,write:notes,write:reactions,read:following,write:following"
        )
    }

    @Test
    fun `callback url matches the manifest intent filter`() {
        assertThat(MiAuth.CALLBACK_URL).isEqualTo("tubehub://miauth")
    }

    @Test
    fun `a callback yields its session id`() {
        assertThat(MiAuth.sessionFrom("tubehub://miauth?session=abc-123")).isEqualTo("abc-123")
    }

    @Test
    fun `unrelated links are not callbacks`() {
        assertThat(MiAuth.sessionFrom("https://www.youtube.com/watch?v=x&session=abc")).isNull()
        assertThat(MiAuth.sessionFrom("tubehub://other?session=abc")).isNull()
        assertThat(MiAuth.sessionFrom(null)).isNull()
        assertThat(MiAuth.sessionFrom("")).isNull()
    }

    @Test
    fun `a callback without a session is not usable`() {
        assertThat(MiAuth.sessionFrom("tubehub://miauth")).isNull()
        assertThat(MiAuth.sessionFrom("tubehub://miauth?session=")).isNull()
    }

    @Test
    fun `the session parameter is found regardless of position`() {
        assertThat(MiAuth.sessionFrom("tubehub://miauth?foo=1&session=abc")).isEqualTo("abc")
    }
}
