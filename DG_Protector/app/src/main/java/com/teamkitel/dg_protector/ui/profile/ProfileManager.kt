package com.teamkitel.dg_protector.ui.profile

import android.content.Context
import com.google.gson.Gson

// 사용자의 프로필 데이터를 SharedPreferences를 통해 저장, 불러오기, 삭제하는 기능을 제공
object ProfileManager {
    // SharedPreferences 파일 이름 및 프로필 데이터가 저장될 키 값 정의
    private const val PREFS_NAME = "selectedProfile"
    private const val KEY_PROFILE = "selectedProfileJson"

    // 현재 선택된 프로필 데이터를 저장하는 변수 (외부에서 읽을 수 있으나, 직접 수정은 불가능)
    var currentProfile: ProfileData? = null
        private set

    // SharedPreferences에 저장된 JSON 형식의 프로필 데이터를 불러와 currentProfile에 할당
    fun loadCurrentProfile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROFILE, null)
        currentProfile = if (!json.isNullOrEmpty() && json != "null") {
            Gson().fromJson(json, ProfileData::class.java)
        } else {
            null
        }
    }

    // 새로운 프로필 데이터를 currentProfile에 저장하고 SharedPreferences에 JSON 형태로 업데이트
    fun updateCurrentProfile(profile: ProfileData, context: Context) {
        currentProfile = profile
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROFILE, Gson().toJson(profile)).apply()
    }

    // currentProfile을 null로 초기화하고 SharedPreferences에서 프로필 데이터를 삭제
    fun clearCurrentProfile(context: Context) {
        currentProfile = null
        // SharedPreferences 인스턴스 획득 후, 프로필 데이터 제거
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PROFILE).apply()
    }
}