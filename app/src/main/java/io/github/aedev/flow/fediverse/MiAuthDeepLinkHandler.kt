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

import android.content.Intent
import android.util.Log
import io.github.aedev.flow.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles the browser's return leg of a MiAuth sign-in.
 *
 * A class of its own rather than code inside `MainActivity` for two reasons: it keeps the edit to
 * that upstream file down to a single early-return, and it makes the flow unit-testable — in the
 * original implementation this logic lived in an Activity and could not be tested at all.
 */
@Singleton
class MiAuthDeepLinkHandler @Inject constructor(
    private val store: FediverseAccountStore,
    private val repository: FediverseRepository,
    private val events: MiAuthEventBus,
) {
    /**
     * Returns true when [intent] was a MiAuth callback and has been taken over, so the caller
     * stops treating it as a video link. Completion happens asynchronously on [scope].
     */
    fun consume(intent: Intent?, scope: CoroutineScope): Boolean {
        val session = MiAuth.sessionFrom(intent?.data?.toString()) ?: return false

        scope.launch {
            val pending = runCatching { store.pendingMiAuth.first() }.getOrNull()
            if (pending == null || pending.uuid != session) {
                // A stale or forged callback. Not an error worth showing, but it must not be acted
                // upon: the session id is what ties this response to the request we made.
                Log.w(TAG, "Ignoring MiAuth callback that matches no pending session")
                events.emit(MiAuthResult.Failure(R.string.fediverse_error_authorization_declined))
                return@launch
            }

            store.clearPendingMiAuth()
            runCatching { repository.completeMiAuth(pending.instanceUrl, session) }
                .onSuccess { account ->
                    store.addAccount(account)
                    events.emit(MiAuthResult.Success(account.username))
                }
                .onFailure { failure ->
                    Log.w(TAG, "MiAuth completion failed", failure)
                    val messageRes = (failure as? FediverseException)?.messageRes
                        ?: R.string.fediverse_error_generic
                    events.emit(MiAuthResult.Failure(messageRes))
                }
        }
        return true
    }

    private companion object {
        const val TAG = "MiAuthDeepLink"
    }
}
