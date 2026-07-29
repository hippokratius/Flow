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

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.source.tubeHubPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** A connected Fediverse (Misskey) account. [apiToken] is a write credential. */
@Serializable
data class FediverseAccount(
    val id: String,
    val instanceUrl: String,
    val username: String,
    val apiToken: String,
) {
    val handle: String get() = "@$username@${instanceUrl.substringAfter("://").trimEnd('/')}"
}

/** A MiAuth session that is waiting for the user to come back from the browser. */
@Serializable
data class PendingMiAuth(
    val uuid: String,
    val instanceUrl: String,
)

/**
 * Stores connected accounts and the in-flight MiAuth session.
 *
 * Shares the `tubehub_preferences` DataStore with the PeerTube instance list — one fork-owned store
 * rather than appending to `PlayerPreferences`, which at 2600 lines is the most upstream-churned
 * file in the app.
 *
 * Tokens pass through [TokenVault] on the way in and out, so what lands on disk is ciphertext.
 * [PendingMiAuth] is persisted rather than kept in memory because the user leaves for a browser in
 * the middle of the flow and the process may be killed while they are gone.
 */
@Singleton
class FediverseAccountStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vault: TokenVault,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val accounts: Flow<List<FediverseAccount>> = context.tubeHubPreferencesDataStore.data
        .map { preferences ->
            val raw = preferences[KEY_ACCOUNTS] ?: return@map emptyList()
            runCatching { json.decodeFromString(accountListSerializer, raw) }
                .getOrElse {
                    Log.w(TAG, "Discarding unreadable account list", it)
                    emptyList()
                }
                .map { account -> account.copy(apiToken = vault.decrypt(account.apiToken)) }
                // A token that failed to decrypt is unusable; surfacing the account would only
                // produce confusing failures on every action.
                .filter { it.apiToken.isNotBlank() }
        }

    val pendingMiAuth: Flow<PendingMiAuth?> = context.tubeHubPreferencesDataStore.data
        .map { preferences ->
            preferences[KEY_PENDING]?.let { raw ->
                runCatching { json.decodeFromString(PendingMiAuth.serializer(), raw) }.getOrNull()
            }
        }

    suspend fun currentAccounts(): List<FediverseAccount> = accounts.first()

    suspend fun addAccount(account: FediverseAccount) = updateAccounts { current ->
        // Reconnecting an account replaces it rather than duplicating the entry.
        current.filterNot {
            it.username == account.username && it.instanceUrl == account.instanceUrl
        } + account
    }

    suspend fun removeAccount(id: String) = updateAccounts { it.filterNot { a -> a.id == id } }

    suspend fun setPendingMiAuth(pending: PendingMiAuth) {
        context.tubeHubPreferencesDataStore.edit {
            it[KEY_PENDING] = json.encodeToString(PendingMiAuth.serializer(), pending)
        }
    }

    suspend fun clearPendingMiAuth() {
        context.tubeHubPreferencesDataStore.edit { it.remove(KEY_PENDING) }
    }

    private suspend fun updateAccounts(
        transform: (List<FediverseAccount>) -> List<FediverseAccount>,
    ) {
        context.tubeHubPreferencesDataStore.edit { preferences ->
            val current = preferences[KEY_ACCOUNTS]
                ?.let { raw ->
                    runCatching { json.decodeFromString(accountListSerializer, raw) }.getOrNull()
                }
                ?.map { it.copy(apiToken = vault.decrypt(it.apiToken)) }
                .orEmpty()
            val next = transform(current)
                .map { it.copy(apiToken = vault.encrypt(it.apiToken)) }
            preferences[KEY_ACCOUNTS] = json.encodeToString(accountListSerializer, next)
        }
    }

    private companion object {
        const val TAG = "FediverseAccountStore"
        val KEY_ACCOUNTS = stringPreferencesKey("fediverse_accounts")
        val KEY_PENDING = stringPreferencesKey("fediverse_pending_miauth")
        val accountListSerializer = ListSerializer(FediverseAccount.serializer())
    }
}
