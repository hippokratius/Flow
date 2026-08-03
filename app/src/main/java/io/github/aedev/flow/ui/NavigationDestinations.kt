package io.github.aedev.flow.ui

import io.github.aedev.flow.data.local.DEFAULT_NAV_TAB_ORDER
import io.github.aedev.flow.data.source.SourceKind
import io.github.aedev.flow.data.source.contentId
import java.net.URI
import java.net.URLEncoder

internal data class NavigationVisibility(
    val home: Boolean = true,
    val shorts: Boolean = true,
    val music: Boolean = true,
    val search: Boolean = false,
    val categories: Boolean = false
)

internal fun visibleNavTabIndices(
    order: List<Int>,
    visibility: NavigationVisibility
): List<Int> {
    val enabled = buildSet {
        if (visibility.home) add(0)
        if (visibility.shorts) add(1)
        if (visibility.music) add(2)
        add(3)
        add(4)
        if (visibility.search) add(5)
        if (visibility.categories) add(6)
    }
    return (order + DEFAULT_NAV_TAB_ORDER).distinct().filter(enabled::contains)
}

internal fun resolveDefaultNavTabIndex(
    preferredIndex: Int,
    order: List<Int>,
    visibility: NavigationVisibility
): Int {
    val visible = visibleNavTabIndices(order, visibility)
    return preferredIndex.takeIf(visible::contains) ?: visible.first()
}

internal fun navRouteForIndex(index: Int): String = when (index) {
    0 -> "home"
    1 -> "shorts"
    2 -> "music"
    3 -> "subscriptions"
    4 -> "library"
    5 -> "search"
    6 -> "categories"
    else -> "home"
}

internal fun youtubeChannelUrl(channelIdOrHandle: String): String? {
    val value = channelIdOrHandle.trim()
    if (value.isEmpty()) return null
    // A channel from another source is not a YouTube handle. Without this guard the `else` branch
    // below turns e.g. "peertube_tilvids.com_news" into "https://www.youtube.com/@peertube_..." and
    // navigates to a page that cannot exist. Callers treat null as "no in-app channel page".
    if (value.contentId.kind != SourceKind.YOUTUBE) return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> normalizeYoutubeChannelUrl(value)
        value.startsWith("UC") -> "https://www.youtube.com/channel/$value"
        value.startsWith("@") -> "https://www.youtube.com/$value"
        else -> "https://www.youtube.com/@$value"
    }
}

/**
 * Route pattern of the YouTube channel page.
 *
 * `plain` forces the untouched YouTube page for a channel that has a PeerTube counterpart, which is
 * how the shared page offers "watch on YouTube only" without bouncing straight back.
 */
internal const val YOUTUBE_CHANNEL_ROUTE = "channel?url={channelUrl}&plain={plain}"

/** Route pattern of the channel-links settings screen. Both arguments are optional. */
internal const val CHANNEL_LINKS_ROUTE =
    "settings/channel_links?youtubeId={youtubeId}&youtubeName={youtubeName}"

/**
 * Route to the channel-links screen, optionally pre-filled with the YouTube channel in hand.
 *
 * Called with arguments from a channel page — where the app already knows whose counterpart is being
 * looked for — and without any from the settings list.
 */
internal fun channelLinksRoute(
    youtubeChannelId: String = "",
    youtubeChannelName: String = "",
): String {
    val encode = { value: String -> URLEncoder.encode(value.trim(), Charsets.UTF_8.name()) }
    return "settings/channel_links?youtubeId=${encode(youtubeChannelId)}" +
        "&youtubeName=${encode(youtubeChannelName)}"
}

/**
 * The `UC…` id inside a YouTube channel URL, or null when the URL addresses the channel some other
 * way.
 *
 * Used to ask whether a channel has a linked counterpart, which is keyed on the id. A `@handle` URL
 * carries no id, so those channels are simply not gated — the same behaviour as before links existed.
 */
internal fun youtubeChannelIdFromUrl(url: String): String? {
    val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    if (segments.size < 2 || segments.first() != "channel") return null
    return segments[1].takeIf { it.isNotBlank() }
}

/** Route pattern of the PeerTube channel page. Declared here so the arg name has one owner. */
internal const val PEERTUBE_CHANNEL_ROUTE = "peertubeChannel?id={channelId}"

/**
 * In-app route to a PeerTube channel, or null when the id belongs to another source.
 *
 * Kept pure — no `android.net.Uri`, no `NavController` — so the dispatch in
 * [navigateToYoutubeChannel] is unit testable.
 */
internal fun peerTubeChannelRoute(channelId: String): String? {
    val id = channelId.trim().contentId
    if (id.kind != SourceKind.PEERTUBE) return null
    if (id.instanceHost.isNullOrBlank() || id.nativeId.isBlank()) return null
    return "peertubeChannel?id=${URLEncoder.encode(id.raw, Charsets.UTF_8.name())}"
}

internal fun youtubeChannelRoute(channelIdOrHandle: String): String? =
    youtubeChannelUrl(channelIdOrHandle)?.let { channelUrl ->
        "channel?url=${URLEncoder.encode(channelUrl, Charsets.UTF_8.name())}"
    }

private fun normalizeYoutubeChannelUrl(url: String): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val host = uri.host?.lowercase().orEmpty()
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return url

    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return url

    val channelValue = when {
        segments.first() == "channel" -> segments.getOrNull(1)
        segments.first().startsWith("@") -> segments.first()
        else -> null
    } ?: return url

    return when {
        channelValue.startsWith("UC") -> "https://www.youtube.com/channel/$channelValue"
        channelValue.startsWith("@") -> "https://www.youtube.com/$channelValue"
        else -> "https://www.youtube.com/@$channelValue"
    }
}
