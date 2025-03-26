package com.teamkitel.dg_protector.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

fun generateUsageKey(profileId: String, date: String): Preferences.Key<Int> {
    return intPreferencesKey("usage_${profileId}_$date")
}

// 기존 함수는 snapshot을 반환하지만, 이 함수는 실시간 Flow를 반환함.
fun getWeeklyUsageFlow(context: Context, profileId: String, startDate: String, endDate: String): Flow<Map<String, Int>> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val start = dateFormat.parse(startDate) ?: Date()
    val end = dateFormat.parse(endDate) ?: Date()
    val calendar = Calendar.getInstance().apply { time = start }

    val keys: MutableList<Pair<String, Preferences.Key<Int>>> = mutableListOf()
    while (!calendar.time.after(end)) {
        val dateStr = dateFormat.format(calendar.time)
        keys.add(dateStr to generateUsageKey(profileId, dateStr))
        calendar.add(Calendar.DATE, 1)
    }

    return context.dataStore.data.map { preferences ->
        keys.associate { (dateStr, key) ->
            dateStr to (preferences[key] ?: 0)
        }
    }
}
