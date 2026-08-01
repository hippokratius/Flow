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

import io.github.aedev.flow.innertube.models.normalizeYouTubeHostLanguage
import org.schabi.newpipe.extractor.localization.Localization

/**
 * The language the YouTube extractor should ask in.
 *
 * Flow pinned this to English everywhere — `Localization("en", "US")` at startup and
 * `Localization.fromLocale(Locale.ENGLISH)` at three call sites. For channels that publish
 * multi-language metadata on YouTube, and many German ones do, that meant a German device showing
 * English titles: "I prefer Claude Opus 5 over Fable" for a video titled "Claude Opus 5 gefällt mir
 * besser als Fable". It also broke the pairing with the PeerTube copy, which naturally carries the
 * creator's own language.
 *
 * Resolved through the same [normalizeYouTubeHostLanguage] that the InnerTube client already uses,
 * so the two halves of the app stop disagreeing about what language they are speaking — InnerTube
 * has followed the user's setting all along.
 */
fun extractorLocalization(appLanguageTag: String): Localization =
    Localization(normalizeYouTubeHostLanguage(appLanguageTag))
