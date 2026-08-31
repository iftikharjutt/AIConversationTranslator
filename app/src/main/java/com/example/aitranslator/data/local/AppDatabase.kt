package com.example.aitranslator.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.aitranslator.domain.model.OfflineModelStatus
import com.example.aitranslator.domain.model.SegmentStatus

class Converters {
    @TypeConverter
    fun fromSegmentStatus(status: SegmentStatus?): String? = status?.name

    @TypeConverter
    fun toSegmentStatus(value: String?): SegmentStatus? =
        value?.let { enumValueOf<SegmentStatus>(it) } ?: SegmentStatus.RECORDED

    @TypeConverter
    fun fromOfflineModelStatus(status: OfflineModelStatus?): String? = status?.name

    @TypeConverter
    fun toOfflineModelStatus(value: String?): OfflineModelStatus? =
        value?.let { enumValueOf<OfflineModelStatus>(it) } ?: OfflineModelStatus.NOT_DOWNLOADED
}

@Database(
    entities = [ConversationEntity::class, SegmentEntity::class, OfflineModelEntity::class],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun segmentDao(): SegmentDao
    abstract fun offlineModelDao(): OfflineModelDao
}
