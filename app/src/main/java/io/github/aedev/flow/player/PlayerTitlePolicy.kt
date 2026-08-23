/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.player

/**
 * Which of the several titles the player holds is the one to show.
 *
 * Playing a video produces two answers about the same upload, and they are not in the same language.
 * Stream extraction asks InnerTube with `YouTubeLocale.EXTRACTION` — `hl=en`, on purpose, so the
 * shape of the response does not change with the user's locale — and the player then took the title
 * out of that response, wrote it over the localized one it already had, put it in the media
 * notification, and let the playback-position writer persist it into the watch history every ten
 * seconds. For a channel that publishes German and English metadata, the German title was fetched
 * and then thrown away.
 *
 * So an extraction title is a last resort here: it is the right answer only when there is no other.
 */
object PlayerTitlePolicy {

    /**
     * [deArrowTitle] is the user's own opt-in and outranks everything. [localizedTitle] is any title
     * from a request that carried the user's language — the NewPipe extraction, the watch metadata.
     * [cachedTitle] is what the list the user tapped already showed. [extractionTitle] comes from
     * the English-pinned player response.
     */
    fun resolveDisplayTitle(
        deArrowTitle: String? = null,
        localizedTitle: String? = null,
        cachedTitle: String? = null,
        extractionTitle: String? = null,
    ): String = sequenceOf(deArrowTitle, localizedTitle, cachedTitle, extractionTitle)
        .firstOrNull { !it.isNullOrBlank() }
        .orEmpty()
}
