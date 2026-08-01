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

import java.util.Locale

/*
 * "vor 5 Tagen" — turning what the extractor says into a timestamp.
 *
 * This is the fallback for feed items that carry no absolute date. It used to understand English
 * only, which was safe while the extractor was pinned to English; now that it answers in the user's
 * language, an unparsed date falls through to "right now" and every video in the feed looks freshly
 * uploaded. Feed ordering is built on these timestamps, so this is not a cosmetic path.
 *
 * Pure and Android-free so the languages can be checked against each other without a device.
 */

/** Millisecond timestamp for a relative date like "5 days ago" / "vor 5 Tagen", or null. */
fun parseRelativeUploadDateMillis(
    textualDate: String?,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val raw = textualDate?.trim().orEmpty()
    if (raw.isBlank()) return null

    val normalized = raw.lowercase(Locale.ROOT)
        // Livestream and premiere prefixes carry no time information in any language.
        .replace(Regex("streamed|premiered|gestreamt|premiere|live"), " ")
        .replace(Regex("\\bago\\b|\\bvor\\b|\\bseit\\b"), " ")
        .trim()

    if (JUST_NOW.containsMatchIn(normalized)) return nowMillis
    if (YESTERDAY.containsMatchIn(normalized)) return nowMillis - DAY_MILLIS

    val value = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: return null

    // Order matters: "monat" and "minute" both start with "m", and German "Monaten" must not be
    // read as minutes. Longer, more specific stems are tested first.
    val unitMillis = when {
        MONTH.containsMatchIn(normalized) -> 30L * DAY_MILLIS
        MINUTE.containsMatchIn(normalized) -> 60_000L
        SECOND.containsMatchIn(normalized) -> 1_000L
        HOUR.containsMatchIn(normalized) -> 3_600_000L
        DAY.containsMatchIn(normalized) -> DAY_MILLIS
        WEEK.containsMatchIn(normalized) -> 7L * DAY_MILLIS
        YEAR.containsMatchIn(normalized) -> 365L * DAY_MILLIS
        // The bare single-letter forms YouTube uses in compact layouts, English only.
        normalized.endsWith("mo") -> 30L * DAY_MILLIS
        normalized.endsWith("s") -> 1_000L
        normalized.endsWith("m") -> 60_000L
        normalized.endsWith("h") -> 3_600_000L
        normalized.endsWith("d") -> DAY_MILLIS
        normalized.endsWith("w") -> 7L * DAY_MILLIS
        normalized.endsWith("y") -> 365L * DAY_MILLIS
        else -> return null
    }

    return nowMillis - (value * unitMillis)
}

private const val DAY_MILLIS = 86_400_000L

private val JUST_NOW = Regex("just now|gerade eben|soeben|heute|today")
private val YESTERDAY = Regex("yesterday|gestern")
private val SECOND = Regex("second|sekunde")
private val MINUTE = Regex("minute|minuten")
private val HOUR = Regex("hour|stunde")
private val DAY = Regex("\\bday|\\btag")
private val WEEK = Regex("week|woche")
private val MONTH = Regex("month|monat")
private val YEAR = Regex("year|jahr")
