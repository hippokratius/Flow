package io.github.aedev.flow.utils

import org.schabi.newpipe.extractor.Image

fun List<Image>?.bestImageUrl(): String =
    this.orEmpty()
        .maxByOrNull { maxOf(it.width, it.height) }
        ?.url
        .orEmpty()

fun List<Image>?.distinctBestImageUrls(limit: Int = 2): List<String> =
    this.orEmpty()
        .asSequence()
        .filter { !it.url.isNullOrBlank() }
        .sortedByDescending { maxOf(it.width, it.height) }
        .distinctBy { it.url.avatarImageIdentityKey() }
        .mapNotNull { it.url }
        .take(limit)
        .toList()

/** Compiled once: this runs per avatar URL, per card, on every composition of the feed. */
private val AVATAR_SIZE_SUFFIX = Regex("=s\\d+.*$")

internal fun String?.avatarImageIdentityKey(): String =
    orEmpty()
        .substringBefore("?")
        .replace(AVATAR_SIZE_SUFFIX, "")
