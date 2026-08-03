package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Reading "vor 5 Tagen" as well as "5 days ago".
 *
 * This parser was English-only in six separate copies, which was safe only while the extractor was
 * pinned to English. Now that it answers in the user's language, an unparsed date falls through to
 * "right now" — and feed ordering is built on these timestamps, so the whole feed would look freshly
 * uploaded.
 */
class RelativeUploadDateTest {

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY = 86_400_000L
    }

    private fun parse(text: String?) = parseRelativeUploadDateMillis(text, NOW)

    @Test
    fun `english keeps working`() {
        assertThat(parse("5 days ago")).isEqualTo(NOW - 5 * DAY)
        assertThat(parse("1 week ago")).isEqualTo(NOW - 7 * DAY)
        assertThat(parse("2 months ago")).isEqualTo(NOW - 60 * DAY)
        assertThat(parse("3 years ago")).isEqualTo(NOW - 3 * 365 * DAY)
        assertThat(parse("14 hours ago")).isEqualTo(NOW - 14 * 3_600_000L)
        assertThat(parse("30 minutes ago")).isEqualTo(NOW - 30 * 60_000L)
        assertThat(parse("45 seconds ago")).isEqualTo(NOW - 45_000L)
        assertThat(parse("Yesterday")).isEqualTo(NOW - DAY)
        assertThat(parse("just now")).isEqualTo(NOW)
    }

    @Test
    fun `german is understood too`() {
        assertThat(parse("vor 5 Tagen")).isEqualTo(NOW - 5 * DAY)
        assertThat(parse("vor 1 Woche")).isEqualTo(NOW - 7 * DAY)
        assertThat(parse("vor 2 Monaten")).isEqualTo(NOW - 60 * DAY)
        assertThat(parse("vor 3 Jahren")).isEqualTo(NOW - 3 * 365 * DAY)
        assertThat(parse("vor 14 Stunden")).isEqualTo(NOW - 14 * 3_600_000L)
        assertThat(parse("vor 30 Minuten")).isEqualTo(NOW - 30 * 60_000L)
        assertThat(parse("vor 45 Sekunden")).isEqualTo(NOW - 45_000L)
        assertThat(parse("Gestern")).isEqualTo(NOW - DAY)
        assertThat(parse("gerade eben")).isEqualTo(NOW)
    }

    /** "Monat" and "Minute" both begin with m, and one of them is 43 200 times the other. */
    @Test
    fun `a month is not a minute`() {
        assertThat(parse("vor 1 Monat")).isEqualTo(NOW - 30 * DAY)
        assertThat(parse("vor 6 Monaten")).isEqualTo(NOW - 180 * DAY)
    }

    @Test
    fun `livestream and premiere prefixes are ignored in both languages`() {
        assertThat(parse("Streamed 2 days ago")).isEqualTo(NOW - 2 * DAY)
        assertThat(parse("Premiered 3 weeks ago")).isEqualTo(NOW - 21 * DAY)
        assertThat(parse("Gestreamt vor 2 Tagen")).isEqualTo(NOW - 2 * DAY)
    }

    /** The compact forms YouTube uses in tight layouts. */
    @Test
    fun `short english forms still parse`() {
        assertThat(parse("3mo")).isEqualTo(NOW - 90 * DAY)
        assertThat(parse("5d")).isEqualTo(NOW - 5 * DAY)
        assertThat(parse("2h")).isEqualTo(NOW - 2 * 3_600_000L)
    }

    @Test
    fun `nothing parseable yields null rather than now`() {
        assertThat(parse(null)).isNull()
        assertThat(parse("")).isNull()
        assertThat(parse("irgendwas")).isNull()
    }
}
