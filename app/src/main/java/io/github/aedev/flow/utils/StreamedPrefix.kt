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

/**
 * Whether an upload date already says it was a livestream.
 *
 * The app prefixes "Streamed" onto the date of an archived livestream when the extractor has not
 * said so itself. That check used to look for the English word only — so once the extractor answers
 * in German, "Gestreamt vor 3 Tagen" was not recognised and became "Streamed Gestreamt vor 3 Tagen".
 */
fun String.hasStreamedPrefix(): Boolean {
    val lower = trimStart().lowercase()
    return STREAMED_MARKERS.any { lower.startsWith(it) }
}

private val STREAMED_MARKERS = listOf("streamed", "gestreamt", "live gestreamt")
