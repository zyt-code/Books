package com.androidbooks.domain.repository

import com.androidbooks.data.local.entity.BookEntity
import com.androidbooks.domain.model.Book
import kotlinx.coroutines.flow.Flow
import java.io.File

enum class SortOption {
    LAST_READ,
    TITLE,
    AUTHOR
}

interface BookRepository {
    fun getAllBooks(sortBy: SortOption = SortOption.LAST_READ): Flow<List<Book>>
    fun getBookById(bookId: String): Flow<Book?>
    suspend fun getBookByIdOnce(bookId: String): Book?
    suspend fun addBook(epubFile: File): Result<String>
    suspend fun deleteBook(bookId: String)
    suspend fun updateReadingProgress(bookId: String, spineIndex: Int, offset: Float)
    suspend fun getBookEntity(bookId: String): BookEntity?
}
