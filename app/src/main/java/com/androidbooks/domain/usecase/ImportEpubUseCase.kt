package com.androidbooks.domain.usecase

import com.androidbooks.domain.repository.BookRepository
import java.io.File
import javax.inject.Inject

class ImportEpubUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    suspend operator fun invoke(epubFile: File): Result<String> {
        return bookRepository.addBook(epubFile)
    }
}
