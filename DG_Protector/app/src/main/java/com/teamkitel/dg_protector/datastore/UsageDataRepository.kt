package com.teamkitel.dg_protector.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

// 사용 기록 저장을 위한 고유 키를 생성하는 함수
// ex) usage_user1_2025-03-31
fun generateUsageKey(profileId: String, date: String): Preferences.Key<Int> {
    return intPreferencesKey("usage_${profileId}_$date")
}

// 지정된 기간 동안의 일별 사용 시간을 Flow로 반환하는 함수
// 실시간 변경 사항을 감지
fun getWeeklyUsageFlow(context: Context, profileId: String, startDate: String, endDate: String): Flow<Map<String, Int>> {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val start = dateFormat.parse(startDate) ?: Date()       // 시작 날짜 파싱
    val end = dateFormat.parse(endDate) ?: Date()           // 종료 날짜 파싱
    val calendar = Calendar.getInstance().apply { time = start }

    // 지정된 기간 동안의 날짜 목록과 각 날짜에 대한 키를 리스트에 저장
    val keys: MutableList<Pair<String, Preferences.Key<Int>>> = mutableListOf()
    while (!calendar.time.after(end)) {
        val dateStr = dateFormat.format(calendar.time)
        keys.add(dateStr to generateUsageKey(profileId, dateStr))
        calendar.add(Calendar.DATE, 1)
    }

    // DataStore에서 해당 키들의 값을 읽고, 날짜별 Map으로 반환
    return context.dataStore.data.map { preferences ->
        keys.associate { (dateStr, key) ->
            dateStr to (preferences[key] ?: 0)
        }
    }
}
