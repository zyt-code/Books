package com.androidbooks.di

import android.content.Context
import androidx.room.Room
import com.androidbooks.data.local.AndroidBooksDatabase
import com.androidbooks.data.local.dao.BookDao
import com.androidbooks.data.local.dao.BookmarkDao
import com.androidbooks.data.local.dao.HighlightDao
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
    fun provideDatabase(@ApplicationContext context: Context): AndroidBooksDatabase {
        return Room.databaseBuilder(
            context,
            AndroidBooksDatabase::class.java,
            "androidbooks_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideBookDao(database: AndroidBooksDatabase): BookDao {
        return database.bookDao()
    }

    @Provides
    fun provideBookmarkDao(database: AndroidBooksDatabase): BookmarkDao {
        return database.bookmarkDao()
    }

    @Provides
    fun provideHighlightDao(database: AndroidBooksDatabase): HighlightDao {
        return database.highlightDao()
    }
}
