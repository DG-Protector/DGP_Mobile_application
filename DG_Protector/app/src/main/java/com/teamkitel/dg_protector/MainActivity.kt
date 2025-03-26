package com.teamkitel.dg_protector

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.teamkitel.dg_protector.databinding.ActivityMainBinding
import androidx.activity.enableEdgeToEdge

// 메인 액티비티임. 바텀 내비게이션과 네비게이션 컴포넌트를 사용함.
class MainActivity : AppCompatActivity() {

    // ViewBinding을 통해 activity_main.xml 레이아웃에 접근함
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // edge to edge 모드 활성화
        enableEdgeToEdge()

        // 뷰바인딩 초기화하고 레이아웃 셋팅
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 바텀 내비게이션 뷰 가져옴
        val navView: BottomNavigationView = binding.navView

        // 네비게이션 호스트 프래그먼트의 NavController
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // 최상위 목적지(탭) ID들을 셋업, 각 메뉴가 최상위 목적지로 간주됨
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_statistics, R.id.navigation_main, R.id.navigation_user_information
            )
        )

        // 바텀 내비게이션을 NavController와 연결해서 화면 전환 관리
        navView.setupWithNavController(navController)

    }
}
