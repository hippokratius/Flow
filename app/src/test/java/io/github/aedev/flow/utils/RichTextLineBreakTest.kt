package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The step that decides whether a line break survives the HTML parser: an uploader's newline has to
 * reach it as `<br>`, or the parser collapses it into a space along with every other run of
 * whitespace — which is how a PeerTube description arrived as one unreadable paragraph.
 */
class RichTextLineBreakTest {

    @Test
    fun `plain newlines become breaks the parser keeps`() {
        val chapters = "Kapitel:\n0:00 Intro\n2:52 GLM 5.3"

        assertThat(chapters.lineBreaksAsHtml())
            .isEqualTo("Kapitel:<br>0:00 Intro<br>2:52 GLM 5.3")
    }

    @Test
    fun `a blank line stays a blank line`() {
        assertThat("Quellen:\n\nKapitel:".lineBreaksAsHtml()).isEqualTo("Quellen:<br><br>Kapitel:")
    }

    @Test
    fun `windows line endings count once`() {
        assertThat("erste\r\nzweite".lineBreaksAsHtml()).isEqualTo("erste<br>zweite")
    }

    @Test
    fun `existing break tags are normalised rather than doubled`() {
        assertThat("erste<br>zweite<BR />dritte".lineBreaksAsHtml())
            .isEqualTo("erste<br>zweite<br>dritte")
    }

    @Test
    fun `the rest of the markup is left to the parser`() {
        val html = """Quellen: <a href="https://z.ai/blog/glm-5.3">GLM 5.3</a>&nbsp;"""

        assertThat(html.lineBreaksAsHtml()).isEqualTo(html)
    }
}
