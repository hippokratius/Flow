package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Runs once per avatar URL, per card, on every composition of the feed — the pattern it uses is now
 * compiled once instead of per call, so this pins the behaviour that must not change with it.
 */
class AvatarImageIdentityTest {

    @Test
    fun `the size suffix is what distinguishes two URLs for the same avatar`() {
        val small = "https://yt3.ggpht.com/abc=s88-c-k-c0x00ffffff-no-rj"
        val large = "https://yt3.ggpht.com/abc=s800-c-k-c0x00ffffff-no-rj"

        assertThat(small.avatarImageIdentityKey()).isEqualTo(large.avatarImageIdentityKey())
    }

    @Test
    fun `query parameters are dropped`() {
        assertThat("https://host/avatar.jpg?sqp=abc".avatarImageIdentityKey())
            .isEqualTo("https://host/avatar.jpg")
    }

    @Test
    fun `different avatars keep different keys`() {
        assertThat("https://yt3.ggpht.com/abc=s88".avatarImageIdentityKey())
            .isNotEqualTo("https://yt3.ggpht.com/xyz=s88".avatarImageIdentityKey())
    }

    @Test
    fun `nothing to identify is the empty key`() {
        assertThat(null.avatarImageIdentityKey()).isEmpty()
        assertThat("".avatarImageIdentityKey()).isEmpty()
    }
}
