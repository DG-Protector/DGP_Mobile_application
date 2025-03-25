package com.teamkitel.dg_protector.ui.profile

import java.util.UUID

// 각 프로필에 대한 정보를 담은 Class
data class ProfileData(
    // 고유 식별자 생성, 랜덤 UUID 부여
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val age: String,
    val gender: String,
    val height: String,
    val weight: String,
    var usedTimeSeconds: Int = 0,
    var lastUsedTimestamp: Long = 0
)
