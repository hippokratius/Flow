/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.fediverse

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of a MiAuth round trip, delivered back to whichever screen is alive by then. */
sealed interface MiAuthResult {
    data class Success(val username: String) : MiAuthResult
    data class Failure(val messageRes: Int) : MiAuthResult
}

/**
 * Carries the MiAuth outcome from the activity that received the deep link back to the settings
 * screen — which may have been destroyed and recreated while the user was in the browser, so a
 * direct callback is not enough.
 */
@Singleton
class MiAuthEventBus @Inject constructor() {
    private val _events = MutableSharedFlow<MiAuthResult>(extraBufferCapacity = 1)
    val events: SharedFlow<MiAuthResult> = _events.asSharedFlow()

    fun emit(result: MiAuthResult) {
        _events.tryEmit(result)
    }
}

/**
 * Everything about the MiAuth URL shape.
 *
 * Deliberately free of `android.net.Uri`: that class is stubbed out under unit tests, so anything
 * built on it can only be verified on a device. Using `java.net` here makes the whole contract —
 * URL shape, permission set, callback parsing — testable on the JVM.
 */
object MiAuth {
    const val CALLBACK_SCHEME = "tubehub"
    const val CALLBACK_HOST = "miauth"
    const val CALLBACK_URL = "$CALLBACK_SCHEME://$CALLBACK_HOST"

    /**
     * Permissions requested from the instance.
     *
     * Exactly this set, in this order: `read:account` is required to read back the username after
     * authorisation, and dropping any of the write scopes silently disables the matching action.
     */
    const val PERMISSIONS =
        "read:account,write:notes,write:reactions,read:following,write:following"

    fun authorizationUrl(instanceUrl: String, sessionUuid: String): String =
        "${instanceUrl.trimEnd('/')}/miauth/$sessionUuid" +
            "?name=TubeHub" +
            "&permission=$PERMISSIONS" +
            "&callback=${URLEncoder.encode(CALLBACK_URL, Charsets.UTF_8.name())}"

    /** The session id carried by a callback URL, or null when this is not a callback. */
    fun sessionFrom(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (!parsed.scheme.equals(CALLBACK_SCHEME, ignoreCase = true)) return null
        // Custom-scheme URLs put "miauth" in the authority rather than the host on some parsers,
        // so accept either.
        val target = parsed.host ?: parsed.authority
        if (!target.equals(CALLBACK_HOST, ignoreCase = true)) return null

        return parsed.query
            ?.split('&')
            ?.firstOrNull { it.startsWith("session=") }
            ?.removePrefix("session=")
            ?.let { runCatching { URLDecoder.decode(it, Charsets.UTF_8.name()) }.getOrDefault(it) }
            ?.takeIf { it.isNotBlank() }
    }
}
