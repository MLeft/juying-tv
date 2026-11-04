package com.juying.tv.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object SourceType {
    const val LOCAL = "LOCAL"
    const val MEDIASTORE = "MEDIASTORE"
    const val WEBDAV = "WEBDAV"
    const val QUARK = "QUARK"

    fun label(type: String): String = when (type) {
        LOCAL -> "本地目录"
        MEDIASTORE -> "本机与 U 盘"
        WEBDAV -> "WebDAV"
        QUARK -> "夸克网盘"
        else -> type
    }
}

object SeriesKind {
    const val SERIES = "SERIES"
    const val MOVIE = "MOVIE"
}

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val name: String,
    val config: String,
    val createdAt: Long,
    val enabled: Boolean = true,
    val lastScanAt: Long = 0L,
    val lastScanCount: Int = 0,
    val lastError: String? = null
)

@Entity(
    tableName = "series",
    foreignKeys = [
        ForeignKey(
            entity = SourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "remotePath"], unique = true)
    ]
)
data class SeriesEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val remotePath: String,
    val autoTitle: String,
    val customTitle: String? = null,
    val kind: String = SeriesKind.SERIES,
    val year: Int? = null,
    val overview: String? = null,
    val coverRef: String? = null,
    val coverUrl: String? = null,
    val hidden: Boolean = false,
    val kidsAllowed: Boolean = false,
    val lastSeenAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val displayTitle: String
        get() {
            val custom = customTitle?.takeIf { it.isNotBlank() }
            if (custom != null) return custom
            val stripped = autoTitle.replace(Regex("""^\d{1,3}[.\s_-]+(?=\S)"""), "").trim()
            return stripped.ifBlank { autoTitle }
        }
}

@Entity(
    tableName = "media",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("seriesId"),
        Index("sourceId"),
        Index(value = ["sourceId", "remoteId"], unique = true)
    ]
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val seriesId: String,
    val sourceId: String,
    val remoteId: String,
    val parentPath: String,
    val fileName: String,
    val title: String,
    val season: Int = 1,
    val episode: Int = 1,
    val sortIndex: Int = 0,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val mime: String? = null,
    val playRef: String,
    val subtitleRefs: String = "[]",
    val hidden: Boolean = false,
    val kidsAllowed: Boolean = false,
    val lastSeenAt: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collections",
    indices = [Index("sortIndex")]
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val coverUrl: String? = null,
    val sortIndex: Int = 0,
    val showOnHome: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "collection_items",
    primaryKeys = ["collectionId", "seriesId"],
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("collectionId"), Index("seriesId"), Index("sortIndex")]
)
data class CollectionItemEntity(
    val collectionId: String,
    val seriesId: String,
    val sortIndex: Int = 0
)

@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mediaId"), Index("seriesId"), Index("updatedAt")]
)
data class ProgressEntity(
    @PrimaryKey val mediaId: String,
    val seriesId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ContinueItem(
    val mediaId: String,
    val seriesId: String,
    val mediaTitle: String,
    val seriesTitle: String,
    val customTitle: String?,
    val coverRef: String?,
    val coverUrl: String?,
    val season: Int,
    val episode: Int,
    val kind: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val displaySeriesTitle: String get() = customTitle?.takeIf { it.isNotBlank() } ?: seriesTitle
    val progressFraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}


@Entity(
    tableName = "scrape_meta",
    foreignKeys = [
        ForeignKey(
            entity = SeriesEntity::class,
            parentColumns = ["id"],
            childColumns = ["seriesId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("externalId"),
        Index(value = ["provider", "externalId"])
    ]
)
data class ScrapeMetaEntity(
    @PrimaryKey val seriesId: String,
    val provider: String,
    val externalId: String,
    val title: String,
    val originalTitle: String? = null,
    val year: Int? = null,
    val overview: String? = null,
    val posterUrl: String? = null,
    val matchedScore: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
