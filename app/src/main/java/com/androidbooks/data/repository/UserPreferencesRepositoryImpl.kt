package com.androidbooks.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.androidbooks.data.local.datastore.UserPreferences
import com.androidbooks.data.local.datastore.UserPreferencesSerializer
import com.androidbooks.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<UserPreferences> by dataStore(
    fileName = "user_preferences.pb",
    serializer = UserPreferencesSerializer
)

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private val dataStore = context.userPreferencesDataStore

    override val userPreferencesFlow: Flow<UserPreferences> = dataStore.data

    override suspend fun updateFontFamily(fontFamily: String) {
        dataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFontFamily(fontFamily)
                .build()
        }
    }

    override suspend fun updateFontSize(fontSize: Float) {
        dataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setFontSize(fontSize)
                .build()
        }
    }

    override suspend fun updateLineHeight(lineHeight: Float) {
        dataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setLineHeight(lineHeight)
                .build()
        }
    }

    override suspend fun updateThemeMode(themeMode: UserPreferences.ThemeMode) {
        dataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setThemeMode(themeMode)
                .build()
        }
    }

    override suspend fun updateBrightness(brightness: Float) {
        dataStore.updateData { currentPrefs ->
            currentPrefs.toBuilder()
                .setBrightness(brightness)
                .build()
        }
    }
}
