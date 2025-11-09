package com.androidbooks.domain.repository

import com.androidbooks.data.local.datastore.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val userPreferencesFlow: Flow<UserPreferences>
    suspend fun updateFontFamily(fontFamily: String)
    suspend fun updateFontSize(fontSize: Float)
    suspend fun updateLineHeight(lineHeight: Float)
    suspend fun updateThemeMode(themeMode: UserPreferences.ThemeMode)
    suspend fun updateBrightness(brightness: Float)
}
