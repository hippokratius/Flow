package io.github.aedev.flow.data.source.peertube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Mapping PeerTube comments onto the model the existing comment UI renders.
 *
 * Until now a federated video simply had no comments: the YouTube extractor was handed a
 * `peertube_…` id, failed, and the failure went to the log. Nothing in the UI said so.
 */
class PeerTubeCommentMappingTest {

    private val instance = "https://tilvids.com"

    private fun comment(
        id: Long = 1,
        text: String = "Nice video",
        createdAt: String? = "2024-03-15T10:30:00.000Z",
        totalReplies: Int = 0,
        isDeleted: Boolean = false,
        account: PTAccountDto? = PTAccountDto(name = "someone", displayName = "Someone"),
    ) = PTCommentDto(
        id = id,
        text = text,
        createdAt = createdAt,
        totalReplies = totalReplies,
        isDeleted = isDeleted,
        account = account,
    )

    @Test
    fun `a comment maps across`() {
        val mapped = comment().toComment(instance)

        assertThat(mapped).isNotNull()
        assertThat(mapped!!.id).isEqualTo("1")
        assertThat(mapped.author).isEqualTo("Someone")
        assertThat(mapped.text).isEqualTo("Nice video")
        assertThat(mapped.publishedTime).isEqualTo("2024-03-15T10:30:00.000Z")
    }

    @Test
    fun `the display name wins, the handle fills in`() {
        assertThat(comment(account = PTAccountDto(name = "someone")).toComment(instance)?.author)
            .isEqualTo("someone")
        assertThat(comment(account = null).toComment(instance)?.author).isEmpty()
    }

    @Test
    fun `the avatar resolves against the instance`() {
        val mapped = comment(
            account = PTAccountDto(
                displayName = "Someone",
                avatars = listOf(PTAvatarDto(path = "/lazy-static/avatars/a.png", width = 120)),
            )
        ).toComment(instance)

        assertThat(mapped!!.authorThumbnail).isEqualTo("https://tilvids.com/lazy-static/avatars/a.png")
    }

    /** A deleted comment is a tombstone in the API and has nothing worth showing. */
    @Test
    fun `deleted and empty comments are dropped`() {
        assertThat(comment(isDeleted = true).toComment(instance)).isNull()
        assertThat(comment(text = "").toComment(instance)).isNull()
        assertThat(comment(text = "<p></p>").toComment(instance)).isNull()
    }

    /**
     * Replies are reported as absent even when the thread has them: the expander in the comment list
     * drives off a NewPipe page token this source cannot produce, so a count would render a control
     * that does nothing.
     */
    @Test
    fun `reply counts are not claimed`() {
        assertThat(comment(totalReplies = 7).toComment(instance)!!.replyCount).isEqualTo(0)
    }

    @Test
    fun `html becomes plain text`() {
        assertThat("<p>Hello</p><p>World</p>".htmlToPlainText()).isEqualTo("Hello\nWorld")
        assertThat("Line<br>break".htmlToPlainText()).isEqualTo("Line\nbreak")
        assertThat("Line<br />break".htmlToPlainText()).isEqualTo("Line\nbreak")
        assertThat("""<a href="https://x">link</a>""".htmlToPlainText()).isEqualTo("link")
    }

    @Test
    fun `entities are decoded`() {
        assertThat("a &amp; b".htmlToPlainText()).isEqualTo("a & b")
        assertThat("&lt;tag&gt;".htmlToPlainText()).isEqualTo("<tag>")
        assertThat("&quot;quoted&quot;".htmlToPlainText()).isEqualTo("\"quoted\"")
        assertThat("it&#39;s".htmlToPlainText()).isEqualTo("it's")
        assertThat("a&nbsp;b".htmlToPlainText()).isEqualTo("a b")
    }

    @Test
    fun `surrounding whitespace is trimmed away`() {
        assertThat("  <p>  padded  </p>  ".htmlToPlainText()).isEqualTo("padded")
    }
}
