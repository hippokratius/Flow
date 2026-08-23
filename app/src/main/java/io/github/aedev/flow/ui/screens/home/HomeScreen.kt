package io.github.aedev.flow.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.screens.notifications.NotificationViewModel
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.player.DeepFlowManager

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.ui.TabScrollEventBus

private data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int
)

@Composable
private fun rememberHomeLayoutConfig(maxWidth: Dp): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, maxWidth) {
        val shortsShelfAfterIndex = when {
            maxWidth < 480.dp -> 1
            maxWidth < 700.dp -> 2
            maxWidth < 900.dp -> 2
            maxWidth < 1200.dp -> 3
            else -> 4
        }
        HomeLayoutConfig(
            columns = base.columns,
            contentPadding = base.contentPadding,
            cardSpacing = base.cardSpacing,
            shortsShelfAfterIndex = shortsShelfAfterIndex,
            shimmerColumns = base.columns
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onOpenShortsFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val unreadNotifications by notificationViewModel.unreadCount.collectAsStateWithLifecycle()
    val preferences = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val homeViewMode by preferences.homeViewMode.collectAsStateWithLifecycle(
        initialValue = io.github.aedev.flow.data.local.HomeViewMode.GRID
    )
    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsStateWithLifecycle(initialValue = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsStateWithLifecycle(initialValue = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsStateWithLifecycle(initialValue = true)
    val deepFlowActive by preferences.deepFlowActive.collectAsStateWithLifecycle(initialValue = false)

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    LifecycleStartEffect(viewModel, homeFeedEnabled) {
        if (homeFeedEnabled) {
            viewModel.onHomeVisible()
        } else {
            viewModel.onHomeHidden()
        }
        onStopOrDispose { viewModel.onHomeHidden() }
    }

    val videoIndexById = remember(uiState.videos) {
        buildMap(uiState.videos.size) {
            uiState.videos.forEachIndexed { index, video -> put(video.id, index) }
        }
    }
    LaunchedEffect(gridState, videoIndexById) {
        snapshotFlow {
            var lastVisibleVideoIndex = -1
            gridState.layoutInfo.visibleItemsInfo.forEach { item ->
                val index = videoIndexById[item.key as? String] ?: return@forEach
                if (index > lastVisibleVideoIndex) lastVisibleVideoIndex = index
            }
            lastVisibleVideoIndex
        }
            .distinctUntilChanged()
            .collect(viewModel::onHomeViewportChanged)
    }

    // Viewport impressions: only items dwelt in view are recorded as "shown".
    LaunchedEffect(gridState) {
        // The observed expression re-runs on every frame the layout changes, so it must stay cheap:
        // building the key list up front allocated one list per frame and compared it against the
        // previous one, with the debounce sitting uselessly downstream. A scrolled-to-rest viewport
        // is what the debounce waits for anyway, so the keys can be read once it fires.
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.layoutInfo.visibleItemsInfo.size }
            .debounce(500)
            .collect {
                viewModel.recordImpressions(
                    gridState.layoutInfo.visibleItemsInfo.mapNotNull { item -> item.key as? String }
                )
            }
    }

    LaunchedEffect(refreshHomeOnReselect) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "home" }
            .collectLatest {
                gridState.animateScrollToItem(0)
                if (refreshHomeOnReselect) {
                    viewModel.refreshFeed()
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showAppLogoIcon) {
                            FlowHeaderLogoIcon(
                                isDeepFlowActive = deepFlowActive,
                                onToggleDeepFlow = {
                                    coroutineScope.launch {
                                        DeepFlowManager.toggle(context)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            stringResource(R.string.app_brand_name_uppercase),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = stringResource(R.string.notifications),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (unreadNotifications > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                            .size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadNotifications > 9) stringResource(R.string.notification_badge_9_plus) else unreadNotifications.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        ResettableHomePullToRefreshBox(
            resetKey = uiState.isLoading && uiState.videos.isEmpty(),
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isListView = homeViewMode == io.github.aedev.flow.data.local.HomeViewMode.LIST
            val layoutConfig = rememberHomeLayoutConfig(maxWidth)
            val gridCells = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.columns)

            when {
                !homeFeedEnabled -> {
                    FeedDisabledState(modifier = Modifier.fillMaxSize())
                }

                uiState.isLoading && uiState.videos.isEmpty() -> {
                    // Initial loading state — matches grid layout
                    LazyVerticalGrid(
                        columns = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.shimmerColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = if (isListView) 0.dp else layoutConfig.contentPadding,
                            end = if (isListView) 0.dp else layoutConfig.contentPadding,
                            top = 8.dp,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        userScrollEnabled = false
                    ) {
                        items(12) {
                            if (isListView) {
                                ShimmerVideoCardHorizontal()
                            } else if (layoutConfig.shimmerColumns == 1) {
                                ShimmerVideoCardFullWidth()
                            } else {
                                ShimmerGridVideoCard()
                            }
                        }
                    }
                }

                uiState.error != null && uiState.videos.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: stringResource(R.string.error_occurred),
                        onRetry = { viewModel.retry() }
                    )
                }

                else -> {
                    // One reuse pool per card shape. Without a content type the grid may hand a
                    // shelf's retired composition to a card slot, and a structurally mismatched
                    // reuse costs more than a fresh composition. List and grid mode render
                    // different cards, so they must not share a pool either.
                    val cardContentType = if (isListView) "video_list" else "video_grid"
                    LazyVerticalGrid(
                        columns = gridCells,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = if (isListView) 0.dp else layoutConfig.contentPadding,
                            end = if (isListView) 0.dp else layoutConfig.contentPadding,
                            top = 4.dp,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing)
                    ) {
                        val videos = uiState.videos
                        if (videos.isNotEmpty()) {
                            val insertShortsAfter = layoutConfig.shortsShelfAfterIndex.coerceAtMost(videos.size)

                            // ── Videos before shelves ──
                            // Indexed rather than `take`/`drop`: those copy half the feed every
                            // time this lambda re-runs, which is on every state emission.
                            items(
                                count = insertShortsAfter,
                                key = { videos[it].id },
                                contentType = { cardContentType }
                            ) { index ->
                                val video = videos[index]
                                LaunchedEffect(
                                    video.id,
                                    video.channelId,
                                    video.channelThumbnailUrl
                                ) {
                                    viewModel.enrichChannelMetadataIfMissing(video)
                                }
                                if (isListView) {
                                    VideoCardHorizontal(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = onChannelClick
                                    )
                                } else {
                                    VideoCardFullWidth(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = onChannelClick,
                                        useInternalPadding = false
                                    )
                                }
                            }

                            // ── Continue Watching Shelf (between first videos and shorts) ──
                            if (uiState.continueWatchingVideos.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "continue_watching_shelf",
                                    contentType = "continue_watching_shelf"
                                ) {
                                    ContinueWatchingShelf(
                                        entries = uiState.continueWatchingVideos,
                                        onVideoClick = { videoId ->
                                            val entry = uiState.continueWatchingVideos.find { it.videoId == videoId }
                                            if (entry != null) {
                                                onVideoClick(
                                                    Video(
                                                        id = entry.videoId,
                                                        title = entry.title,
                                                        channelName = entry.channelName,
                                                        channelId = entry.channelId,
                                                        thumbnailUrl = entry.thumbnailUrl,
                                                        duration = (entry.duration / 1000).toInt(),
                                                        viewCount = 0L,
                                                        uploadDate = ""
                                                    )
                                                )
                                            }
                                        },
                                        onRemove = { videoId ->
                                            viewModel.removeContinueWatchingEntry(videoId)
                                        },
                                        onSeeAllClick = onNavigateToHistory
                                    )
                                }
                            }

                            // ── Shorts Shelf ──
                            if (uiState.shorts.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "shorts_shelf",
                                    contentType = "shorts_shelf"
                                ) {
                                    ShortsShelf(
                                        shorts = uiState.shorts,
                                        onShortClick = onShortClick,
                                        onSeeAllClick = onOpenShortsFeed
                                    )
                                }
                            }

                            // ── Remaining Videos ──
                            items(
                                count = videos.size - insertShortsAfter,
                                key = { videos[insertShortsAfter + it].id },
                                contentType = { cardContentType }
                            ) { index ->
                                val video = videos[insertShortsAfter + index]
                                LaunchedEffect(
                                    video.id,
                                    video.channelId,
                                    video.channelThumbnailUrl
                                ) {
                                    viewModel.enrichChannelMetadataIfMissing(video)
                                }
                                if (isListView) {
                                    VideoCardHorizontal(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = onChannelClick
                                    )
                                } else {
                                    VideoCardFullWidth(
                                        video = video,
                                        onClick = { onVideoClick(video) },
                                        onChannelClick = onChannelClick,
                                        useInternalPadding = false
                                    )
                                }
                            }
                        }

                        if (uiState.isLoadingMore) {
                            item(
                                key = "loading_indicator",
                                contentType = "loading_indicator",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }

                        // End of feed indicator
                        if (!uiState.hasMorePages && uiState.videos.size > 100 && !uiState.isLoadingMore) {
                            item(
                                key = "feed_footer",
                                contentType = "feed_footer",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                FlowFeedFooter(
                                    videoCount = uiState.videos.size,
                                    onRefresh = { viewModel.refreshFeed() }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResettableHomePullToRefreshBox(
    resetKey: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    key(resetKey) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content
        )
    }
}

@Composable
private fun FeedDisabledState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartDisplay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlowFeedFooter(
    videoCount: Int,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.personalized_feed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.videos_curated_template, videoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.home_refresh_feed))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(R.string.retry))
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlowHeaderLogoIcon(
    isDeepFlowActive: Boolean,
    onToggleDeepFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val haptic = LocalHapticFeedback.current

    val bgPath = remember {
        PathParser().parsePathString(HEADER_LOGO_PLATE_PATH).toPath()
    }
    val glyphPath = remember {
        PathParser().parsePathString(HEADER_LOGO_GLYPH_PATH).toPath()
    }
    val incognitoPath = remember {
        PathParser().parsePathString(HEADER_INCOGNITO_GLYPH_PATH).toPath()
    }

    val glyphAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowGlyphAlpha"
    )
    val incognitoAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowIncognitoAlpha"
    )

    Canvas(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggleDeepFlow()
            }
        )
    ) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        drawContext.canvas.save()
        drawContext.canvas.scale(sx, sy)
        drawPath(path = bgPath, color = primaryColor)

        if (glyphAlpha > 0f) {
            drawPath(path = glyphPath, color = onPrimaryColor.copy(alpha = glyphAlpha))
        }
        if (incognitoAlpha > 0f) {
            val incognitoScale = 0.65f
            val incognitoOffset = 12f * (1f - incognitoScale)
            drawContext.canvas.save()
            drawContext.canvas.translate(incognitoOffset, incognitoOffset)
            drawContext.canvas.scale(incognitoScale, incognitoScale)
            drawPath(path = incognitoPath, color = onPrimaryColor.copy(alpha = incognitoAlpha))
            drawContext.canvas.restore()
        }

        drawContext.canvas.restore()
    }
}
