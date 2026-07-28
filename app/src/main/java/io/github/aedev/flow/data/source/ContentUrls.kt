/*
 * This file is part of TubeHub, a fork of Flow.
 * Copyright (C) 2026 TubeHub contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package io.github.aedev.flow.data.source

/**
 * Canonical web URLs per source.
 *
 * There are ~50 places that build a YouTube watch URL by hand. They are deliberately *not* swept:
 * a call site only needs converting once content from another source can actually reach it, which
 * is share, copy-link, and "open in browser". Converting the rest would be churn against upstream
 * for no behavioural gain.
 */
fun watchUrl(id: ContentId): String = when (id.kind) {
    SourceKind.PEERTUBE -> "https://${id.instanceHost}/w/${id.nativeId}"
    SourceKind.LOCAL -> ""
    SourceKind.YOUTUBE -> "https://www.youtube.com/watch?v=${id.raw}"
}

fun channelUrl(id: ContentId, channelId: String): String = when (id.kind) {
    SourceKind.PEERTUBE -> "https://${id.instanceHost}/c/$channelId"
    SourceKind.LOCAL -> ""
    SourceKind.YOUTUBE -> "https://www.youtube.com/channel/$channelId"
}
