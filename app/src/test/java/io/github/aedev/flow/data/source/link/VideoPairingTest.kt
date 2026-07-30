package io.github.aedev.flow.data.source.link

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.SourceKind
import org.junit.Test

/**
 * Deciding whether two uploads are the same video.
 *
 * The riskiest logic in the whole linking feature, because a false positive is not cosmetic: it plays
 * a different video than the one tapped, and hides a video from the shared channel list. The tests
 * lean hard on the cases that should **not** pair.
 */
class VideoPairingTest {

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

    @Test
    fun `case, punctuation and spacing do not matter`() {
        assertThat(normalizeTitle("The NEWS")).isEqualTo(normalizeTitle("the news"))
        assertThat(normalizeTitle("Hello, World!")).isEqualTo("hello world")
        assertThat(normalizeTitle("a   b")).isEqualTo("a b")
    }

    /** Decorations routinely survive on one platform and get dropped on the other. */
    @Test
    fun `bracketed decorations are dropped`() {
        assertThat(normalizeTitle("Song [4K] (Official Video)")).isEqualTo("song")
    }

    @Test
    fun `emoji are dropped but digits are kept`() {
        assertThat(normalizeTitle("News 🔥 Today")).isEqualTo("news today")
        assertThat(normalizeTitle("Part 2")).isEqualTo("part 2")
    }

    @Test
    fun `the same upload on both platforms pairs`() {
        assertThat(isSameUpload(video("y", "The News", 600), video("p", "the news!", 602))).isTrue()
    }

    @Test
    fun `the duration tolerance is five seconds`() {
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 605))).isTrue()
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 606))).isFalse()
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 660))).isFalse()
    }

    /**
     * PeerTube reports 0 while a video is still transcoding. Treating 0 == 0 as a match would pair
     * every transcoding video with every other one.
     */
    @Test
    fun `an unknown duration never pairs`() {
        assertThat(isSameUpload(video("y", "A", 0), video("p", "A", 0))).isFalse()
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 0))).isFalse()
        assertThat(isSameUpload(video("y", "A", -1), video("p", "A", -1))).isFalse()
    }

    @Test
    fun `different titles of the same length do not pair`() {
        assertThat(isSameUpload(video("y", "Part 1"), video("p", "Part 2"))).isFalse()
    }

    /** Substring matching would equate a video with a reaction to it. */
    @Test
    fun `a longer title containing the shorter one does not pair`() {
        assertThat(isSameUpload(video("y", "Part 1"), video("p", "Part 1 Reaction"))).isFalse()
    }

    @Test
    fun `titles that normalise to nothing do not pair`() {
        assertThat(isSameUpload(video("y", "!!!"), video("p", "???"))).isFalse()
    }

    @Test
    fun `the counterpart is found among candidates`() {
        val candidates = listOf(
            video("p1", "Other", source = SourceKind.PEERTUBE),
            video("p2", "The News", source = SourceKind.PEERTUBE),
        )

        assertThat(findCounterpart(video("y", "the news", 601), candidates)?.id).isEqualTo("p2")
        assertThat(findCounterpart(video("y", "Nothing else", 601), candidates)).isNull()
        assertThat(findCounterpart(video("y", "A"), emptyList())).isNull()
    }
}
