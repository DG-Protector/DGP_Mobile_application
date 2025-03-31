package com.teamkitel.dg_protector

import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.teamkitel.dg_protector.databinding.ActivityMainBinding
import androidx.activity.enableEdgeToEdge

// 앱의 Main Activity Class
// Bottom Navigation Bar를 사용하며 각 화면은 Navigation Component를 통해 전환됨
class MainActivity : AppCompatActivity() {

    // ViewBinding 객체 선언: activity_main.xml의 뷰들에 접근할 수 있게 해줌
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge to Edge모드 활성화
        enableEdgeToEdge()

        // ViewBinding 초기화하고 레이아웃 셋팅
        binding = ActivityMainBinding.inflate(layoutInflater)

        // Activity의 컨텐츠 뷰를 binding.root로 설정
        setContentView(binding.root)

        // activity_main.xml에 정의된 BottomNavigationView를 가져옴
        val navView: BottomNavigationView = binding.navView

        // Navigation Component에서 화면 전환을 관리하는 NavController 가져오기
        // nav_host_fragment_activity_main은 FragmentContainerView의 ID
        val navController = findNavController(R.id.nav_host_fragment_activity_main)

        // 최상위 목적지(탭) ID들을 셋업, 각 메뉴가 최상위 목적지로 간주
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_statistics,         // 통계 화면
                R.id.navigation_main,               // 메인 화면
                R.id.navigation_user_information    // 사용자 정보 화면
            )
        )

        // BottomNavigationView를 NavController와 연결해서 화면 전환 관리
        navView.setupWithNavController(navController)

    }
}
