package com.androidbooks.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.androidbooks.data.local.dao.BookDao
import com.androidbooks.data.local.dao.BookmarkDao
import com.androidbooks.data.local.dao.HighlightDao
import com.androidbooks.data.local.entity.BookEntity
import com.androidbooks.data.local.entity.BookmarkEntity
import com.androidbooks.data.local.entity.HighlightEntity

@Database(
    entities = [
        BookEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AndroidBooksDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
}
