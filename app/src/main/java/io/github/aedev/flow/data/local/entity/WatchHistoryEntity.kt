package io.github.aedev.flow.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.source.ContentId

/**
 * Room entity that replaces the previous DataStore-based watch history.
 *
 * The old DataStore approach stored ~8 preference keys per video, meaning
 * 46 000 watched videos → ~370 000 entries in a single serialised protobuf
 * file that was fully deserialized on every read.  SQLite handles millions of
 * rows efficiently with proper indexing.
 */
@Entity(
    tableName = "watch_history",
    indices = [
        Index(value = ["videoId"], unique = true),
        Index(value = ["timestamp"]),
        Index(value = ["isMusic"]),
        Index(value = ["isShort"]),
        Index(value = ["isLocal"])
    ]
)
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val position: Long,
    val duration: Long,
    val timestamp: Long,
    val title: String,
    val thumbnailUrl: String,
    val channelName: String,
    val channelId: String,
    val isMusic: Boolean,
    val isShort: Boolean = false,
    val isLocal: Boolean = false
) {
    fun toDomain() = VideoHistoryEntry(
        videoId = videoId,
        position = position,
        duration = duration,
        timestamp = timestamp,
        title = title,
        thumbnailUrl = thumbnailUrl,
        channelName = channelName,
        channelId = channelId,
        isMusic = isMusic,
        isShort = isShort,
        isLocal = isLocal
    )

    /** Reconstruct a lightweight [Video] from history metadata (no stream info). */
    fun toVideo(): Video {
        // Recomputed from the id rather than stored, as in [VideoEntity.toDomain]. Without it a
        // restored federated video claims to be a YouTube one, and everything that dispatches on
        // the source — the badge, the Fediverse actions, the player's own load path — believes it.
        val contentId = ContentId(videoId)
        return Video(
            id = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumbnailUrl,
            duration = if (duration > 0) (duration / 1000).toInt() else 0,
            viewCount = 0,
            uploadDate = "",
            isShort = isShort,
            source = contentId.kind,
            instanceHost = contentId.instanceHost
        )
    }

    companion object {
        fun fromDomain(entry: VideoHistoryEntry) = WatchHistoryEntity(
            videoId      = entry.videoId,
            position     = entry.position,
            duration     = entry.duration,
            timestamp    = entry.timestamp,
            title        = entry.title,
            thumbnailUrl = entry.thumbnailUrl,
            channelName  = entry.channelName,
            channelId    = entry.channelId,
            isMusic      = entry.isMusic,
            isShort      = entry.isShort,
            isLocal      = entry.isLocal
        )
    }
}
