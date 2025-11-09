package com.androidbooks.di

import com.androidbooks.data.epub.EpubParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideEpubParser(): EpubParser {
        return EpubParser()
    }
}
