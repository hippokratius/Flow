package io.github.aedev.flow.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.utils.DateDisplaySettings

/**
 * The date settings resolved once for everything below it.
 *
 * `compositionLocalOf`, not `staticCompositionLocalOf`: these change while the app is running — the
 * user picks a different date format in settings — and a static local would invalidate the whole
 * subtree instead of just the composables that read it.
 *
 * Null means "nobody provided it", which is what makes the fallback in [rememberDateDisplaySettings]
 * necessary. A non-null default would be worse than no default: a surface outside the provider would
 * silently show the standard format instead of the user's.
 */
val LocalDateDisplaySettings = compositionLocalOf<DateDisplaySettings?> { null }

/**
 * How every screen asks for the user's date preferences.
 *
 * The name and signature are unchanged on purpose, so the twelve call sites across seven files keep
 * working untouched — but what happens underneath is not: this used to start **five** DataStore
 * collectors per call, and it is called from inside a feed card. A screen showing twenty cards paid
 * a hundred collectors, each delivering its default first and the real value a moment later, which
 * invalidated the card again. Under the provider it is now one shared value.
 */
@Composable
fun rememberDateDisplaySettings(): DateDisplaySettings {
    LocalDateDisplaySettings.current?.let { return it }

    // Outside the provider — the TV surfaces, a preview, anything hosted on its own — behave as
    // before rather than silently losing the user's setting.
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val settings by prefs.dateDisplaySettings.collectAsState(initial = DateDisplaySettings())
    return settings
}
