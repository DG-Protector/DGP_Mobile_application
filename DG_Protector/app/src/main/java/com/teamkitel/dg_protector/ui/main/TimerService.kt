package com.teamkitel.dg_protector.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.teamkitel.dg_protector.ui.profile.ProfileData

// 백그라운드, 다른 레이아웃에서도 타이머가 기능을 수행할 수 있도록 하기 위해 생성한 파일
// TimerService가 없던 기존 코드에서는 앱이 백그라운드에 있을 때, main 화면이 아닌 다른 레이아웃 화면으로 이동했을 때 Timer가 정지되어 TimerService를 생성하여 별도로 관리되도록 함

class TimerService : Service() {
    // Handler 객체 생성, 1초마다 작업 실행할 때 사용
    private val handler = Handler()
    // 서비스가 실행 중인지 체크하는 변수 (true면 실행 중)
    private var isRunning = false
    // 별도의 elapsedSeconds 변수 없이, 매 tick마다 선택된 프로필의 사용 시간을 업데이트함

    // 1초마다 실행되는 Runnable, 타이머 역할 수행함
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                // 매 tick마다 SharedPreferences에서 선택된 프로필 정보를 가져와 사용 시간을 1초 증가시킴
                val prefs = getSharedPreferences("selectedProfile", Context.MODE_PRIVATE)
                val profileJson = prefs.getString("selectedProfileJson", null)
                if (profileJson != null) {
                    val gson = Gson()
                    val profile = gson.fromJson(profileJson, ProfileData::class.java)
                    profile.usedTimeSeconds += 1
                    profile.lastUsedTimestamp = System.currentTimeMillis()
                    // 업데이트된 프로필 정보를 SharedPreferences에 저장함
                    prefs.edit().putString("selectedProfileJson", gson.toJson(profile)).apply()

                    // 타이머 값(사용 시간)을 브로드캐스트로 전송해서 UI 업데이트 가능하게 함
                    val tickIntent = Intent("TIMER_TICK")
                    tickIntent.putExtra("elapsedSeconds", profile.usedTimeSeconds)
                    sendBroadcast(tickIntent)
                }

                // 1초 후 다시 이 Runnable 실행함
                handler.postDelayed(this, 1000)
            }
        }
    }

    // Service가 시작될 때 호출됨
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            handler.post(timerRunnable)  // 타이머 Runnable 실행시킴
            Log.d("TimerService", "Service started (continuous mode)")
        }
        // START_STICKY: 시스템이 서비스 종료 후 다시 시작하도록 함
        return START_STICKY
    }

    // Service가 종료될 때 호출됨
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(timerRunnable)  // 타이머 Runnable 제거함
        Log.d("TimerService", "Service destroyed")
    }

    // 바인딩 서비스를 지원하지 않으므로 null 반환함
    override fun onBind(intent: Intent?): IBinder? = null
}
