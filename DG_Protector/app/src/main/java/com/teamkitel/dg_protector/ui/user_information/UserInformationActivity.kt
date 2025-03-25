package com.teamkitel.dg_protector.ui.user_information

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.teamkitel.dg_protector.R

// 사용자 정보 액티비티 Class.
// User Information Fragment를 로드함.
class UserInformationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // activity_user_information 레이아웃을 setting
        setContentView(R.layout.activity_user_information)
        // savedInstanceState가 null이면 처음 생성된 것이므로 Fragment를 추가함
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, UserInformationFragment())
                .commit()
        }
    }
}
