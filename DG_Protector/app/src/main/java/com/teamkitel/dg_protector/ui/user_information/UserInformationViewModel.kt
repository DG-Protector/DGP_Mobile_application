package com.teamkitel.dg_protector.ui.user_information

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// 사용자 정보 관련 데이터를 관리하는 ViewModel Class
class UserInformationViewModel : ViewModel() {
    // 내부에서 수정 가능한 LiveData
    // 기본값은 "UserInformationViewModel"로 설정
    private val _text = MutableLiveData<String>().apply {
        value = "UserInformationViewModel"
    }
    val text: LiveData<String> = _text
}
