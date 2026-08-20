/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.utils

import android.content.Context
import android.content.res.Resources
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeLocale
import io.github.aedev.flow.innertube.models.normalizeYouTubeHostLanguage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/**
 * The country and language every YouTube request carries.
 *
 * Both are process-global state — NewPipe keeps its `Localization` and `ContentCountry` in static
 * fields, the InnerTube client in a singleton — and both used to be written from five places, three
 * of which ignored the user's setting: the app pinned `US` at startup, the extractor's reload retry
 * pinned it again, and `NewPipe.init(downloader)` on the playback path reset the pair to the
 * library's own `en`/`GB`. Playing one video was enough to make the whole app answer as if it lived
 * somewhere else.
 *
 * So there is one owner. Everything that used to call `NewPipe.init` or assign `YouTube.locale`
 * goes through here, and [snapshot] answers without blocking — [seed] mirrors the resolved pair into
 * SharedPreferences, the same trick [AppLanguageManager] uses to have an answer ready in
 * `attachBaseContext`, because neither `Application.onCreate` nor the extractor's lazy init can wait
 * for DataStore.
 */
object ContentLocale {

    // The file FlowApplication already opens for its visitor data, so the seed on the cold-start
    // path costs no additional file read.
    private const val PREFS_FILE = "flow_prefs"
    private const val KEY_GL = "content_locale_gl"
    private const val KEY_HL = "content_locale_hl"

    @Volatile
    private var current: YouTubeLocale = YouTubeLocale(gl = FALLBACK_REGION, hl = "en")

    @Volatile
    private var mirror: android.content.SharedPreferences? = null

    /** Loads the last known pair, so the first request of the process already carries it. */
    fun seed(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
        mirror = prefs
        val gl = prefs.getString(KEY_GL, null)
        val hl = prefs.getString(KEY_HL, null)
        publish(
            YouTubeLocale(
                gl = resolveContentRegion(gl, deviceRegion()),
                hl = normalizeYouTubeHostLanguage(
                    hl ?: AppLanguageManager.loadSelectedLanguageTag(context)
                ),
            )
        )
    }

    /** What every request should say right now. Never touches disk. */
    fun snapshot(): YouTubeLocale = current

    /** The one writer. Called from the preference collector in `FlowApplication`. */
    fun apply(locale: YouTubeLocale) {
        if (locale == current) return
        publish(locale)
        mirror?.edit()?.putString(KEY_GL, locale.gl)?.putString(KEY_HL, locale.hl)?.apply()
    }

    /**
     * Both clients at once.
     *
     * The InnerTube singleton is the half that cannot wait: it seeds itself from `Locale.getDefault()`,
     * whose country the app's own language override empties, and the visitor-data request that
     * decides which country YouTube thinks the user is in goes out during startup — long before
     * DataStore has answered.
     */
    private fun publish(locale: YouTubeLocale) {
        current = locale
        YouTube.locale = locale
    }

    /**
     * Initialises NewPipe with the current pair.
     *
     * The reason this exists rather than each caller passing its own arguments: every one of those
     * callers got at least one of the two wrong.
     */
    fun applyTo(downloader: Downloader) {
        val locale = current
        NewPipe.init(downloader, Localization(locale.hl), ContentCountry(locale.gl))
    }

    /** The region to fall back on when the user has never picked one. */
    fun defaultRegion(): String = resolveContentRegion(stored = null, deviceCountry = deviceRegion())

    /**
     * The country of the device itself.
     *
     * Deliberately not `Locale.getDefault().country`: [AppLanguageManager.wrapContext] calls
     * `Locale.setDefault` with a language tag that carries no country, so from the moment a user
     * picks an app language, the process default has an empty country and every "fall back to the
     * device" path silently lands on the United States. `Resources.getSystem()` is the device's own
     * configuration and is not affected by that override.
     */
    fun deviceRegion(): String {
        val system = Resources.getSystem().configuration.locales
        return system.takeIf { !it.isEmpty }?.get(0)?.country.orEmpty()
    }
}

/**
 * The pair to apply for a given setting, resolved as two independent answers.
 *
 * Country and language degrade separately on purpose: a device whose country is unknown must still
 * be answered in its language, which is exactly the case a user creates by picking an app language
 * (see [ContentLocale.deviceRegion]).
 */
fun resolveContentLocale(
    storedRegion: String?,
    appLanguageTag: String,
    deviceCountry: String,
    deviceLanguageTag: String,
): YouTubeLocale = YouTubeLocale(
    gl = resolveContentRegion(storedRegion, deviceCountry),
    hl = normalizeYouTubeHostLanguage(appLanguageTag, java.util.Locale.forLanguageTag(deviceLanguageTag)),
)
