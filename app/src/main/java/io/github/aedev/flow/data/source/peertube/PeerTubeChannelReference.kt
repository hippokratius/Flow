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

import io.github.aedev.flow.data.source.ContentId
import java.net.URI

/**
 * Turns whatever a user pastes into a PeerTube channel [ContentId].
 *
 * Needed alongside the channel search because search is not dependable here: instances may restrict
 * the search index to local content, or disable federated search entirely, so a channel that plainly
 * exists can be unfindable. A pasted address always works, and pasting is what people do with a
 * channel they are already looking at in a browser.
 *
 * Pure and free of `android.net.Uri` so it can be unit tested.
 */
object PeerTubeChannelReference {

    /**
     * Accepted forms:
     * - `https://host/c/handle`, with or without a trailing `/videos`
     * - `https://host/video-channels/handle` — the ActivityPub actor URL
     * - `https://host/a/handle` and `/accounts/handle` — an account actor, which the API also serves
     * - `handle@host`
     * - `@handle@host` — how Mastodon and friends render a Fediverse address
     *
     * Returns null rather than guessing. A bare `handle` with no host is refused on purpose: without
     * a host there is nothing to query, and silently picking one of the user's instances would link
     * to whichever happens to have a channel of that name.
     */
    fun parse(input: String): ContentId? {
        val value = input.trim()
        if (value.isEmpty()) return null

        return parseUrl(value) ?: parseHandle(value)
    }

    private fun parseUrl(value: String): ContentId? {
        if (!value.startsWith("http://", ignoreCase = true) &&
            !value.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
        if (segments.size < 2) return null

        val handle = when (segments[0].lowercase()) {
            "c", "video-channels", "a", "accounts" -> segments[1]
            else -> return null
        }.removePrefix("@").takeIf { it.isNotBlank() } ?: return null

        // A pasted URL can already carry a federated handle: https://tilvids.com/c/news@framatube.org
        return ContentId.peerTube(host, handle)
    }

    private fun parseHandle(value: String): ContentId? {
        val parts = value.removePrefix("@").split('@')
        if (parts.size != 2) return null
        val handle = parts[0].trim().takeIf { it.isNotBlank() } ?: return null
        val host = parts[1].trim().lowercase().trim('/').takeIf { it.isNotBlank() } ?: return null
        // A host has a dot; without one this is far more likely to be a typo than a real address.
        if (!host.contains('.')) return null
        return ContentId.peerTube(host, handle)
    }
}
