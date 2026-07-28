package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeerTubeInstanceNormalizationTest {

    @Test
    fun `a bare host gets https`() {
        assertThat(PeerTubeInstances.normalizeUrl("framatube.org"))
            .isEqualTo("https://framatube.org")
    }

    @Test
    fun `an explicit scheme is respected`() {
        assertThat(PeerTubeInstances.normalizeUrl("http://local.test"))
            .isEqualTo("http://local.test")
    }

    @Test
    fun `trailing slashes and whitespace are trimmed`() {
        assertThat(PeerTubeInstances.normalizeUrl("  https://tilvids.com/  "))
            .isEqualTo("https://tilvids.com")
    }

    @Test
    fun `blank and scheme-only input is rejected`() {
        assertThat(PeerTubeInstances.normalizeUrl("")).isNull()
        assertThat(PeerTubeInstances.normalizeUrl("   ")).isNull()
        assertThat(PeerTubeInstances.normalizeUrl("https://")).isNull()
    }

    @Test
    fun `an empty name falls back to the host`() {
        assertThat(PeerTubeInstances.deriveName("", "https://framatube.org"))
            .isEqualTo("framatube.org")
        assertThat(PeerTubeInstances.deriveName("  Framatube ", "https://framatube.org"))
            .isEqualTo("Framatube")
    }

    @Test
    fun `adding appends a normalized entry`() {
        val result = PeerTubeInstances.add(emptyList(), name = "", rawUrl = "tilvids.com/", id = "x")

        assertThat(result).hasSize(1)
        assertThat(result.single().url).isEqualTo("https://tilvids.com")
        assertThat(result.single().name).isEqualTo("tilvids.com")
        assertThat(result.single().enabled).isTrue()
    }

    /** The same server added twice would duplicate every one of its videos in the feed. */
    @Test
    fun `duplicates are rejected case-insensitively`() {
        val first = PeerTubeInstances.add(emptyList(), "", "https://tilvids.com", "a")
        val second = PeerTubeInstances.add(first, "", "https://TILVIDS.com/", "b")

        assertThat(second).isEqualTo(first)
    }

    @Test
    fun `invalid input leaves the list untouched`() {
        val current = PeerTubeInstances.add(emptyList(), "", "https://tilvids.com", "a")

        assertThat(PeerTubeInstances.add(current, "", "   ", "b")).isEqualTo(current)
    }

    @Test
    fun `removing drops only the matching entry`() {
        val list = PeerTubeInstances.add(
            PeerTubeInstances.add(emptyList(), "", "https://a.test", "a"),
            "", "https://b.test", "b"
        )

        val result = PeerTubeInstances.remove(list, "a")

        assertThat(result.map { it.id }).containsExactly("b")
    }

    @Test
    fun `toggling changes only the matching entry`() {
        val list = PeerTubeInstances.add(
            PeerTubeInstances.add(emptyList(), "", "https://a.test", "a"),
            "", "https://b.test", "b"
        )

        val result = PeerTubeInstances.setEnabled(list, "a", enabled = false)

        assertThat(result.first { it.id == "a" }.enabled).isFalse()
        assertThat(result.first { it.id == "b" }.enabled).isTrue()
    }

    @Test
    fun `host is exposed for building content ids`() {
        val instance = PeerTubeInstance(id = "1", name = "n", url = "https://tilvids.com")

        assertThat(instance.host).isEqualTo("tilvids.com")
    }
}
