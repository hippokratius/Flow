package io.github.aedev.flow

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import io.github.aedev.flow.notification.SubscriptionCheckWorker
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import kotlinx.coroutines.flow.first
import io.github.aedev.flow.data.repository.NewPipeDownloader
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.notification.NotificationHelper
import io.github.aedev.flow.network.AppProxyManager
import io.github.aedev.flow.utils.FlowCrashHandler
import io.github.aedev.flow.utils.PerformanceDispatcher
import org.schabi.newpipe.extractor.NewPipe

import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import javax.inject.Inject
import java.security.Security
import org.conscrypt.Conscrypt
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.utils.AppLanguageManager
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.ui.screens.home.HomeFeedCache
import io.github.aedev.flow.utils.ContentLocale
import io.github.aedev.flow.utils.resolveContentLocale
import io.github.aedev.flow.utils.potoken.NewPipePoTokenProvider
import io.github.aedev.flow.discord.DiscordPresenceRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

@HiltAndroidApp
class FlowApplication : Application(), ImageLoaderFactory {
    
    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader = imageLoader
    
    companion object {
        private const val TAG = "FlowApplication"
        private const val VISITOR_DATA_KEY = "visitor_data"
        private const val VISITOR_DATA_FETCHED_AT_KEY = "visitor_data_fetched_at"
        private const val VISITOR_DATA_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1_000L
        lateinit var appContext: Context
            private set
    }

    override fun attachBaseContext(base: Context) {
        val selectedLanguage = AppLanguageManager.loadSelectedLanguageTag(base)
        super.attachBaseContext(AppLanguageManager.wrapContext(base, selectedLanguage))
    }
    
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext

        DiscordPresenceRuntime.initialize(this, okHttpClient)

        val playerPreferences = PlayerPreferences(this)
        
        // Injects modern TLS/SSL certificates so OkHttp and Ktor don't crash
        if (android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.N_MR1) {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        }

        // Install crash handler for real-time monitoring
        FlowCrashHandler.install(this)
        
        // Before anything can ask YouTube a question. The stored pair is mirrored outside DataStore
        // precisely so the first request of the process already carries it, rather than a hardcoded
        // country that the preference collector further down would correct a moment too late.
        ContentLocale.seed(this)

        try {
            ContentLocale.applyTo(NewPipeDownloader.getInstance(this))
            YoutubeStreamExtractor.setPoTokenProvider(NewPipePoTokenProvider)
            val seeded = ContentLocale.snapshot()
            Log.d(TAG, "NewPipe initialized with gl=${seeded.gl}, hl=${seeded.hl}")
        } catch (e: Exception) {
            // Log error but don't crash the app
            Log.e(TAG, "Failed to initialize NewPipe", e)
        }

        try {
            io.github.aedev.flow.utils.cipher.CipherDeobfuscator.initialize(this)
            Log.d(TAG, "CipherDeobfuscator initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CipherDeobfuscator", e)
        }
        
        // Initialize notification channels
        NotificationHelper.createNotificationChannels(this)
        Log.d(TAG, "Notification channels created")
        
        /*
        try {
            // Initialize YoutubeDL
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            Log.d(TAG, "YoutubeDL initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        }
        */
        
        // Schedule periodic subscription checks for new videos
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val savedIntervalMinutes = playerPreferences.subscriptionCheckIntervalMinutes.first()
            SubscriptionCheckWorker.schedulePeriodicCheck(
                this@FlowApplication,
                intervalMinutes = savedIntervalMinutes.toLong()
            )
        }
        
        // Schedule periodic update checks (every 12 hours) — github flavor only
        if (BuildConfig.UPDATER_ENABLED) {
            io.github.aedev.flow.notification.UpdateCheckWorker.schedulePeriodicCheck(this)
        }
        
        Log.d(TAG, "Workers scheduled successfully")

        // Fetch and cache visitor data for the lifetime of the install.
        // The X-Goog-Visitor-Id header prevents YouTube from returning empty
        // search results on tablets and fresh Android 16 installs (Issue #223).
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            playerPreferences.proxyConfig.collectLatest { proxyConfig ->
                applyProxyConfig(proxyConfig)
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
                val cached = prefs.getString(VISITOR_DATA_KEY, null)
                val cachedAt = prefs.getLong(VISITOR_DATA_FETCHED_AT_KEY, 0L)
                val cacheIsFresh = cachedAt > 0L &&
                    System.currentTimeMillis() - cachedAt < VISITOR_DATA_MAX_AGE_MS
                if (!cached.isNullOrEmpty() && cacheIsFresh) {
                    YouTube.visitorData = cached
                    Log.d(TAG, "visitorData restored from prefs")
                } else {
                    YouTube.visitorData().onSuccess { data ->
                        if (!data.isNullOrEmpty()) {
                            prefs.edit()
                                .putString(VISITOR_DATA_KEY, data)
                                .putLong(VISITOR_DATA_FETCHED_AT_KEY, System.currentTimeMillis())
                                .apply()
                            YouTube.visitorData = data
                            Log.d(TAG, "visitorData fetched and cached")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "visitorData fetch failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "visitorData init error: ${e.message}")
            }
            try {
                io.github.aedev.flow.utils.potoken.WebPoTokenSession.prewarm()
            } catch (e: Exception) {
                Log.w(TAG, "WebPoTokenSession prewarm failed: ${e.message}")
            }
        }

        // One collector for both halves of the answer, in one order. Split in two, the cache
        // invalidation could run — and the feed reload it triggers could go out — before the new
        // country had reached the clients, refilling the caches it had just cleared with the old
        // region's videos.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var lastRegion: String? = null
            combine(
                playerPreferences.appLanguage,
                playerPreferences.trendingRegion
            ) { lang, region ->
                resolveContentLocale(
                    storedRegion = region,
                    appLanguageTag = lang,
                    deviceCountry = ContentLocale.deviceRegion(),
                    deviceLanguageTag = Locale.getDefault().toLanguageTag(),
                )
            }.distinctUntilChanged().collectLatest { newLocale ->
                // The extractor followed the setting only for the language, and InnerTube only
                // until something else reassigned its locale.
                ContentLocale.apply(newLocale)
                runCatching {
                    ContentLocale.applyTo(NewPipe.getDownloader())
                }.onFailure { Log.w(TAG, "Could not apply locale to the extractor", it) }
                Log.d(TAG, "Dynamic YouTube Locale updated: gl=${newLocale.gl}, hl=${newLocale.hl}")

                val previousRegion = lastRegion
                lastRegion = newLocale.gl
                if (previousRegion == null || previousRegion == newLocale.gl) return@collectLatest

                Log.d(TAG, "Content region changed from $previousRegion to ${newLocale.gl}.")

                // Every list on screen and in the caches describes the old country now. Cleared
                // here rather than in the feed's ViewModel because that one only exists once the
                // home screen has been composed — a region changed from Settings would otherwise
                // leave the 8-hour rows in Room and the process-wide in-memory feed untouched, and
                // the home feed short-circuits its entire load on the latter.
                runCatching {
                    HomeFeedCacheRepository(this@FlowApplication).clearAll()
                    HomeFeedCache.clear()
                }.onFailure { Log.w(TAG, "Could not clear the feed caches", it) }
                FeedInvalidationBus.emit(
                    FeedInvalidationBus.Event.ContentRegionChanged(newLocale.gl)
                )

                // Last, because it is a network round trip: which country YouTube believes the user
                // is in is carried by this token, but nothing on screen should wait for it.
                invalidateVisitorData(newLocale.gl)
            }
        }

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = SubscriptionRepository.getInstance(this@FlowApplication)
                val youtubeRepository = YouTubeRepository.getInstance(playerPreferences)
                val repaired = repository.repairVideoThumbnailSubscriptions { channelId ->
                    withTimeoutOrNull(6_000L) {
                        youtubeRepository.fetchChannelAvatarById(channelId)
                    }.orEmpty()
                }
                if (repaired > 0) {
                    Log.i(TAG, "Repaired $repaired subscription thumbnails")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Subscription thumbnail repair failed: ${e.message}")
            }
        }
    }

    /**
     * Drops the visitor token minted for the previous region and fetches one for the new.
     *
     * YouTube ties a slice of what it serves to this token, so keeping it across a region change
     * would answer the new country's requests with the old country's session.
     */
    private suspend fun invalidateVisitorData(region: String) {
        val prefs = getSharedPreferences("flow_prefs", MODE_PRIVATE)
        prefs.edit()
            .remove(VISITOR_DATA_KEY)
            .remove(VISITOR_DATA_FETCHED_AT_KEY)
            .apply()
        YouTube.visitorData = null

        YouTube.visitorData().onSuccess { data ->
            if (!data.isNullOrEmpty()) {
                prefs.edit()
                    .putString(VISITOR_DATA_KEY, data)
                    .putLong(VISITOR_DATA_FETCHED_AT_KEY, System.currentTimeMillis())
                    .apply()
                YouTube.visitorData = data
                Log.d(TAG, "Fresh visitorData fetched for region: $region")
            }
        }.onFailure { e ->
            Log.w(TAG, "Failed to fetch fresh visitorData: ${e.message}")
        }
    }

    private fun applyProxyConfig(config: io.github.aedev.flow.network.AppProxyConfig) {
        AppProxyManager.update(config)
        YouTube.proxy = AppProxyManager.currentProxy()
        YouTube.proxyAuth = AppProxyManager.currentHttpProxyAuthorizationHeader()
        NewPipeExtractor.invalidateClient()
    }

    override fun onTerminate() {
        DiscordPresenceRuntime.shutdown()
        super.onTerminate()
        // Clean up performance dispatcher resources
        PerformanceDispatcher.shutdown()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onLowMemory")
        releaseVolatileMemory()
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        FlowCrashHandler.recordPhase("memory", "FlowApplication.onTrimMemory level=$level")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            releaseVolatileMemory()
        }
    }

    private fun releaseVolatileMemory() {
        if (::imageLoader.isInitialized) {
            imageLoader.memoryCache?.clear()
        }
        if (::okHttpClient.isInitialized) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                okHttpClient.connectionPool.evictAll()
            }
        }
    }
}
