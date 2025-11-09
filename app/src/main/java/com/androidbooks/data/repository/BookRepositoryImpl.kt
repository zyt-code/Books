package com.androidbooks.data.repository

import android.content.Context
import com.androidbooks.data.epub.EpubParser
import com.androidbooks.data.local.dao.BookDao
import com.androidbooks.data.local.entity.BookEntity
import com.androidbooks.domain.model.Book
import com.androidbooks.domain.repository.BookRepository
import com.androidbooks.domain.repository.SortOption
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookDao: BookDao,
    private val epubParser: EpubParser
) : BookRepository {

    override fun getAllBooks(sortBy: SortOption): Flow<List<Book>> {
        val flow = when (sortBy) {
            SortOption.LAST_READ -> bookDao.getAllBooks()
            SortOption.TITLE -> bookDao.getAllBooksSortedByTitle()
            SortOption.AUTHOR -> bookDao.getAllBooksSortedByAuthor()
        }
        return flow.map { entities -> entities.map { it.toDomainModel() } }
    }

    override fun getBookById(bookId: String): Flow<Book?> {
        return bookDao.getBookByIdFlow(bookId).map { it?.toDomainModel() }
    }

    override suspend fun getBookByIdOnce(bookId: String): Book? {
        return withContext(Dispatchers.IO) {
            bookDao.getBookById(bookId)?.toDomainModel()
        }
    }

    override suspend fun addBook(epubFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            android.util.Log.d("BookRepositoryImpl", "Adding book from file: ${epubFile.absolutePath}")

            val bookId = UUID.randomUUID().toString()
            val bookDir = File(context.filesDir, "books/$bookId")
            bookDir.mkdirs()

            android.util.Log.d("BookRepositoryImpl", "Created book directory: ${bookDir.absolutePath}")

            // Copy EPUB to app private storage
            val destinationFile = File(bookDir, "book.epub")
            epubFile.copyTo(destinationFile, overwrite = true)

            android.util.Log.d("BookRepositoryImpl", "Copied EPUB to: ${destinationFile.absolutePath}, size: ${destinationFile.length()} bytes")

            // Parse EPUB
            val parseResult = epubParser.parseEpub(destinationFile, bookDir)
            if (parseResult.isFailure) {
                android.util.Log.e("BookRepositoryImpl", "Failed to parse EPUB", parseResult.exceptionOrNull())
                bookDir.deleteRecursively()
                return@withContext Result.failure(
                    parseResult.exceptionOrNull() ?: Exception("Failed to parse EPUB")
                )
            }

            val epubBook = parseResult.getOrThrow()

            android.util.Log.d("BookRepositoryImpl", "Successfully parsed EPUB: title=${epubBook.metadata.title}, " +
                    "author=${epubBook.metadata.author}, " +
                    "spine items=${epubBook.spineItems.size}, " +
                    "cover=${epubBook.coverImagePath}")

            // Create BookEntity
            val bookEntity = BookEntity(
                id = bookId,
                title = epubBook.metadata.title,
                author = epubBook.metadata.author,
                filePath = destinationFile.absolutePath,
                coverPath = epubBook.coverImagePath,
                lastReadAt = System.currentTimeMillis(),
                progressSpineIndex = 0,
                progressOffset = 0f,
                language = epubBook.metadata.language,
                publisher = epubBook.metadata.publisher,
                totalSpineItems = epubBook.spineItems.size
            )

            bookDao.insertBook(bookEntity)
            android.util.Log.d("BookRepositoryImpl", "Inserted book entity into database: $bookId")

            Result.success(bookId)
        } catch (e: Exception) {
            android.util.Log.e("BookRepositoryImpl", "Exception in addBook", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteBook(bookId: String) = withContext(Dispatchers.IO) {
        // Delete from database
        bookDao.deleteBookById(bookId)

        // Delete files
        val bookDir = File(context.filesDir, "books/$bookId")
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
    }

    override suspend fun updateReadingProgress(
        bookId: String,
        spineIndex: Int,
        offset: Float
    ) = withContext(Dispatchers.IO) {
        bookDao.updateReadingProgress(
            bookId = bookId,
            spineIndex = spineIndex,
            offset = offset,
            timestamp = System.currentTimeMillis()
        )
    }

    override suspend fun getBookEntity(bookId: String): BookEntity? {
        return withContext(Dispatchers.IO) {
            bookDao.getBookById(bookId)
        }
    }

    private fun BookEntity.toDomainModel() = Book(
        id = id,
        title = title,
        author = author,
        coverPath = coverPath,
        progressSpineIndex = progressSpineIndex,
        progressOffset = progressOffset,
        lastReadAt = lastReadAt,
        totalSpineItems = totalSpineItems
    )
}
