package com.teamkitel.dg_protector.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

// 메인 UI와 관련된 데이터를 저장하고 관리하는 Class
// 좌/우 압력 값, 타이머 경과 시간, 그리고 이전에 선택된 프로필의 식별자 등이 포함되어 있음

class MainViewModel : ViewModel() {
    // 좌측 압력 값: 사용자가 설정한 왼쪽 압력 값을 저장
    // 초기값은 null이며, 사용자가 값을 선택하면 업데이트
    var leftPressure: Int? = null

    // 우측 압력 값: 사용자가 설정한 오른쪽 압력 값을 저장
    // 초기값은 null이며, 사용자가 값을 선택하면 업데이트
    var rightPressure: Int? = null

    // 타이머로 사용한 시간을 초 단위로 저장하는 LiveData.
    // LiveData를 사용하면 UI 컴포넌트가 이 데이터를 관찰하여 값이 변경될 때 자동으로 업데이트 가능
    val elapsedSeconds: MutableLiveData<Int> = MutableLiveData<Int>().apply { value = 0 }

    // 이전에 선택한 프로필의 고유 식별자를 저장
    // profile id를 비교하여 프로필 변경 감지
    var previousProfileId: String? = null
}
