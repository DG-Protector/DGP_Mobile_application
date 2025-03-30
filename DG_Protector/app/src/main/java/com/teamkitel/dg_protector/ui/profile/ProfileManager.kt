package com.teamkitel.dg_protector.ui.profile

import android.content.Context
import com.google.gson.Gson

object ProfileManager {
    private const val PREFS_NAME = "selectedProfile"
    private const val KEY_PROFILE = "selectedProfileJson"

    var currentProfile: ProfileData? = null
        private set

    fun loadCurrentProfile(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_PROFILE, null)
        currentProfile = if (!json.isNullOrEmpty() && json != "null") {
            Gson().fromJson(json, ProfileData::class.java)
        } else {
            null
        }
    }

    fun updateCurrentProfile(profile: ProfileData, context: Context) {
        currentProfile = profile
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROFILE, Gson().toJson(profile)).apply()
    }

    fun clearCurrentProfile(context: Context) {
        currentProfile = null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_PROFILE).apply()
    }
}
