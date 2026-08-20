package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Country and language are resolved as two separate questions on purpose: the app's own language
 * override empties the process locale's country, and that must not be allowed to cost the user their
 * language as well.
 */
class ContentLocaleResolutionTest {

    @Test
    fun `a German setting on a German device asks as German`() {
        val locale = resolveContentLocale(
            storedRegion = "DE",
            appLanguageTag = "de",
            deviceCountry = "DE",
            deviceLanguageTag = "de-DE",
        )

        assertThat(locale.gl).isEqualTo("DE")
        assertThat(locale.hl).isEqualTo("de")
    }

    @Test
    fun `no chosen region takes the device's country`() {
        val locale = resolveContentLocale(
            storedRegion = null,
            appLanguageTag = "de",
            deviceCountry = "DE",
            deviceLanguageTag = "de-DE",
        )

        assertThat(locale.gl).isEqualTo("DE")
    }

    @Test
    fun `an unknown country does not cost the language`() {
        val locale = resolveContentLocale(
            storedRegion = null,
            appLanguageTag = "de",
            deviceCountry = "",
            deviceLanguageTag = "de-DE",
        )

        assertThat(locale.gl).isEqualTo(FALLBACK_REGION)
        assertThat(locale.hl).isEqualTo("de")
    }

    @Test
    fun `the app language follows the device when set to system`() {
        val locale = resolveContentLocale(
            storedRegion = "BR",
            appLanguageTag = "system",
            deviceCountry = "BR",
            deviceLanguageTag = "pt-BR",
        )

        assertThat(locale.gl).isEqualTo("BR")
        assertThat(locale.hl).isEqualTo("pt-BR")
    }
}
