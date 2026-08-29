package com.example.aitranslator.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.example.aitranslator.domain.model.SegmentStatus

class Converters {
    @TypeConverter
    fun fromSegmentStatus(status: SegmentStatus?): String? = status?.name

    @TypeConverter
    fun toSegmentStatus(value: String?): SegmentStatus? =
        value?.let { enumValueOf<SegmentStatus>(it) } ?: SegmentStatus.RECORDED
}

@Database(
    entities = [ConversationEntity::class, SegmentEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun segmentDao(): SegmentDao
}
