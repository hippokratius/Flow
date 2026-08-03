/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source.link

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.aedev.flow.data.source.tubeHubPreferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage for the user's cross-platform channel links.
 *
 * Shares [tubeHubPreferencesDataStore] with the PeerTube instances rather than opening a store of its
 * own, and holds the list as one JSON string — the same shape as
 * [io.github.aedev.flow.data.source.peertube.PeerTubePreferences]. Deliberately not a Room table: a
 * new table means a schema migration, and this is a handful of rows of user preference, not content.
 *
 * The serializer is built once and shared by the read and write paths. Passing the list to
 * `encodeToString` without it resolves to the two-argument overload, which reads the list as a
 * serializer — a mistake this project has already paid for once.
 */
@Singleton
class ChannelLinkStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val links: Flow<List<ChannelLink>> = context.tubeHubPreferencesDataStore.data
        .map { preferences ->
            val raw = preferences[KEY_LINKS] ?: return@map emptyList()
            runCatching { json.decodeFromString(linkListSerializer, raw) }
                .getOrElse {
                    // An unreadable blob must not take the channel pages down with it.
                    Log.w(TAG, "Discarding unreadable channel link list", it)
                    emptyList()
                }
                .filter { it.isValid }
        }

    /** Whether subscribing to one half should subscribe the other. User-visible, defaults on. */
    val mirrorSubscriptions: Flow<Boolean> = context.tubeHubPreferencesDataStore.data
        .map { preferences -> preferences[KEY_MIRROR_SUBSCRIPTIONS] ?: true }

    /**
     * Whether tapping a YouTube video of a linked channel should open the PeerTube copy.
     *
     * On by default: moving one's viewing to the decentralised platform is the point of the app, and
     * a feature that only works once found in settings mostly does not work. The switch is the
     * permanent way out; the button under a redirected video is the one-off.
     */
    val redirectPlayback: Flow<Boolean> = context.tubeHubPreferencesDataStore.data
        .map { preferences -> preferences[KEY_REDIRECT_PLAYBACK] ?: true }

    suspend fun currentLinks(): List<ChannelLink> = links.first()

    suspend fun forYouTube(youtubeChannelId: String): ChannelLink? =
        ChannelLinks.forYouTube(currentLinks(), youtubeChannelId)

    suspend fun forPeerTube(peerTubeChannelId: String): ChannelLink? =
        ChannelLinks.forPeerTube(currentLinks(), peerTubeChannelId)

    suspend fun link(link: ChannelLink) = update { ChannelLinks.add(it, link) }

    suspend fun unlink(channelId: String) = update { ChannelLinks.remove(it, channelId) }

    suspend fun setMirrorSubscriptions(enabled: Boolean) {
        context.tubeHubPreferencesDataStore.edit { it[KEY_MIRROR_SUBSCRIPTIONS] = enabled }
    }

    suspend fun setRedirectPlayback(enabled: Boolean) {
        context.tubeHubPreferencesDataStore.edit { it[KEY_REDIRECT_PLAYBACK] = enabled }
    }

    private suspend fun update(transform: (List<ChannelLink>) -> List<ChannelLink>) {
        context.tubeHubPreferencesDataStore.edit { preferences ->
            val current = preferences[KEY_LINKS]
                ?.let { raw -> runCatching { json.decodeFromString(linkListSerializer, raw) }.getOrNull() }
                ?: emptyList()
            preferences[KEY_LINKS] = json.encodeToString(linkListSerializer, transform(current))
        }
    }

    private companion object {
        const val TAG = "ChannelLinkStore"
        val KEY_LINKS = stringPreferencesKey("channel_links")
        val KEY_MIRROR_SUBSCRIPTIONS = booleanPreferencesKey("channel_links_mirror_subscriptions")
        val KEY_REDIRECT_PLAYBACK = booleanPreferencesKey("channel_links_redirect_playback")
        val linkListSerializer = ListSerializer(ChannelLink.serializer())
    }
}

/**
 * Reaches the store from a class Hilt does not construct — `ChannelViewModel` is a plain `ViewModel`
 * with an `initialize(context)` entry point that many upstream call sites depend on. Same approach as
 * [io.github.aedev.flow.data.source.ContentSourceEntryPoint].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChannelLinkEntryPoint {
    fun channelLinkStore(): ChannelLinkStore
}

fun channelLinkStore(context: Context): ChannelLinkStore =
    EntryPointAccessors
        .fromApplication(context.applicationContext, ChannelLinkEntryPoint::class.java)
        .channelLinkStore()
