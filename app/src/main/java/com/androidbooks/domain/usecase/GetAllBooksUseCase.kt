package com.androidbooks.domain.usecase

import com.androidbooks.domain.model.Book
import com.androidbooks.domain.repository.BookRepository
import com.androidbooks.domain.repository.SortOption
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAllBooksUseCase @Inject constructor(
    private val bookRepository: BookRepository
) {
    operator fun invoke(sortBy: SortOption = SortOption.LAST_READ): Flow<List<Book>> {
        return bookRepository.getAllBooks(sortBy)
    }
}
