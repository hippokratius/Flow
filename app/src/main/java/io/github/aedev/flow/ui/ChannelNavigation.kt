package io.github.aedev.flow.ui

import androidx.navigation.NavHostController
import java.net.URLDecoder

/**
 * Opens the channel page for [channelIdOrHandle], whichever source it belongs to.
 *
 * The YouTube-specific name is upstream's and is kept on purpose: eight call sites in
 * `FlowNavigation` use it, and renaming them would be merge debt for no behavioural gain. The
 * dispatch below is what makes a federated channel reachable at all — before it, tapping the
 * channel name under a PeerTube video did nothing, because `youtubeChannelUrl` correctly refuses
 * to invent a YouTube URL for a `peertube_…` id.
 */
internal fun NavHostController.navigateToYoutubeChannel(channelIdOrHandle: String) {
    peerTubeChannelRoute(channelIdOrHandle)?.let { route ->
        if (currentPeerTubeChannelId() != channelIdOrHandle.trim()) navigate(route)
        return
    }

    val targetUrl = youtubeChannelUrl(channelIdOrHandle) ?: return
    val currentUrl = currentBackStackEntry
        ?.takeIf { it.destination.route == YOUTUBE_CHANNEL_ROUTE }
        ?.arguments
        ?.getString("channelUrl")
        ?.let(::decodeNavArgument)
        ?.let(::youtubeChannelUrl)

    if (currentUrl == targetUrl) return
    youtubeChannelRoute(targetUrl)?.let(::navigate)
}

/** The channel already on screen, so tapping its own name does not stack a duplicate entry. */
private fun NavHostController.currentPeerTubeChannelId(): String? = currentBackStackEntry
    ?.takeIf { it.destination.route == PEERTUBE_CHANNEL_ROUTE }
    ?.arguments
    ?.getString("channelId")
    ?.let(::decodeNavArgument)

private fun decodeNavArgument(value: String): String =
    runCatching { URLDecoder.decode(value, Charsets.UTF_8.name()) }.getOrDefault(value)
