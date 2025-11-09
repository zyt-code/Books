package com.androidbooks.data.local.dao

import androidx.room.*
import com.androidbooks.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookId = :bookId ORDER BY spineIndex ASC, startOffset ASC")
    fun getHighlightsByBookId(bookId: String): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId AND spineIndex = :spineIndex ORDER BY startOffset ASC")
    fun getHighlightsBySpineIndex(bookId: String, spineIndex: Int): Flow<List<HighlightEntity>>

    @Insert
    suspend fun insertHighlight(highlight: HighlightEntity): Long

    @Update
    suspend fun updateHighlight(highlight: HighlightEntity)

    @Delete
    suspend fun deleteHighlight(highlight: HighlightEntity)

    @Query("DELETE FROM highlights WHERE bookId = :bookId")
    suspend fun deleteAllHighlightsForBook(bookId: String)
}
