package com.androidbooks.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val filePath: String,           // Path to EPUB file in app private directory
    val coverPath: String?,         // Path to extracted cover image
    val lastReadAt: Long,
    val progressSpineIndex: Int = 0, // Current chapter/spine position
    val progressOffset: Float = 0f,  // Scroll percentage within chapter (0.0 - 1.0)
    val addedAt: Long = System.currentTimeMillis(),
    val language: String? = null,
    val publisher: String? = null,
    val totalSpineItems: Int = 0
)
