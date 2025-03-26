package com.teamkitel.dg_protector.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

// DataStore 인스턴스 (PreferenceManager.kt에 정의되어 있을 수 있음)
val Context.dataStore by preferencesDataStore(name = "settings")

// addUsageTime 함수: 최상위 함수로 선언
suspend fun addUsageTime(context: Context, profileId: String, date: String, additionalSeconds: Int) {
    val key = intPreferencesKey("usage_${profileId}_$date")
    context.dataStore.edit { preferences ->
        val current = preferences[key] ?: 0
        preferences[key] = current + additionalSeconds
    }
}
