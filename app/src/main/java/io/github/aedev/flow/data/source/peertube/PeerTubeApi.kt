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

import io.github.aedev.flow.di.MetadataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal PeerTube REST client.
 *
 * Plain OkHttp rather than Retrofit: every PeerTube call targets an arbitrary host, so a Retrofit
 * interface would need an absolute `@Url` against a dummy base URL for all of two endpoints. This
 * project has no Retrofit dependency and adding one to buy nothing is not worth it.
 *
 * Uses [MetadataClient] — 10s connect / 15s read — rather than the default client's 60s call
 * timeout. Instances are fanned out concurrently for the home feed, and one unreachable server must
 * not stall the feed for a minute. Both clients inherit the app's proxy setting.
 */
@Singleton
class PeerTubeApi @Inject constructor(
    @MetadataClient private val client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Newest videos of an instance, or search results when [search] is set. */
    suspend fun videos(
        instanceUrl: String,
        count: Int,
        start: Int = 0,
        search: String? = null,
    ): PTVideoListDto {
        val base = if (search.isNullOrBlank()) {
            "$instanceUrl/api/v1/videos"
        } else {
            "$instanceUrl/api/v1/search/videos"
        }
        val query = buildString {
            append("?count=").append(count)
            append("&start=").append(start)
            append("&sort=-publishedAt")
            append("&nsfw=false")
            if (!search.isNullOrBlank()) {
                append("&search=").append(URLEncoder.encode(search, "UTF-8"))
            }
        }
        return get(base + query)
    }

    /**
     * Newest videos of a single channel.
     *
     * A federated follow can point at either a channel actor or an account actor, so the account
     * endpoint — which aggregates all of that account's channels — is the fallback when the channel
     * lookup fails.
     */
    suspend fun channelVideos(
        instanceUrl: String,
        channelName: String,
        count: Int,
        start: Int = 0,
    ): PTVideoListDto {
        val name = URLEncoder.encode(channelName, "UTF-8")
        val query = "/videos?count=$count&start=$start&sort=-publishedAt&nsfw=false"
        return runCatching {
            get<PTVideoListDto>("$instanceUrl/api/v1/video-channels/$name$query")
        }.getOrElse {
            get("$instanceUrl/api/v1/accounts/$name$query")
        }
    }

    /**
     * Channels matching [query] on this instance.
     *
     * Note that instances vary in what they will answer: `searchTargetType` may be restricted to
     * local content, and federated search can be switched off entirely. An empty result therefore
     * does not mean the channel does not exist, which is why pasting an address stays a first-class
     * way to link a channel — see [PeerTubeChannelReference].
     */
    suspend fun searchChannels(
        instanceUrl: String,
        query: String,
        count: Int = 10,
    ): PTChannelListDto {
        val search = URLEncoder.encode(query, "UTF-8")
        return get("$instanceUrl/api/v1/search/video-channels?search=$search&count=$count")
    }

    /** Name, description, follower count and artwork of a channel. Same actor fallback as above. */
    suspend fun channelDetail(instanceUrl: String, channelName: String): PTChannelDetailDto {
        val name = URLEncoder.encode(channelName, "UTF-8")
        return runCatching {
            get<PTChannelDetailDto>("$instanceUrl/api/v1/video-channels/$name")
        }.getOrElse {
            get("$instanceUrl/api/v1/accounts/$name")
        }
    }

    suspend fun videoDetail(instanceUrl: String, uuid: String): PTVideoDetailDto =
        get("$instanceUrl/api/v1/videos/$uuid")

    /**
     * Top-level comments on a video.
     *
     * Threads only. Replies live behind `/comment-threads/{threadId}`, one request per thread, which
     * is why they are not fetched here — see the note in [toComment] about what that costs.
     */
    suspend fun comments(instanceUrl: String, uuid: String, count: Int = 30): PTCommentListDto =
        get("$instanceUrl/api/v1/videos/$uuid/comment-threads?count=$count")

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            json.decodeFromString<T>(body)
        }
    }
}
