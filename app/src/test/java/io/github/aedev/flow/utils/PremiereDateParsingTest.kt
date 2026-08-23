package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * A feed card asked for this on every composition, for every video, and threw the answer away
 * unless the video was a premiere. The pre-filter is what stops an ordinary relative date from
 * walking eight date patterns and eight exceptions to arrive at `null` — so what matters here is
 * that it still says `null` in exactly the same cases, and still parses everything it used to.
 */
class PremiereDateParsingTest {

    @Test
    fun `a relative date is not a premiere date`() {
        assertThat(formatPremiereDate("3 days ago")).isNull()
        assertThat(formatPremiereDate("vor 3 Tagen")).isNull()
        assertThat(formatPremiereDate("Streamed 2 weeks ago")).isNull()
    }

    @Test
    fun `blank is not a premiere date`() {
        assertThat(formatPremiereDate("")).isNull()
        assertThat(formatPremiereDate("   ")).isNull()
    }

    @Test
    fun `every supported pattern still parses`() {
        val supported = listOf(
            "2026-04-01 21:00",
            "2026-04-01T21:00:00+02:00",
            "2026-04-01T21:00:00Z",
            "2026-04-01T21:00:00.000+02:00",
            "2026-04-01T21:00:00.000Z",
            "2026-04-01T21:00:00",
            "2026-04-01",
        )

        supported.forEach { input ->
            assertThat(formatPremiereDate(input)).isNotNull()
        }
    }

    @Test
    fun `the timestamp variant agrees with the formatted one`() {
        assertThat(parsePremiereTimestamp("2026-04-01T21:00:00Z")).isNotNull()
        assertThat(parsePremiereTimestamp("in 2 hours")).isNull()
    }

    @Test
    fun `something merely containing digits is not a date`() {
        assertThat(formatPremiereDate("1.2M views")).isNull()
        assertThat(formatPremiereDate("2026")).isNull()
    }
}
