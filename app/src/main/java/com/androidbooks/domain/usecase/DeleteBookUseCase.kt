package com.androidbooks.domain.usecase

import com.androidbooks.domain.repository.BookRepository
import javax.inject.Inject

class DeleteBookUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: String) {
        bookRepository.deleteBook(bookId)
    }
}
