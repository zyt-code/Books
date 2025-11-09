package com.androidbooks.domain.usecase

import com.androidbooks.domain.repository.BookRepository
import javax.inject.Inject

class UpdateReadingProgressUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(bookId: String, spineIndex: Int, offset: Float) {
        bookRepository.updateReadingProgress(bookId, spineIndex, offset)
    }
}
