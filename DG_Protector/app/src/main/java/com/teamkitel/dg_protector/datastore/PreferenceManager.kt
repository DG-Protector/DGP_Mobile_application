package com.teamkitel.dg_protector.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.*

// Context에 DataStore 확장 property를 정의
// 내부적으로 "settings"라는 이름의 DataStore 파일을 생성함
val Context.dataStore by preferencesDataStore(name = "settings")

// 지정된 프로필 ID와 날짜에 대해 사용 시간을 누적 저장하는 함수
suspend fun addUsageTime(context: Context, profileId: String, date: String, additionalSeconds: Int) {
    val key = intPreferencesKey("usage_${profileId}_$date")       // "usage_프로필ID_날짜" 형태의 키를 생성
context.dataStore.edit { preferences ->                                 // DataStore에 접근하여 해당 키의 값을 업데이트
        val current = preferences[key] ?: 0                             // 기존 값이 없으면 0으로 시작
        preferences[key] = current + additionalSeconds                  // 추가 시간만큼 누적
    }
}
