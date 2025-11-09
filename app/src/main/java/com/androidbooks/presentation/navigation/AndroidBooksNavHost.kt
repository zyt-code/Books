package com.androidbooks.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.androidbooks.presentation.bookshelf.BookshelfScreen
import com.androidbooks.presentation.reader.ReaderScreen
import com.androidbooks.presentation.reader.ReaderViewModel
import com.androidbooks.presentation.theme.AndroidBooksTheme
import com.androidbooks.domain.repository.UserPreferencesRepository

@Composable
fun AndroidBooksNavHost(
    navController: NavHostController,
    userPreferencesRepository: UserPreferencesRepository
) {
    val userPreferences by userPreferencesRepository.userPreferencesFlow.collectAsState(
        initial = com.androidbooks.data.local.datastore.UserPreferences.getDefaultInstance()
    )

    AndroidBooksTheme(themeMode = userPreferences.themeMode) {
        NavHost(
            navController = navController,
            startDestination = Screen.Bookshelf.route
        ) {
            composable(Screen.Bookshelf.route) {
                BookshelfScreen(
                    onNavigateToReader = { bookId ->
                        navController.navigate(Screen.Reader.createRoute(bookId))
                    }
                )
            }

            composable(
                route = Screen.Reader.route,
                arguments = listOf(
                    navArgument("bookId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
                ReaderScreen(
                    bookId = bookId,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
