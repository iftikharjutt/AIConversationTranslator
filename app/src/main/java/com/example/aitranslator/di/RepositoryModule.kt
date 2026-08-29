package com.example.aitranslator.di

import com.example.aitranslator.data.repository.TranslationRepositoryImpl
import com.example.aitranslator.domain.repository.TranslationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTranslationRepository(
        impl: TranslationRepositoryImpl
    ): TranslationRepository
}
