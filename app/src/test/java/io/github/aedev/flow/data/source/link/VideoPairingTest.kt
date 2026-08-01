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

    /**
     * The case this rule exists for. c't 3003 publishes the same episode as 14:43 on
     * peertube.heise.de and 15:48 on YouTube — different intros and outros, a minute apart. The
     * original five-second tolerance rejected it, and with it every other real mirror.
     */
    @Test
    fun `platform versions with different intros still pair`() {
        assertThat(
            isSameUpload(
                video("y", "Genialer als gedacht!", 948),
                video("p", "Genialer als gedacht!", 883),
            )
        ).isTrue()
    }

    /**
     * The floor only bites on short videos: from about 7:30 upwards the fraction is the larger of
     * the two. Below that, a percentage would be stricter than the minute-scale differences that
     * intros and outros actually cause.
     */
    @Test
    fun `short videos get a ninety second floor`() {
        assertThat(isSameUpload(video("y", "A", 100), video("p", "A", 190))).isTrue()
        assertThat(isSameUpload(video("y", "A", 100), video("p", "A", 191))).isFalse()
    }

    /** For long videos the fraction takes over, because a fixed 90 s is noise in an hour. */
    @Test
    fun `the tolerance grows with the runtime`() {
        assertThat(isSameUpload(video("y", "A", 3600), video("p", "A", 3000))).isTrue()
        assertThat(isSameUpload(video("y", "A", 3600), video("p", "A", 2800))).isFalse()
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 730))).isTrue()
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 800))).isFalse()
    }

    /** Same title, wildly different length — a trailer is not the film. */
    @Test
    fun `a very different runtime still does not pair`() {
        assertThat(isSameUpload(video("y", "A", 1200), video("p", "A", 600))).isFalse()
    }

    /**
     * PeerTube reports 0 while a video is still transcoding. Rejecting that made a freshly imported
     * video unpairable for exactly as long as it was new — which is when someone looks at it. The
     * title carries the match on its own.
     */
    @Test
    fun `an unknown duration pairs on the title alone`() {
        assertThat(isSameUpload(video("y", "A", 600), video("p", "A", 0))).isTrue()
        assertThat(isSameUpload(video("y", "A", 0), video("p", "A", 0))).isTrue()
        assertThat(isSameUpload(video("y", "A", -1), video("p", "B", -1))).isFalse()
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

    /**
     * With the runtime relaxed to a sanity check, a channel that reuses a title across episodes can
     * produce several candidates. "Whichever the API returned first" is not an answer.
     */
    @Test
    fun `the closest runtime wins when several candidates share a title`() {
        val candidates = listOf(
            video("p_far", "The News", 1000, source = SourceKind.PEERTUBE),
            video("p_near", "The News", 890, source = SourceKind.PEERTUBE),
        )

        assertThat(findCounterpart(video("y", "The News", 900), candidates)?.id).isEqualTo("p_near")
    }

    /** With nothing to tell the runtimes apart, the nearest publication date decides. */
    @Test
    fun `the nearest upload date breaks a tie`() {
        val candidates = listOf(
            video("p_old", "The News", 0, timestamp = 1_000L, source = SourceKind.PEERTUBE),
            video("p_new", "The News", 0, timestamp = 9_000L, source = SourceKind.PEERTUBE),
        )

        assertThat(
            findCounterpart(video("y", "The News", 0, timestamp = 9_500L), candidates)?.id
        ).isEqualTo("p_new")
    }
}
