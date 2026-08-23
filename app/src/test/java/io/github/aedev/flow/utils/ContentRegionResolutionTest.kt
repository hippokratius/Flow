package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Which country every YouTube request claims to come from.
 *
 * The case that started this: a German user whose trending feed was American. The stored default was
 * a literal "US", and the fallback that was supposed to catch a device like theirs read
 * `Locale.getDefault().country` — which the app itself empties when someone picks an app language.
 */
class ContentRegionResolutionTest {

    @Test
    fun `a chosen region wins`() {
        assertThat(resolveContentRegion(stored = "DE", deviceCountry = "US")).isEqualTo("DE")
    }

    @Test
    fun `never chosen means the device answers`() {
        assertThat(resolveContentRegion(stored = null, deviceCountry = "DE")).isEqualTo("DE")
    }

    @Test
    fun `a device with no country of its own falls back`() {
        // What Locale.setDefault(Locale.forLanguageTag("de")) leaves behind — see ContentLocale.
        assertThat(resolveContentRegion(stored = null, deviceCountry = "")).isEqualTo(FALLBACK_REGION)
    }

    @Test
    fun `a country YouTube has no feed for falls back`() {
        assertThat(resolveContentRegion(stored = null, deviceCountry = "XK")).isEqualTo(FALLBACK_REGION)
        assertThat(resolveContentRegion(stored = "XK", deviceCountry = "DE")).isEqualTo("DE")
    }

    @Test
    fun `case and padding do not matter`() {
        assertThat(resolveContentRegion(stored = "  de  ", deviceCountry = "US")).isEqualTo("DE")
    }

    @Test
    fun `something that is not a region code is not one`() {
        assertThat(resolveContentRegion(stored = "USA", deviceCountry = "DE")).isEqualTo("DE")
        assertThat(resolveContentRegion(stored = "", deviceCountry = "DE")).isEqualTo("DE")
    }

    @Test
    fun `the region list carries the countries the picker offers`() {
        assertThat(YOUTUBE_REGIONS).containsEntry("DE", "Germany")
        assertThat(YOUTUBE_REGIONS).containsEntry("US", "United States")
    }
}
