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

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_700_000_000_000L
    }

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

    /**
     * The Morpheus Tutorials translate their titles: "I prefer Claude Opus 5 over Fable" on YouTube,
     * "Claude Opus 5 gefällt mir besser als Fable" on their own instance. Same 22:06, same day, and
     * nothing in common as text — the runtime is the only evidence there is.
     */
    @Test
    fun `a translated title pairs on the runtime alone`() {
        val candidates = listOf(
            video("p1", "Claude Opus 5 gefällt mir besser als Fable", 1326, NOW - 5 * DAY, SourceKind.PEERTUBE),
            video("p2", "Das ist der Deepseek 2.0 Moment", 2522, NOW - 7 * DAY, SourceKind.PEERTUBE),
        )

        assertThat(
            findCounterpart(video("y", "I prefer Claude Opus 5 over Fable", 1326, NOW - 5 * DAY), candidates)?.id
        ).isEqualTo("p1")
    }

    /**
     * The guard that makes the runtime-only stage defensible. With no title to tell them apart, two
     * videos of the same length are indistinguishable — so nothing is returned rather than a guess.
     */
    @Test
    fun `two candidates of the same length are not guessed between`() {
        val candidates = listOf(
            video("p_a", "Folge A", 1326, NOW - 3 * DAY, SourceKind.PEERTUBE),
            video("p_b", "Folge B", 1326, NOW - 4 * DAY, SourceKind.PEERTUBE),
        )

        assertThat(findCounterpart(video("y", "Something else", 1326, NOW), candidates)).isNull()
    }

    @Test
    fun `the runtime-only stage is bounded in time and length`() {
        val yt = video("y", "Something else", 1326, NOW)

        assertThat(
            findCounterpart(yt, listOf(video("p", "Anderer Titel", 1326, NOW - 365 * DAY, SourceKind.PEERTUBE)))
        ).isNull()
        assertThat(
            findCounterpart(yt, listOf(video("p", "Anderer Titel", 1400, NOW, SourceKind.PEERTUBE)))
        ).isNull()
        // One percent of an hour is still half a minute.
        assertThat(
            findCounterpart(
                video("y", "Something else", 3600, NOW),
                listOf(video("p", "Anderer Titel", 3630, NOW, SourceKind.PEERTUBE)),
            )?.id
        ).isEqualTo("p")
    }

    /** An unknown runtime or timestamp is not evidence of anything. */
    @Test
    fun `the runtime-only stage needs both facts`() {
        assertThat(
            findCounterpart(
                video("y", "Something else", 1326, 0L),
                listOf(video("p", "Anderer Titel", 1326, 0L, SourceKind.PEERTUBE)),
            )
        ).isNull()
        assertThat(
            findCounterpart(
                video("y", "Something else", 0, NOW),
                listOf(video("p", "Anderer Titel", 0, NOW, SourceKind.PEERTUBE)),
            )
        ).isNull()
    }

    @Test
    fun `a title match beats a runtime match`() {
        val candidates = listOf(
            video("p_runtime", "Ganz anderer Titel", 1326, NOW, SourceKind.PEERTUBE),
            video("p_title", "The Show", 1200, NOW, SourceKind.PEERTUBE),
        )

        assertThat(findCounterpart(video("y", "The Show", 1326, NOW), candidates)?.id)
            .isEqualTo("p_title")
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
