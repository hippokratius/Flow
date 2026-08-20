package io.github.aedev.flow.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/**
 * Pure (non-Composable) utility to format comment/reply text with:
 * - HTML entity decoding (&apos;, &quot;, etc.)
 * - Line break handling, for HTML `<br>` and for the plain newlines PeerTube sends alike
 * - Clickable URLs (preserving actual href from <a> tags, not just visible text)
 * - Clickable Timestamps (0:00) — TIMESTAMP takes priority over URL for
 *   YouTube chapter/timestamp links like <a href="...?t=74">1:14</a>
 * - Clickable Hashtags (#hashtag)
 *
 * Call from a Composable wrapped in remember(text) for efficiency.
 */
fun formatRichText(
    text: String,
    primaryColor: Color,
    textColor: Color
): AnnotatedString {
    // Markup, line breaks and entities are decoded in one shared place: comments and descriptions
    // reach this from both platforms and only [decodeRichText] knows what each of them sends.
    val (plainText, links) = decodeRichText(text)
    val htmlLinkRanges: List<IntRange> = links.map { it.start until it.end }

    return buildAnnotatedString {
        append(plainText)
        if (plainText.isNotEmpty()) {
            addStyle(SpanStyle(color = textColor), 0, plainText.length)
        }

        // ── 1. Timestamps (highest priority) ──────────────────────────────────
        val timestampPattern = Regex("""(\d{1,2}:)?\d{1,2}:\d{2}""")
        val annotatedTimestampRanges = mutableListOf<IntRange>()
        for (match in timestampPattern.findAll(plainText)) {
            val s = match.range.first
            val e = match.range.last + 1
            annotatedTimestampRanges += s until e
            addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), s, e)
            addStringAnnotation("TIMESTAMP", match.value, s, e)
        }

        // ── 2. URLs from HTML anchor tags (href preserved) ────────────────────
        for (link in links) {
            val s = link.start
            val e = link.end
            val absoluteUrl = link.url
            if (annotatedTimestampRanges.any { range -> s in range || (s <= range.first && e >= range.last + 1) }) continue
            addStyle(
                SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                s, e
            )
            addStringAnnotation("URL", absoluteUrl, s, e)
            addLink(LinkAnnotation.Url(absoluteUrl), s, e)
        }

        // ── 3. Plain-text URLs (not inside an HTML anchor) ────────────────────
        val urlRegex = Regex("""(?i)\b(?:https?://|www\.)[^\s<]+""")
        for (match in urlRegex.findAll(plainText)) {
            val s = match.range.first
            val displayUrl = match.value.trimEnd('.', ',', ';', ':', '!', ')', ']', '}')
            val e = s + displayUrl.length
            if (displayUrl.isBlank()) continue
            if (htmlLinkRanges.any { s in it } || annotatedTimestampRanges.any { s in it }) continue
            val absoluteUrl = if (displayUrl.startsWith("www.", ignoreCase = true)) {
                "https://$displayUrl"
            } else {
                displayUrl
            }
            addStyle(
                SpanStyle(color = primaryColor, textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium),
                s, e
            )
            addStringAnnotation("URL", absoluteUrl, s, e)
            addLink(LinkAnnotation.Url(absoluteUrl), s, e)
        }

        // ── 4. Hashtags (not inside a link or timestamp) ──────────────────────
        val hashtagRegex = Regex("""#\w+""")
        for (match in hashtagRegex.findAll(plainText)) {
            val s = match.range.first
            val e = match.range.last + 1
            if (htmlLinkRanges.any { s in it } || annotatedTimestampRanges.any { s in it }) continue
            addStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.Bold), s, e)
            addStringAnnotation("HASHTAG", match.value, s, e)
        }
    }
}
