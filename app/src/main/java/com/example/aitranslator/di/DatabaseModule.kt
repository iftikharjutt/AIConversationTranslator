package com.example.aitranslator.di

import android.content.Context
import androidx.room.Room
import com.example.aitranslator.data.local.AppDatabase
import com.example.aitranslator.data.local.ConversationDao
import com.example.aitranslator.data.local.SegmentDao
import com.example.aitranslator.util.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.DATABASE_NAME
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideSegmentDao(database: AppDatabase): SegmentDao = database.segmentDao()

    @Provides
    fun provideOfflineModelDao(database: AppDatabase): com.example.aitranslator.data.local.OfflineModelDao = database.offlineModelDao()
}
