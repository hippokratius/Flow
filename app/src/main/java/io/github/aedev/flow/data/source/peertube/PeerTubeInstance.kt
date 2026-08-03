/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.peertube

import kotlinx.serialization.Serializable
import java.net.URI

/** A PeerTube instance the user has added. */
@Serializable
data class PeerTubeInstance(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
) {
    /** Bare host, used to build [io.github.aedev.flow.data.source.ContentId]s. */
    val host: String get() = hostOf(url) ?: url
}

/**
 * The instances a fresh install starts with, so the feed shows something before the user has
 * configured anything. Both are removable in settings.
 */
val DEFAULT_PEERTUBE_INSTANCES: List<PeerTubeInstance> = listOf(
    PeerTubeInstance(id = "default-framatube", name = "Framatube", url = "https://framatube.org"),
    PeerTubeInstance(id = "default-tilvids", name = "TILvids", url = "https://tilvids.com"),
)

/**
 * Instance list mutations, kept pure and free of Android types so they can be unit tested.
 * Ported from tube-hub's `SettingsRepository`, with `Uri.parse` swapped for [URI] — `Uri` is a
 * stubbed Android class under unit tests and would silently return null.
 */
object PeerTubeInstances {

    /** Adds a scheme when missing and drops any trailing slash. Blank input yields null. */
    fun normalizeUrl(rawUrl: String): String? {
        var url = rawUrl.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        url = url.trimEnd('/')
        // Reject anything that is only a scheme, e.g. a user typing "https://".
        return url.takeIf { hostOf(it)?.isNotBlank() == true }
    }

    /** Falls back to the host when the user leaves the name empty. */
    fun deriveName(name: String, url: String): String =
        name.trim().ifEmpty { hostOf(url) ?: url }

    /**
     * Returns the list with the instance appended, or unchanged when the URL is invalid or already
     * present. Comparison is case-insensitive: instance hosts are not case sensitive, and adding
     * the same server twice would duplicate every one of its videos in the feed.
     */
    fun add(
        current: List<PeerTubeInstance>,
        name: String,
        rawUrl: String,
        id: String,
    ): List<PeerTubeInstance> {
        val url = normalizeUrl(rawUrl) ?: return current
        if (current.any { it.url.equals(url, ignoreCase = true) }) return current
        return current + PeerTubeInstance(
            id = id,
            name = deriveName(name, url),
            url = url,
            enabled = true,
        )
    }

    fun remove(current: List<PeerTubeInstance>, id: String): List<PeerTubeInstance> =
        current.filterNot { it.id == id }

    fun setEnabled(
        current: List<PeerTubeInstance>,
        id: String,
        enabled: Boolean,
    ): List<PeerTubeInstance> =
        current.map { if (it.id == id) it.copy(enabled = enabled) else it }
}

/** Host of an absolute URL, or null when it cannot be parsed. */
internal fun hostOf(url: String): String? =
    runCatching { URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }
