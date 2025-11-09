package com.androidbooks.domain.usecase

import com.androidbooks.domain.model.Book
import com.androidbooks.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBookByIdUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    operator fun invoke(bookId: String): Flow<Book?> {
        return bookRepository.getBookById(bookId)
    }
}
