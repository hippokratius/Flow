/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Look-up and ordering for the available [ContentSource]s.
 *
 * Mirrors [io.github.aedev.flow.data.lyrics.LyricsProviderRegistry] — same CSV order string, same
 * "unknown keys are dropped, missing keys are appended" behaviour — so there is one ordering
 * convention in the app rather than two.
 */
@Singleton
class ContentSourceRegistry @Inject constructor(
    private val sources: Map<String, @JvmSuppressWildcards ContentSource>,
) {
    val keys: List<String> = sources.keys.toList()

    fun byKey(key: String): ContentSource? = sources[key]

    fun forId(id: ContentId): ContentSource? = sources.values.firstOrNull { it.handles(id) }

    fun deserializeOrder(orderString: String): List<String> {
        if (orderString.isBlank()) return defaultOrder()
        return orderString.split(",").map { it.trim() }.filter { it in keys }
    }

    fun serializeOrder(order: List<String>): String =
        order.filter { it in keys }.joinToString(",")

    fun defaultOrder(): List<String> = listOf(KEY_YOUTUBE, KEY_PEERTUBE).filter { it in keys }

    /** Sources in the user's configured order, with any not mentioned appended. */
    fun ordered(orderString: String): List<ContentSource> {
        val ordered = deserializeOrder(orderString).mapNotNull(::byKey)
        val missing = sources.values.filter { s -> ordered.none { it.key == s.key } }
        return ordered + missing
    }

    companion object {
        const val KEY_YOUTUBE = "youtube"
        const val KEY_PEERTUBE = "peertube"
    }
}
