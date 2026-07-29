package io.github.aedev.flow.data.model

import io.github.aedev.flow.data.source.SourceKind
import org.schabi.newpipe.extractor.Page

data class VideoCollaborator(
    val name: String,
    val channelId: String = "",
    val thumbnailUrl: String = "",
    val subscriberCountText: String = ""
)

data class Video(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val duration: Int, // in seconds
    val viewCount: Long,
    val likeCount: Long = 0,
    val uploadDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String = "",
    val channelThumbnailUrl: String = "",
    val tags: List<String> = emptyList(),
    val isMusic: Boolean = false,
    val isLive: Boolean = false,
    val isShort: Boolean = false,
    val isUpcoming: Boolean = false,
    val commentCountText: String = "",
    val channelThumbnailUrls: List<String> = emptyList(),
    val collaborators: List<VideoCollaborator> = emptyList(),
    // Transient: when this video was added to the playlist currently being viewed. Not persisted
    // on the video row — populated only by playlist-scoped queries.
    val addedAtInPlaylist: Long? = null,
    // Which backend this came from. Always derivable from [id] via ContentId, but carried here so
    // hot paths (list item keys, feed merging) don't re-parse the id on every recomposition.
    val source: SourceKind = SourceKind.YOUTUBE,
    // Host of the originating instance for federated sources. Null for YouTube and local media.
    val instanceHost: String? = null
)

data class Channel(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val subscriberCount: Long,
    val description: String = "",
    val isSubscribed: Boolean = false,
    val isMusic: Boolean = false,
    val url: String = "", // Full channel URL for navigation
    val bannerUrl: String = "",
    val source: SourceKind = SourceKind.YOUTUBE,
    val instanceHost: String? = null
)

data class Playlist(
    val id: String,
    val name: String,
    val thumbnailUrl: String,
    val videoCount: Int,
    val description: String = "",
    val videos: List<Video> = emptyList(),
    val isLocal: Boolean = true
)
data class Comment(
    val id: String,
    val author: String,
    val authorThumbnail: String,
    val text: String,
    val likeCount: Int,
    val publishedTime: String,
    val replies: List<Comment> = emptyList(),
    val replyCount: Int = 0,
    val repliesPage: Page? = null,
    val isPinned: Boolean = false,
    val continuationToken: String? = null,
    val authorChannelId: String = ""
)

data class SearchResult(
    val videos: List<Video> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

enum class SearchFilter {
    ALL, VIDEOS, CHANNELS, PLAYLISTS
}

