package io.github.aedev.flow.data.repository

import android.util.LruCache
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object VideoCollaboratorResolver {
    private val cache = LruCache<String, List<VideoCollaborator>>(300)

    suspend fun resolve(videoId: String): List<VideoCollaborator> {
        if (videoId.isBlank()) return emptyList()
        cache[videoId]?.let { return it }

        return withContext(Dispatchers.IO) {
            cache[videoId]?.let { return@withContext it }
            // "No collaborators" is an answer worth remembering — most videos have none, and
            // without caching it every one of them asked YouTube again each time it scrolled back
            // into view. But only when the request actually answered: a timeout and a failure also
            // produce an empty list, and caching those would hide a video's collaborators for the
            // rest of the process over one bad moment on the network.
            val answered = withTimeoutOrNull(4_000L) {
                YouTube.videoCollaborators(videoId).getOrNull()
            }
            if (answered != null) {
                cache.put(videoId, answered)
            }
            answered.orEmpty()
        }
    }
}
