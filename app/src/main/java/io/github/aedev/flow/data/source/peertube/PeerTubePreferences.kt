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

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.safePreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the user's PeerTube instances.
 *
 * A DataStore of its own rather than a section in `PlayerPreferences`: that file is 2600+ lines and
 * is the single most upstream-churned file in the app, so appending to it would cost a merge
 * conflict on every sync with Flow. It still goes through the project's own
 * [safePreferencesDataStore] helper, so corruption handling matches the rest of the app.
 *
 * The list is stored as JSON in one string key, which is how this codebase already holds structured
 * preference data (see `SubscriptionRepository`).
 */
@Singleton
class PeerTubePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val instances: Flow<List<PeerTubeInstance>> = context.tubeHubPreferencesDataStore.data
        .map { preferences ->
            val raw = preferences[KEY_INSTANCES] ?: return@map DEFAULT_PEERTUBE_INSTANCES
            runCatching {
                json.decodeFromString(instanceListSerializer, raw)
            }.getOrElse {
                // Never let a bad blob take the feed down with it.
                Log.w(TAG, "Discarding unreadable PeerTube instance list", it)
                DEFAULT_PEERTUBE_INSTANCES
            }
        }

    /** Only the instances that should actually be queried. */
    val enabledInstances: Flow<List<PeerTubeInstance>> =
        instances.map { list -> list.filter { it.enabled } }

    suspend fun currentEnabledInstances(): List<PeerTubeInstance> = enabledInstances.first()

    suspend fun addInstance(name: String, rawUrl: String) =
        update { PeerTubeInstances.add(it, name, rawUrl, id = UUID.randomUUID().toString()) }

    suspend fun removeInstance(id: String) = update { PeerTubeInstances.remove(it, id) }

    suspend fun setInstanceEnabled(id: String, enabled: Boolean) =
        update { PeerTubeInstances.setEnabled(it, id, enabled) }

    private suspend fun update(transform: (List<PeerTubeInstance>) -> List<PeerTubeInstance>) {
        context.tubeHubPreferencesDataStore.edit { preferences ->
            val current = preferences[KEY_INSTANCES]
                ?.let { raw -> runCatching { json.decodeFromString(instanceListSerializer, raw) }.getOrNull() }
                ?: DEFAULT_PEERTUBE_INSTANCES
            preferences[KEY_INSTANCES] = json.encodeToString(instanceListSerializer, transform(current))
        }
    }

    private companion object {
        const val TAG = "PeerTubePreferences"
        val KEY_INSTANCES = stringPreferencesKey("peertube_instances")
        val instanceListSerializer = ListSerializer(PeerTubeInstance.serializer())
    }
}

private val Context.tubeHubPreferencesDataStore by safePreferencesDataStore(name = "tubehub_preferences")
