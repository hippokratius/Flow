package io.github.aedev.flow.data.local

import androidx.datastore.preferences.core.preferencesOf
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.utils.DateContextMode
import io.github.aedev.flow.utils.DateDisplayMode
import io.github.aedev.flow.utils.DateFormatStyle
import org.junit.Test

/**
 * Five separate preference flows became one read. Worth a test because the failure mode is quiet: a
 * mistyped key does not crash, it just hands everyone the default date format and looks like the
 * setting was never saved.
 */
class DateDisplaySettingsMappingTest {

    @Test
    fun `an empty store gives the defaults`() {
        val settings = preferencesOf().toDateDisplaySettings()

        assertThat(settings).isEqualTo(io.github.aedev.flow.utils.DateDisplaySettings())
    }

    @Test
    fun `each stored value reaches its own field`() {
        val settings = preferencesOf(
            DatePreferenceKeys.DISPLAY_MODE to DateDisplayMode.EXACT.name,
            DatePreferenceKeys.FORMAT_STYLE to DateFormatStyle.ISO.name,
            DatePreferenceKeys.MODE_LISTS to DateContextMode.RELATIVE.name,
            DatePreferenceKeys.MODE_WATCH to DateContextMode.EXACT.name,
            DatePreferenceKeys.MODE_DESCRIPTION to DateContextMode.BOTH.name,
        ).toDateDisplaySettings()

        assertThat(settings.globalMode).isEqualTo(DateDisplayMode.EXACT)
        assertThat(settings.formatStyle).isEqualTo(DateFormatStyle.ISO)
        assertThat(settings.listsMode).isEqualTo(DateContextMode.RELATIVE)
        assertThat(settings.watchMode).isEqualTo(DateContextMode.EXACT)
        assertThat(settings.descriptionMode).isEqualTo(DateContextMode.BOTH)
    }

    @Test
    fun `an unreadable value falls back rather than throwing`() {
        val settings = preferencesOf(
            DatePreferenceKeys.DISPLAY_MODE to "NOT_A_MODE",
        ).toDateDisplaySettings()

        assertThat(settings.globalMode).isEqualTo(DateDisplayMode.RELATIVE)
    }
}
