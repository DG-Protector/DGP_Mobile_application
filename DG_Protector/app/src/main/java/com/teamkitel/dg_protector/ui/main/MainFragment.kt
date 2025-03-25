package com.teamkitel.dg_protector.ui.main

import android.Manifest
import android.app.ProgressDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Space
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutMainBinding
import com.teamkitel.dg_protector.service.TimerService
import com.teamkitel.dg_protector.ui.profile.ProfileData
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

// 메인 화면의 UI와 Bluetooth 연결, 타이머 서비스 및 사용자 프로필 관리 기능을 담당하는 Class

class MainFragment : Fragment() {

    // ViewBinding을 사용하여 layout_main.xml과 연결
    private var _binding: LayoutMainBinding? = null
    private val binding get() = _binding!!

    // UI 상태를 관리하기 위한 ViewModel
    private lateinit var mainViewModel: MainViewModel
    // Bluetooth 관련 변수들
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    // 진행 상황을 보여주기 위한 ProgressDialog (블루투스 검색/연결 시 사용됨)
    private var progressDialog: ProgressDialog? = null

    // 현재 선택된 프로필 데이터 (JSON 형식으로 SharedPreferences에 저장된 프로필)
    private var currentProfile: ProfileData? = null
    // 이전에 로드한 프로필 id (프로필 변경 감지)
    private var previousProfileId: String? = null

    // Bluetooth 연결 상태를 주기적으로 확인하기 위한 Handler와 Runnable
    private val connectionHandler = Handler(Looper.getMainLooper())
    private val connectionMonitor = object : Runnable {
        override fun run() {
            // Bluetooth 소켓이 연결되지 않았으면 TimerService를 중지함
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                val serviceIntent = Intent(requireContext(), TimerService::class.java)
                requireContext().stopService(serviceIntent)
                Log.d("MainFragment", "Bluetooth 연결 끊김 감지 - TimerService stopped")
            }
            // 1초마다 업데이트
            connectionHandler.postDelayed(this, 1000)
        }
    }

    // 실제로 BOND_BONDED 상태(=Bluetooth가 연결된 상태)일 때 Bluetooth가 '페어링'된 것으로 간주하는 변수
    private var isPaired: Boolean = false

    companion object {
        // Bluetooth 활성화 요청 및 권한 요청 시 사용하는 상수
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_CODE_PERMISSIONS = 101
        // Bluetooth SPP (Serial Port Profile) UUID
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // Bluetooth 상태 변경을 모니터링하는 BroadcastReceiver (Bluetooth on/off 상태에 따른 UI 업데이트 및 서비스 중지)
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        // Bluetooth가 꺼지면 버튼 색상 회색으로 업데이트, 페어링 상태 초기화, 타이머 서비스 중지
                        updateBluetoothButtonTint()
                        isPaired = false
                        val serviceIntent = Intent(requireContext(), TimerService::class.java)
                        requireContext().stopService(serviceIntent)
                    }
                    BluetoothAdapter.STATE_ON -> updateBluetoothButtonTint()
                }
            }
        }
    }

    // Bluetooth 장치 검색 관련 BroadcastReceiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // 새로운 장치를 찾으면 discoveredDevices 리스트에 추가
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                        }
                    }
                }
                // 검색 완료 시 등록된 Receiver 해제, ProgressDialog 종료, 검색 결과를 다이얼로그로 표시
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    requireContext().unregisterReceiver(this)
                    progressDialog?.dismiss()
                    showDevicesDialog()
                }
            }
        }
    }
    // 검색된 Bluetooth 장치를 저장하는 리스트
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // BLUETOOTH_CONNECT 권한이 있는지 확인하는 함수
    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // TimerService에서 방송하는 타이머 틱 이벤트를 수신하여 UI와 ViewModel 업데이트
    private val timerTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val seconds = intent?.getIntExtra("elapsedSeconds", 0) ?: 0
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(seconds)}"
            mainViewModel.elapsedSeconds.value = seconds
            Log.d("MainFragment", "Received timer tick: $seconds seconds")
        }
    }

    // Fragment가 화면에 나타나기 전에 필요한 권한을 확인 및 요청
    override fun onStart() {
        super.onStart()
        val permissionsNeeded = mutableListOf<String>()
        // BLUETOOTH_SCAN 권한 확인
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        // 위치 권한 확인 (Bluetooth 스캔에 필요)
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        // BLUETOOTH_CONNECT 권한 확인
        if (!hasBluetoothConnectPermission()) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        // 필요한 권한이 있으면 AlertDialog를 통해 사용자에게 요청
        if (permissionsNeeded.isNotEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle("권한 필요")
                .setMessage("이 앱은 블루투스 및 위치 기능을 사용하기 위해 권한이 필요합니다. 권한을 허용해주세요.")
                .setPositiveButton("확인") { dialog, _ ->
                    requestPermissions(permissionsNeeded.toTypedArray(), REQUEST_CODE_PERMISSIONS)
                }
                .setNegativeButton("취소") { dialog, _ -> dialog.dismiss() }
                .show()
        }
    }

    // Fragment 생성 시 ViewModel 초기화
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)
    }

    // View 생성 및 초기화: layout inflate, UI 컴포넌트 초기 설정, Bluetooth 초기화, 프로필 로드 등
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMainBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Bluetooth 초기화 및 현재 선택된 프로필 로드
        initializeBluetooth()
        loadSelectedProfile()

        // NumberPicker 설정 (좌우 압력 조절)
        val stepArr = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val max = stepArr.size - 1
        val min = 0
        val defaultPressure = 5

        binding.leftNumberPicker.maxValue = max
        binding.rightNumberPicker.maxValue = max
        binding.leftNumberPicker.minValue = min
        binding.rightNumberPicker.minValue = min

        // 기본 압력 값 설정 (ViewModel에 저장된 값이 없으면 defaultPressure 사용)
        binding.leftNumberPicker.value = mainViewModel.leftPressure ?: defaultPressure
        binding.rightNumberPicker.value = mainViewModel.rightPressure ?: defaultPressure

        binding.leftNumberPicker.displayedValues = stepArr
        binding.rightNumberPicker.displayedValues = stepArr
        binding.leftNumberPicker.wrapSelectorWheel = false
        binding.rightNumberPicker.wrapSelectorWheel = false

        // NumberPicker 값 변경 리스너 설정 (변경 시 ViewModel 업데이트)
        binding.leftNumberPicker.setOnValueChangedListener { _, _, newVal ->
            mainViewModel.leftPressure = newVal
        }
        binding.rightNumberPicker.setOnValueChangedListener { _, _, newVal ->
            mainViewModel.rightPressure = newVal
        }

        // Spinner 설정: 모드 선택 ("사용자 설정" vs 기타 자동 모드)
        val modeOptions = resources.getStringArray(R.array.mode_list)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modeOptions)
        binding.spinnerMode.adapter = spinnerAdapter

        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedMode = modeOptions[position]
                // "사용자 설정" 선택 시 NumberPicker 활성화, 자동 모드 관련 오버레이 숨김
                if (selectedMode == "사용자 설정") {
                    binding.leftNumberPicker.isEnabled = true
                    binding.rightNumberPicker.isEnabled = true
                    binding.controllerContainer.alpha = 1.0f
                    binding.overlayAutoMode.visibility = View.GONE
                    binding.tvAutoMode.visibility = View.GONE
                } else {
                    // 다른 모드 선택 시 NumberPicker 비활성화, 자동 모드 관련 UI 오버레이 표시('자동모드가 활성화 중입니다.')
                    binding.leftNumberPicker.isEnabled = false
                    binding.rightNumberPicker.isEnabled = false
                    binding.overlayAutoMode.visibility = View.VISIBLE
                    binding.tvAutoMode.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }

        // Bluetooth 버튼 클릭 시 동작:
        // 권한 확인 후, Bluetooth 비활성 상태이면 활성화 요청
        // 이미 활성화된 경우 장치 검색 시작
        binding.btnBluetooth.setOnClickListener {
            if (!hasBluetoothConnectPermission()) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE_PERMISSIONS)
                return@setOnClickListener
            }
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                Toast.makeText(requireContext(), "Bluetooth is already enabled.", Toast.LENGTH_SHORT).show()
                startDiscovery()
            }
        }

        // 동기화 버튼 클릭 시 동작
        // 연결된 Bluetooth 기기를 통해 선택한 모드 및 압력 값 전송
        binding.btnSync.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Toast.makeText(requireContext(), "아직 기기에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedMode = binding.spinnerMode.selectedItem.toString()
            if (selectedMode == "사용자 설정") {
                // 사용자 설정 모드인 경우 좌/우 압력 값을 포함하는 명령 생성
                val leftValue = binding.leftNumberPicker.value
                val rightValue = binding.rightNumberPicker.value
                val command = "L:$leftValue,R:$rightValue\n"
                try {
                    bluetoothSocket?.outputStream?.write(command.toByteArray())
                    Toast.makeText(requireContext(), "동기화 명령 전송 완료", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainFragment", "전송 실패: ${e.message}", e)
                    Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 사용자 설정 외 다른 모드인 경우 모드 이름(약, 중, 강)을 전송
                val command = "$selectedMode\n"
                try {
                    bluetoothSocket?.outputStream?.write(command.toByteArray())
                    Toast.makeText(requireContext(), "$selectedMode 모드 명령 전송 완료", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainFragment", "전송 실패: ${e.message}", e)
                    Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Bluetooth 버튼의 색상 업데이트 (Bluetooth 활성화 여부에 따라 다름)
        updateBluetoothButtonTint()
        // 현재 선택된 프로필 재로드 (필요 시 UI 업데이트)
        loadSelectedProfile()
        // Bluetooth 연결 모니터링 시작
        connectionHandler.post(connectionMonitor)

        return root
    }

    // 프로필 변경 감지 시 호출되는 함수 (프로필 재로드)
    private var isProfileChanging = false
    private fun onProfileChanged() {
        isProfileChanging = true
        loadSelectedProfile()
        isProfileChanging = false
    }

    // SharedPreferences에서 프로필을 로드하여 UI에 반영
    private fun loadSelectedProfile() {
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        if (profileJson != null) {
            val loadedProfile = Gson().fromJson(profileJson, ProfileData::class.java)
            currentProfile = loadedProfile
            binding.textUserName.text = currentProfile?.name ?: "USER1"
            // 프로필 변경 시 TimerService는 공유 저장소의 현재 선택된 프로필을 사용합니다.
        } else {
            binding.textUserName.text = "USER1"
        }
    }

    // 오늘 날짜를 키로 사용해 사용 시간을 저장 (예: "yyyy-MM-dd" 형식) TimerServiec에서 다른 레이아웃 or 앱이 백그라운드에 있을 때 시간을 저장할 곳이 필요하므로 저장이 필요함
    private fun saveUsageTimeForToday(usedSeconds: Int) {
        val prefs = requireContext().getSharedPreferences("dailyUsage", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().putInt(today, usedSeconds).apply()
        Log.d("MainFragment", "Saved usage for $today: $usedSeconds seconds")
    }

    // 프로필 변경이나 onPause() 시 현재 타이머 값을 프로필에 업데이트하여 저장
    private fun updateCurrentProfileUsage() {
        if (isProfileChanging) return
        currentProfile?.let { profile ->
            val currentSeconds = mainViewModel.elapsedSeconds.value ?: 0
            profile.usedTimeSeconds = currentSeconds
            profile.lastUsedTimestamp = System.currentTimeMillis()
            val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
            prefs.edit().putString("selectedProfileJson", Gson().toJson(profile)).commit()
            Log.d("MainFragment", "Updated profile: used time = $currentSeconds seconds, last used = ${profile.lastUsedTimestamp}")
            saveUsageTimeForToday(currentSeconds)
        }
    }

    // BluetoothAdapter 초기화 (BluetoothManager를 통해 가져옴)
    private fun initializeBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth 버튼의 색상을 Bluetooth 활성화 여부에 따라 변경 (UI 피드백 제공)
    private fun updateBluetoothButtonTint() {
        try {
            if (!hasBluetoothConnectPermission()) {
                binding.btnBluetooth.setColorFilter(
                    ContextCompat.getColor(requireContext(), android.R.color.white)
                )
                return
            }
            if (bluetoothAdapter.isEnabled) {
                binding.btnBluetooth.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
                )
            } else {
                binding.btnBluetooth.setColorFilter(
                    ContextCompat.getColor(requireContext(), android.R.color.white)
                )
            }
        } catch (e: SecurityException) {
            Log.e("MainFragment", "Permission error: ${e.message}", e)
            Toast.makeText(requireContext(), "Permission error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth 장치 검색 시작: 필요한 권한 확인 후 검색 시작 및 Receiver 등록
    private fun startDiscovery() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_CODE_PERMISSIONS
            )
            return
        }
        try {
            progressDialog = ProgressDialog(requireContext()).apply {
                setMessage("Scanning for devices...")
                setCancelable(false)
                show()
            }
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            discoveredDevices.clear()
            // IntentFilter 등록 (장치 발견 및 검색 종료 이벤트 감지)
            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_FOUND)
                addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            }
            requireContext().registerReceiver(discoveryReceiver, filter)
            bluetoothAdapter.startDiscovery()
        } catch (e: SecurityException) {
            Log.e("MainFragment", "Error starting discovery: ${e.message}", e)
            Toast.makeText(requireContext(), "Error starting discovery: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 검색된 Bluetooth 장치와 이미 페어링된 장치를 합쳐서 다이얼로그로 보여줌
    private fun showDevicesDialog() {
        val pairedDevices: Set<BluetoothDevice> =
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bondedDevices
            } else {
                emptySet()
            }
        // 모든 장치를 합침 (페어링된 장치 + 검색된 장치)
        val allDevices = mutableSetOf<BluetoothDevice>()
        allDevices.addAll(pairedDevices)
        allDevices.addAll(discoveredDevices)
        if (allDevices.isEmpty()) {
            Toast.makeText(requireContext(), "No Bluetooth devices found.", Toast.LENGTH_SHORT).show()
            return
        }
        // 장치 이름 및 주소 표시 (등록된 기기의 경우 "(Registered)" 표시)
        val deviceNames = allDevices.map { device ->
            val name = try {
                if (hasBluetoothConnectPermission()) {
                    device.name ?: "Unknown Device"
                } else {
                    "Permission not granted"
                }
            } catch (e: SecurityException) {
                "Error"
            }
            if (pairedDevices.contains(device)) "$name (Registered)\n${device.address}"
            else "$name\n${device.address}"
        }.toTypedArray()
        // AlertDialog 생성: 장치 목록 중 선택할 수 있도록 함
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Select Bluetooth Device")
        builder.setItems(deviceNames) { dialog, which ->
            val deviceList = allDevices.toList()
            val selectedDevice = deviceList[which]
            connectToDevice(selectedDevice)
        }
        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.show()
    }

    // 선택된 Bluetooth 장치와 연결 시도 및 연결 성공 시 TimerService 시작, 프로필 로드 등 수행
    private fun connectToDevice(device: BluetoothDevice) {
        val connectDialog = ProgressDialog(requireContext()).apply {
            val deviceName = try {
                if (hasBluetoothConnectPermission()) {
                    device.name ?: device.address
                } else {
                    device.address
                }
            } catch (e: SecurityException) {
                device.address
            }
            setMessage("Connecting to $deviceName...")
            setCancelable(false)
            show()
        }
        Thread {
            try {
                if (!hasBluetoothConnectPermission()) {
                    throw SecurityException("BLUETOOTH_CONNECT permission not granted")
                }
                // SPP UUID를 사용하여 Bluetooth 소켓 생성 및 연결
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                bluetoothSocket = socket
                activity?.runOnUiThread {
                    connectDialog.dismiss()
                    Toast.makeText(requireContext(), "Connected to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                    isPaired = true
                    loadSelectedProfile()
                    // 연결 후 현재 프로필의 사용 시간으로 TimerService 초기화
                    val initialTime = currentProfile?.usedTimeSeconds ?: 0
                    val serviceIntent = Intent(requireContext(), TimerService::class.java)
                    serviceIntent.putExtra("initialTime", initialTime)
                    requireContext().stopService(serviceIntent)
                    requireContext().startService(serviceIntent)
                    Log.d("MainFragment", "Bluetooth connected. Timer service started with initial time: $initialTime")
                }
            } catch (e: Exception) {
                Log.e("MainFragment", "Connection failed: ${e.message}", e)
                activity?.runOnUiThread {
                    connectDialog.dismiss()
                    Toast.makeText(requireContext(), "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // Bluetooth 활성화 요청 결과를 처리 (요청 후 onActivityResult에서 호출)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            updateBluetoothButtonTint()
            if (bluetoothAdapter.isEnabled) {
                Toast.makeText(requireContext(), "Bluetooth enabled successfully.", Toast.LENGTH_SHORT).show()
                startDiscovery()
            } else {
                Toast.makeText(requireContext(), "Bluetooth enabling failed or was cancelled.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 권한 요청 결과를 처리: 모든 권한이 승인된 경우 Bluetooth 검색 시작
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    if (bluetoothAdapter.isEnabled) {
                        startDiscovery()
                    }
                } else {
                    Toast.makeText(requireContext(), "필요한 권한이 부여되지 않아 일부 기능이 동작하지 않습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Fragment가 다시 활성화될 때 필요한 Receiver 등록 및 프로필 로드
    override fun onResume() {
        super.onResume()
        loadSelectedProfile()
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireContext().registerReceiver(bluetoothStateReceiver, filter)
        requireContext().registerReceiver(timerTickReceiver, IntentFilter("TIMER_TICK"))
    }

    // Fragment가 백그라운드로 전환될 때 사용 시간 업데이트 및 Receiver 해제, 연결 모니터 중지
    override fun onPause() {
        super.onPause()
        updateCurrentProfileUsage()
        connectionHandler.removeCallbacks(connectionMonitor)
        requireContext().unregisterReceiver(bluetoothStateReceiver)
        requireContext().unregisterReceiver(timerTickReceiver)
    }

    // View가 파괴될 때 ViewBinding 해제
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 초 단위의 시간을 hour:minute:second 형식의 문자열로 포맷팅하는 헬퍼 함수
    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
}
