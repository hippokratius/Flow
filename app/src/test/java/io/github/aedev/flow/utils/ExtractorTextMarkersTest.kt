package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The two other places the app read English words out of the extractor's answers.
 */
class ExtractorTextMarkersTest {

    /**
     * The app prefixes "Streamed" onto an archived livestream's date when the extractor has not
     * already said so. Recognising only the English word meant a German date collected a second
     * prefix: "Streamed Gestreamt vor 3 Tagen".
     */
    @Test
    fun `an existing streamed prefix is recognised in both languages`() {
        assertThat("Streamed 3 days ago".hasStreamedPrefix()).isTrue()
        assertThat("Gestreamt vor 3 Tagen".hasStreamedPrefix()).isTrue()
        assertThat("  streamed 3 days ago".hasStreamedPrefix()).isTrue()
    }

    @Test
    fun `an ordinary date has no prefix`() {
        assertThat("3 days ago".hasStreamedPrefix()).isFalse()
        assertThat("vor 3 Tagen".hasStreamedPrefix()).isFalse()
        assertThat("".hasStreamedPrefix()).isFalse()
    }

    /** "1.2K watching" marks a live video; "1.2K views" does not. */
    @Test
    fun `live viewer counts are told apart from view counts`() {
        assertThat("1.2K watching".isLiveViewerText()).isTrue()
        assertThat("3,400 viewers".isLiveViewerText()).isTrue()
        assertThat("1.234 Zuschauer".isLiveViewerText()).isTrue()

        assertThat("1.2K views".isLiveViewerText()).isFalse()
        assertThat("1.234 Aufrufe".isLiveViewerText()).isFalse()
        assertThat(null.isLiveViewerText()).isFalse()
        assertThat("".isLiveViewerText()).isFalse()
    }
}
