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

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.di.MetadataClient
import io.github.aedev.flow.fediverse.model.ApShowDto
import io.github.aedev.flow.fediverse.model.MiAuthCheckDto
import io.github.aedev.flow.fediverse.model.MiUserDetailDto
import io.github.aedev.flow.fediverse.model.MiUserDto
import io.github.aedev.flow.fediverse.model.NoteShowDto
import io.github.aedev.flow.fediverse.model.ReplyDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Fediverse failure with a message the UI can localise.
 *
 * The original implementation embedded German error strings in the repository. Flow ships 27
 * Weblate-managed locales and forbids hardcoded user-facing text, so the message is carried as a
 * resource id and resolved at the UI boundary — which also keeps a `Context` out of the repository.
 */
class FediverseException(
    @StringRes val messageRes: Int,
    val arg: Any? = null,
) : IOException()

data class CommentItem(
    val id: String,
    val username: String,
    val host: String?,
    val text: String?,
    val createdAt: String,
)

data class VideoStats(
    val reactions: Map<String, Int> = emptyMap(),
    val renoteCount: Int = 0,
    val replies: List<CommentItem> = emptyList(),
)

/**
 * Misskey interaction: MiAuth sign-in, then like / boost / comment / follow against a federated
 * video. Ported near-verbatim from the standalone TubeHub client — the quirks below were each paid
 * for once and must not be "tidied up".
 *
 * Raw OkHttp rather than a typed client because request bodies and error shapes are dynamic.
 * Bound to [MetadataClient]: the default client carries a 100 MB disk cache and caps concurrency at
 * five requests per host, which would serialise the concurrent instance-software lookups.
 */
@Singleton
class FediverseRepository @Inject constructor(
    @MetadataClient private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json".toMediaType()

    private suspend fun misskeyPost(
        instanceUrl: String,
        endpoint: String,
        body: JsonObject,
    ): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$instanceUrl/api/$endpoint")
            .post(json.encodeToString(JsonObject.serializer(), body).toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.parseToJsonElement(text)
                        .jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
                }.getOrNull()
                throw message?.let { IOException(it) }
                    ?: FediverseException(R.string.fediverse_error_http, response.code)
            }
            // Misskey answers some endpoints with 204 and no body at all.
            if (response.code == 204 || text.isBlank()) {
                JsonObject(emptyMap())
            } else {
                json.parseToJsonElement(text)
            }
        }
    }

    /** Completes a MiAuth session and returns the connected account. */
    suspend fun completeMiAuth(instanceUrl: String, sessionUuid: String): FediverseAccount {
        val check: MiAuthCheckDto = json.decodeFromJsonElement(
            MiAuthCheckDto.serializer(),
            // The empty object is load-bearing: Misskey rejects a POST with no body at all.
            misskeyPost(instanceUrl, "miauth/$sessionUuid/check", buildJsonObject { }),
        )
        val token = check.token
        if (!check.ok || token.isNullOrBlank()) {
            throw FediverseException(R.string.fediverse_error_authorization_declined)
        }
        val user: MiUserDto = json.decodeFromJsonElement(
            MiUserDto.serializer(),
            misskeyPost(instanceUrl, "i", buildJsonObject { put("i", token) }),
        )
        val username = user.username
        if (username.isNullOrBlank()) {
            throw FediverseException(R.string.fediverse_error_invalid_response)
        }
        return FediverseAccount(
            id = UUID.randomUUID().toString(),
            instanceUrl = instanceUrl,
            username = username,
            apiToken = token,
        )
    }

    /**
     * Resolves a remote ActivityPub URL to a note id local to the user's own instance.
     *
     * This indirection is the whole reason cross-software interaction works at all: a Misskey
     * instance cannot react to a PeerTube URL directly, only to an object it has itself ingested.
     */
    private suspend fun resolveApObject(account: FediverseAccount, apUrl: String): String {
        val data: ApShowDto = json.decodeFromJsonElement(
            ApShowDto.serializer(),
            misskeyPost(
                account.instanceUrl,
                "ap/show",
                buildJsonObject {
                    put("i", account.apiToken)
                    put("uri", apUrl)
                },
            ),
        )
        return data.obj?.id ?: throw FediverseException(R.string.fediverse_error_unresolvable)
    }

    /** Reacts with a heart. Deliberately a reaction, not a renote — those are different acts. */
    suspend fun like(account: FediverseAccount, apUrl: String) {
        val noteId = resolveApObject(account, apUrl)
        misskeyPost(
            account.instanceUrl,
            "notes/reactions/create",
            buildJsonObject {
                put("i", account.apiToken)
                put("noteId", noteId)
                put("reaction", "❤")
            },
        )
    }

    suspend fun boost(account: FediverseAccount, apUrl: String) {
        val noteId = resolveApObject(account, apUrl)
        misskeyPost(
            account.instanceUrl,
            "notes/create",
            buildJsonObject {
                put("i", account.apiToken)
                put("renoteId", noteId)
            },
        )
    }

    suspend fun comment(account: FediverseAccount, apUrl: String, text: String) {
        val noteId = resolveApObject(account, apUrl)
        misskeyPost(
            account.instanceUrl,
            "notes/create",
            buildJsonObject {
                put("i", account.apiToken)
                put("inReplyToId", noteId)
                put("text", text)
            },
        )
    }

    suspend fun stats(account: FediverseAccount, apUrl: String): VideoStats = coroutineScope {
        val noteId = resolveApObject(account, apUrl)
        val noteDeferred = async {
            json.decodeFromJsonElement(
                NoteShowDto.serializer(),
                misskeyPost(
                    account.instanceUrl,
                    "notes/show",
                    buildJsonObject {
                        put("i", account.apiToken)
                        put("noteId", noteId)
                    },
                ),
            )
        }
        val repliesDeferred = async {
            json.decodeFromJsonElement(
                ListSerializer(ReplyDto.serializer()),
                misskeyPost(
                    account.instanceUrl,
                    "notes/replies",
                    buildJsonObject {
                        put("i", account.apiToken)
                        put("noteId", noteId)
                        put("limit", REPLY_LIMIT)
                    },
                ),
            )
        }
        val note = noteDeferred.await()
        VideoStats(
            reactions = note.reactions,
            renoteCount = note.renoteCount,
            replies = repliesDeferred.await().map {
                CommentItem(
                    id = it.id,
                    username = it.user.username,
                    host = it.user.host,
                    text = it.text,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    /** Whether the account already follows [actorUrl]. */
    suspend fun isFollowing(account: FediverseAccount, actorUrl: String): Boolean {
        val userId = runCatching { resolveApActor(account, actorUrl) }.getOrNull() ?: return false
        val detail: MiUserDetailDto = json.decodeFromJsonElement(
            MiUserDetailDto.serializer(),
            misskeyPost(
                account.instanceUrl,
                "users/show",
                buildJsonObject {
                    put("i", account.apiToken)
                    put("userId", userId)
                },
            ),
        )
        return detail.isFollowing
    }

    suspend fun follow(account: FediverseAccount, actorUrl: String) {
        val userId = resolveApActor(account, actorUrl)
        misskeyPost(
            account.instanceUrl,
            "following/create",
            buildJsonObject {
                put("i", account.apiToken)
                put("userId", userId)
            },
        )
    }

    private suspend fun resolveApActor(account: FediverseAccount, actorUrl: String): String =
        resolveApObject(account, actorUrl)

    private companion object {
        const val REPLY_LIMIT = 10
    }
}
