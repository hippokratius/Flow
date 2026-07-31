package io.github.aedev.flow.ui.screens.player.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.contentId
import io.github.aedev.flow.data.repository.VideoCollaboratorResolver
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.rememberDeArrowResult
import io.github.aedev.flow.ui.components.CommentsPreview
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.AddToPlaylistDialog
import io.github.aedev.flow.ui.components.VideoInfoSection
import io.github.aedev.flow.ui.components.VideoSourceTabs
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * The playing video's channel id, preferring the extraction over the feed item.
 *
 * Was repeated at five call sites inside [VideoInfoContent]; pulled out when the source switch made
 * each of them read "the channel of the side currently shown" instead.
 */
private fun channelIdOf(uiState: VideoPlayerUiState, video: Video): String =
    uiState.streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId

@Composable
fun VideoInfoContent(
    video: Video,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    screenState: PlayerScreenState,
    comments: List<Comment>,
    commentsEnabled: Boolean = true,
    showCommentsPreview: Boolean = true,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onChannelClick: (String) -> Unit
) {
    var showAddToPlaylistDialog by remember(video.id) { mutableStateOf(false) }
    val playerPrefs = remember { PlayerPreferences(context) }
    val shareWithoutText by playerPrefs.shareWithoutText.collectAsState(initial = false)
    val deArrowEnabled by playerPrefs.deArrowEnabled.collectAsState(initial = false)
    val deArrowResult = rememberDeArrowResult(video.id, deArrowEnabled)
    val resolvedVideoTitle = deArrowResult?.title ?: uiState.streamInfo?.name ?: video.title
    val resolvedCollaborators by produceState(
        initialValue = video.collaborators,
        key1 = video.id,
        key2 = video.collaborators
    ) {
        value = if (video.collaborators.size > 1) {
            video.collaborators
        } else {
            VideoCollaboratorResolver.resolve(video.id)
        }
    }
    val resolvedChannelName = remember(video.channelName, uiState.streamInfo?.uploaderName, resolvedCollaborators) {
        resolvedCollaborators
            .map { it.name }
            .filter { it.isNotBlank() }
            .takeIf { it.size > 1 }
            ?.joinToString(" and ")
            ?: uiState.streamInfo?.uploaderName
            ?: video.channelName
    }
    val streamUploadDate = uiState.streamInfo?.let { streamInfo ->
        val rawDate = streamInfo.textualUploadDate?.takeIf { it.isNotBlank() }
            ?: streamInfo.uploadDate?.toString()
        val isArchivedLivestream = streamInfo.streamType == StreamType.POST_LIVE_STREAM
        when {
            rawDate.isNullOrBlank() -> null
            isArchivedLivestream && !rawDate.startsWith("Streamed", ignoreCase = true) -> "Streamed $rawDate"
            else -> rawDate
        }
    }
    val dialogVideo = remember(video, uiState.streamInfo, uiState.channelAvatarUrl, resolvedVideoTitle) {
        uiState.streamInfo?.let { streamInfo ->
            Video(
                id = streamInfo.id ?: video.id,
                title = resolvedVideoTitle,
                channelName = streamInfo.uploaderName ?: video.channelName,
                channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
                thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
                duration = streamInfo.duration.toInt(),
                viewCount = streamInfo.viewCount,
                likeCount = streamInfo.likeCount,
                uploadDate = streamUploadDate ?: streamInfo.uploadDate?.run {
                    try {
                        val date = java.util.Date.from(offsetDateTime().toInstant())
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        sdf.format(date)
                    } catch (e: Exception) {
                        video.uploadDate
                    }
                } ?: video.uploadDate,
                description = streamInfo.description?.content ?: video.description,
                channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
                timestamp = video.timestamp,
                isMusic = video.isMusic
            )
        } ?: video
    }

    // ── Error details panel ─────────────────────────────────────────────────
    if (uiState.error != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.errorHint.isNullOrBlank()) {
                    Text(
                        text = uiState.errorHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                }
                // Row 1: Retry + Copy Logs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.retryLoadVideo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0000),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.retry), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val ok = PlayerDiagnostics.copyToClipboard(context)
                            Toast.makeText(
                                context,
                                if (ok) context.getString(R.string.logs_copied) else context.getString(R.string.logs_copy_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.copy_logs), fontSize = 13.sp)
                    }
                }
                // Row 2: Open in YouTube (full width)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=${video.id}")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_open_in_youtube), fontSize = 13.sp)
                }
            }
        }
    }

    val downloadedVideoIds by viewModel.downloadedVideoIds.collectAsState()
    val isVideoDownloaded = remember(downloadedVideoIds, video.id) { downloadedVideoIds.contains(video.id) }
    val isVideoSaved by remember(video.id) { viewModel.isVideoSavedToAnyPlaylist(video.id) }
        .collectAsState(initial = false)

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = dialogVideo,
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }

    /*
     * The PeerTube/YouTube switch under the title selects which copy of this upload the panel below
     * describes. `shownVideo` is non-null only while the counterpart is selected — in every other
     * case, including every video that has no counterpart at all, the arguments below are exactly
     * what they were before the switch existed.
     *
     * `streamInfo` never applies to the counterpart: it is the NewPipe extraction of the video that
     * is *playing*.
     */
    val counterpartVideo by viewModel.counterpartVideo.collectAsState()
    val shownVideo = uiState.infoVideo
    val shown = shownVideo ?: video
    val shownChannelId = if (shownVideo != null) shownVideo.channelId else channelIdOf(uiState, video)
    val shownChannelName = if (shownVideo != null) shownVideo.channelName else resolvedChannelName
    val shownChannelThumb = if (shownVideo != null) {
        shownVideo.channelThumbnailUrl
    } else {
        uiState.channelAvatarUrl?.takeIf { it.isNotEmpty() } ?: video.channelThumbnailUrl
    }
    val shownLikeState = if (shownVideo != null) uiState.infoLikeState else uiState.likeState
    val shownIsSubscribed = if (shownVideo != null) uiState.infoSubscribed else uiState.isSubscribed

    val toggleShownSubscription = {
        viewModel.toggleSubscription(shownChannelId, shownChannelName, shownChannelThumb)
    }

    VideoInfoSection(
        video = shown,
        title = resolvedVideoTitle,
        sourceSwitch = {
            VideoSourceTabs(
                playingVideo = video,
                counterpartVideo = counterpartVideo,
                selectedVideoId = shown.id,
                onSelect = { viewModel.showInfoFor(it) }
            )
        },
        viewCount = if (shownVideo != null) shown.viewCount else (uiState.streamInfo?.viewCount ?: video.viewCount),
        uploadDate = if (shownVideo != null) shown.uploadDate else (streamUploadDate ?: video.uploadDate),
        description = if (shownVideo != null) {
            shown.description
        } else {
            uiState.streamInfo?.description?.content ?: video.description
        },
        isUpcoming = uiState.isUpcoming && shownVideo == null,
        channelName = shownChannelName,
        channelAvatarUrl = shownChannelThumb,
        channelAvatarUrls = if (shownVideo != null) emptyList() else video.channelThumbnailUrls,
        // Collaborators come out of the YouTube extraction of the playing video.
        collaborators = if (shownVideo != null) emptyList() else resolvedCollaborators,
        subscriberCount = if (shownVideo != null) uiState.infoChannel?.subscriberCount else uiState.channelSubscriberCount,
        isSubscribed = shownIsSubscribed,
        isNotificationsEnabled = uiState.isNotificationsEnabled && shownVideo == null,
        likeState = shownLikeState ?: "NONE",
        likeCount = if (shownVideo != null) shown.likeCount else (uiState.streamInfo?.likeCount ?: video.likeCount),
        // Only YouTube has one, and it belongs to the playing video's extraction.
        dislikeCount = if (shownVideo != null) null else uiState.dislikeCount,
        onLikeClick = {
            val thumbnailUrl = if (shownVideo != null) {
                shown.thumbnailUrl
            } else {
                uiState.streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
            }
            when (shownLikeState) {
                "LIKED" -> viewModel.removeLikeState(shown.id)
                else -> viewModel.likeVideo(
                    shown.id,
                    if (shownVideo != null) shown.title else resolvedVideoTitle,
                    thumbnailUrl,
                    shownChannelName
                )
            }
        },
        onDislikeClick = {
            when (shownLikeState) {
                "DISLIKED" -> viewModel.removeLikeState(shown.id)
                else -> viewModel.dislikeVideo(shown.id)
            }
        },
        onSubscribeClick = {
            toggleShownSubscription()
            scope.launch {
                val message = if (shownIsSubscribed)
                    context.getString(R.string.unsubscribed_from, shownChannelName)
                else
                    context.getString(R.string.subscribed_to, shownChannelName)

                val result = snackbarHostState.showSnackbar(
                    message,
                    actionLabel = if (shownIsSubscribed) context.getString(R.string.undo) else null
                )

                if (result == SnackbarResult.ActionPerformed && shownIsSubscribed) {
                    toggleShownSubscription()
                }
            }
        },
        onUnsubscribeClick = {
            toggleShownSubscription()
            scope.launch {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.unsubscribed_from, shownChannelName)
                )
            }
        },
        onNotificationChange = { enabled ->
            viewModel.setNotificationEnabled(shownChannelId, enabled)
        },
        onChannelClick = { onChannelClick(shownChannelId) },
        onCollaboratorClick = onChannelClick,
        onSaveClick = { showAddToPlaylistDialog = true },
        onShareClick = {
            val shareText = if (shareWithoutText) {
                context.getString(R.string.share_link_only_template, video.id)
            } else {
                context.getString(R.string.check_out_video_template, resolvedVideoTitle, video.id)
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, resolvedVideoTitle)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
        },
        onDownloadClick = { screenState.showDownloadDialog = true },
        isSaved = isVideoSaved,
        isDownloaded = isVideoDownloaded,
        onBackgroundPlayClick = { viewModel.startBackgroundPlayback() },
        onCopyLinkClick = {
            val url = "https://www.youtube.com/watch?v=${video.id}"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("video_link", url))
            Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
        },
        onCopyLinkAtTimeClick = {
            val positionMs = EnhancedPlayerManager.getInstance().getCurrentPosition()
            val positionSeconds = positionMs / 1000L
            val url = "https://www.youtube.com/watch?v=${video.id}&t=${positionSeconds}s"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("video_link_at_time", url))
            Toast.makeText(context, context.getString(R.string.link_with_timestamp_copied), Toast.LENGTH_SHORT).show()
        },
        onDescriptionClick = { screenState.showDescriptionSheet = true }
    )

    // Fediverse actions for federated videos. A sibling of VideoInfoSection rather than more
    // parameters on it — that composable already takes 25. Renders nothing without an account.
    if (video.source == io.github.aedev.flow.data.source.SourceKind.PEERTUBE) {
        io.github.aedev.flow.fediverse.ui.FediverseActionBar(
            apUrl = io.github.aedev.flow.data.source.watchUrl(video.id.contentId)
        )
    }

    // "This creator also publishes on PeerTube", when the user has said so. Renders nothing
    // otherwise, so it costs an unlinked channel nothing but a lookup in a cached list. Shown for
    // the channel on screen, which the switch above may have changed.
    io.github.aedev.flow.ui.components.LinkedChannelRow(
        channelId = shownChannelId,
        onOpenLinkedChannel = onChannelClick
    )

    if (uiState.isLiveChatAvailable) {
        io.github.aedev.flow.ui.components.LiveChatPreview(
            onClick = { screenState.showLiveChatSheet = true }
        )
    }

    if (commentsEnabled) {
        CommentsPreview(
            latestComment = if (showCommentsPreview) comments.firstOrNull()?.text else null,
            authorAvatar = if (showCommentsPreview) comments.firstOrNull()?.authorThumbnail else null,
            showPreviewText = showCommentsPreview,
            onClick = { screenState.showCommentsSheet = true }
        )
    }
}

/**
 * Related videos content for LazyListScope.
 */
fun LazyListScope.relatedVideosContent(
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH
) {
    // Video items
    items(
        count = relatedVideos.size,
        key = { index -> relatedVideos[index].id }
    ) { index ->
        val relatedVideo = relatedVideos[index]
        when (cardStyle) {
            PlayerRelatedCardStyle.COMPACT -> CompactVideoCard(
                video = relatedVideo,
                onClick = { onVideoClick(relatedVideo) },
                onChannelClick = onChannelClick
            )
            PlayerRelatedCardStyle.FULL_WIDTH -> VideoCardFullWidth(
                video = relatedVideo,
                onClick = { onVideoClick(relatedVideo) },
                onChannelClick = onChannelClick
            )
        }
    }
}

/**
 * Related videos grid content for LazyListScope.
 */
fun LazyListScope.relatedVideosGridContent(
    relatedVideos: List<Video>,
    columns: Int,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH
) {
    val chunkedVideos = relatedVideos.chunked(columns)
    
    items(
        count = chunkedVideos.size,
        key = { index -> chunkedVideos[index].joinToString { it.id } }
    ) { index ->
        val rowVideos = chunkedVideos[index]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (video in rowVideos) {
                Box(modifier = Modifier.weight(1f)) {
                    when (cardStyle) {
                        PlayerRelatedCardStyle.COMPACT -> CompactVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick
                        )
                        PlayerRelatedCardStyle.FULL_WIDTH -> VideoCardFullWidth(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = onChannelClick
                        )
                    }
                }
            }
            val emptySpaces = columns - rowVideos.size
            for (i in 0 until emptySpaces) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * Creates a complete Video object from StreamInfo if available
 */
fun createCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState
): Video {
    val streamInfo = uiState.streamInfo
    return if (streamInfo != null) {
        Video(
            id = streamInfo.id ?: video.id,
            title = streamInfo.name ?: video.title,
            channelName = streamInfo.uploaderName ?: video.channelName,
            channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
            thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
            duration = streamInfo.duration.toInt(),
            viewCount = streamInfo.viewCount,
            uploadDate = streamInfo.uploadDate?.toString() ?: video.uploadDate,
            description = streamInfo.description?.content ?: video.description,
            channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl
        )
    } else {
        video
    }
}

/**
 * Remember-able version of createCompleteVideo
 */
@Composable
fun rememberCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState
): Video {
    return remember(uiState.streamInfo, video) {
        createCompleteVideo(video, uiState)
    }
}
