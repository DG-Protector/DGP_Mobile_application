package com.teamkitel.dg_protector.service

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.util.Log
import com.google.gson.Gson
import com.teamkitel.dg_protector.ui.profile.ProfileData
// 기존 Room 관련 import 제거하고, DataStore 관련 함수 import
import com.teamkitel.dg_protector.datastore.addUsageTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TimerService : Service() {
    private val handler = Handler()
    private var isRunning = false

    // Bluetooth 상태 변화 감지용 리시버 등록
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF) {
                    Log.d("TimerService", "Bluetooth turned off, stopping TimerService")
                    // Bluetooth가 꺼지면 서비스를 중지
                    stopSelf()
                }
            }
        }
    }

    // 타이머 Runnable: 매 초마다 프로필 데이터를 업데이트하고, 사용 시간을 저장함.
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                val prefs = getSharedPreferences("selectedProfile", Context.MODE_PRIVATE)
                val profileJson = prefs.getString("selectedProfileJson", null)
                if (profileJson != null) {
                    val gson = Gson()
                    val profile = gson.fromJson(profileJson, ProfileData::class.java)
                    profile.usedTimeSeconds += 1
                    profile.lastUsedTimestamp = System.currentTimeMillis()
                    prefs.edit().putString("selectedProfileJson", gson.toJson(profile)).apply()

                    // 타이머 값 업데이트: DataStore를 사용해 오늘 날짜의 사용 시간을 1초 추가
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                    CoroutineScope(Dispatchers.IO).launch {
                        addUsageTime(applicationContext, profile.id, today, 1)
                        // 오래된 데이터 삭제 기능이 있다면 이곳에서 호출 (예: deleteOlderUsage)
                        val calendar = Calendar.getInstance()
                        calendar.add(Calendar.DATE, -6)
                        val thresholdDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
                        // deleteOlderUsage(applicationContext, profile.id, thresholdDate)
                    }

                    // 타이머 값을 브로드캐스트로 전송하여 UI 업데이트
                    val tickIntent = Intent("TIMER_TICK")
                    tickIntent.putExtra("elapsedSeconds", profile.usedTimeSeconds)
                    sendBroadcast(tickIntent)
                }
                handler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Bluetooth 상태 변경 리시버 등록
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            handler.post(timerRunnable)
            Log.d("TimerService", "Service started (continuous mode)")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(timerRunnable)
        unregisterReceiver(bluetoothStateReceiver)
        Log.d("TimerService", "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
