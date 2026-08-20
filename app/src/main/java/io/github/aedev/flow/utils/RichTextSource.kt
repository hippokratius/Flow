package io.github.aedev.flow.utils

import android.text.Spanned
import android.text.style.URLSpan
import androidx.core.text.HtmlCompat

/**
 * A link the markup of a description or a comment carried, addressed in the decoded text.
 */
data class RichTextLink(val url: String, val start: Int, val end: Int)

/**
 * Text as it should be shown, plus the links its markup carried.
 */
data class RichTextSource(val text: String, val links: List<RichTextLink>)

/**
 * Decodes a description or comment into what the UI renders.
 *
 * The two platforms do not agree on what they send. YouTube's descriptions arrive as HTML — `<br>`
 * for line breaks, anchors for links, entities for anything else — while PeerTube sends the plain
 * text the uploader typed, real newlines and all, and so does InnerTube's watch metadata. Feeding
 * that plain text straight to an HTML parser is what glued a federated description into one
 * paragraph: HTML collapses every run of whitespace, so each newline became a space and the chapter
 * list, the source links and the blank lines between them all ran together.
 *
 * Escaping the newlines as `<br>` before parsing settles it for both without having to guess which
 * kind of text this is: a line break is a line break, whether the uploader typed it or YouTube
 * encoded it.
 */
fun decodeRichText(raw: String): RichTextSource {
    val spanned = HtmlCompat.fromHtml(raw.lineBreaksAsHtml(), HtmlCompat.FROM_HTML_MODE_LEGACY)
    val text = spanned.toString().trimEnd()
    return RichTextSource(text, spanned.linksIn(text))
}

/**
 * The same text with its line breaks expressed the way an HTML parser understands them.
 *
 * Existing `<br>` tags are normalised first so that HTML and plain text converge on one form rather
 * than doubling up.
 */
internal fun String.lineBreaksAsHtml(): String =
    replace(BR_TAG, "\n").replace(NEWLINE, "<br>")

private val BR_TAG = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
private val NEWLINE = Regex("\r\n|\r|\n")

/**
 * The `<a href>` links of this parsed markup, clamped to [text].
 *
 * A relative href is YouTube's way of writing an internal link, and the app has no page to open it
 * on, so it is resolved against the site it came from.
 */
private fun Spanned.linksIn(text: String): List<RichTextLink> =
    getSpans(0, length, URLSpan::class.java).mapNotNull { span ->
        val start = getSpanStart(span).coerceAtMost(text.length)
        val end = getSpanEnd(span).coerceAtMost(text.length)
        if (start >= end) return@mapNotNull null
        val url = span.url ?: return@mapNotNull null
        RichTextLink(
            url = if (url.startsWith("/")) "https://www.youtube.com$url" else url,
            start = start,
            end = end,
        )
    }
