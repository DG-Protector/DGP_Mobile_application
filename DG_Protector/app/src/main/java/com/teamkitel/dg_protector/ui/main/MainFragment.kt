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
import com.teamkitel.dg_protector.ui.profile.ProfileManager
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainFragment : Fragment() {
    private var _binding: LayoutMainBinding? = null
    private val binding get() = _binding!!

    private lateinit var mainViewModel: MainViewModel           // MainViewModel, UI 관련 데이터(예: 시간, 압력값 등)를 저장
    private lateinit var bluetoothAdapter: BluetoothAdapter     // BluetoothAdapter, 기기의 블루투스 기능 제어를 위해 사용
    private var bluetoothSocket: BluetoothSocket? = null        // BluetoothSocket, 기기와의 블루투스 연결에 사용
    private var progressDialog: ProgressDialog? = null          // ProgressDialog, 진행 상황을 보여주는 Dialog
    private var previousProfileId: String? = null               // 이전에 선택한 프로필 ID를 저장하여 프로필 변경 여부 확인에 사용

    // 블루투스 연결 상태를 주기적으로 모니터링하기 위한 Handler와 Runnable
    private val connectionHandler = Handler(Looper.getMainLooper())
    private val connectionMonitor = object : Runnable {
        override fun run() {
            // 소켓이 null이거나 연결이 끊어진 경우 TimerService를 중지
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                val serviceIntent = Intent(requireContext(), TimerService::class.java)
                requireContext().stopService(serviceIntent)
                Log.d("MainFragment", "Bluetooth 연결 끊김 감지 - TimerService stopped")
            }
            connectionHandler.postDelayed(this, 1000)
        }
    }

    companion object {
        // 블루투스 활성화 요청 코드 및 권한 요청 코드 정의
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")    // Serial Port Profile UUID (블루투스 통신에 사용)
    }

    // 블루투스 상태 변경을 감지하는 BroadcastReceiver
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        updateBluetoothButtonTint()
                        val serviceIntent = Intent(requireContext(), TimerService::class.java)
                        requireContext().stopService(serviceIntent)
                    }
                    BluetoothAdapter.STATE_ON -> updateBluetoothButtonTint()
                }
            }
        }
    }

    // 블루투스 기기 검색 관련 BroadcastReceiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (!discoveredDevices.contains(it)) {
                            discoveredDevices.add(it)
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    requireContext().unregisterReceiver(this)
                    progressDialog?.dismiss()
                    showDevicesDialog()
                }
            }
        }
    }
    // 발견된 블루투스 기기를 저장
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // TimerService에서 주기적으로 보내는 타이머 틱(Broadcast)을 수신하여 UI를 업데이트하는 리시버
    private val timerTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val seconds = intent?.getIntExtra("elapsedSeconds", 0) ?: 0
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(seconds)}"
            mainViewModel.elapsedSeconds.value = seconds
            Log.d("MainFragment", "Received timer tick: $seconds seconds")
        }
    }

    // Fragment가 생성될 때 ViewModel을 초기화
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    // 레이아웃을 확장하고 UI 구성요소 및 이벤트 리스너를 초기화
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMainBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 블루투스 초기화 및 선택된 사용자 프로필 불러오기
        initializeBluetooth()
        loadSelectedProfile()

        // 타이머가 0이면 세션 타이머 초기화
        if (mainViewModel.elapsedSeconds.value == 0) {
            resetSessionTimer()
        }

        // NumberPicker 설정
        val stepArr = arrayOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10")
        val max = stepArr.size - 1
        val min = 0

        binding.leftNumberPicker.maxValue = max
        binding.rightNumberPicker.maxValue = max
        binding.leftNumberPicker.minValue = min
        binding.rightNumberPicker.minValue = min

        binding.leftNumberPicker.displayedValues = stepArr
        binding.rightNumberPicker.displayedValues = stepArr
        binding.leftNumberPicker.wrapSelectorWheel = false
        binding.rightNumberPicker.wrapSelectorWheel = false

        binding.leftNumberPicker.setOnValueChangedListener { _, _, newVal ->
            mainViewModel.leftPressure = newVal
        }
        binding.rightNumberPicker.setOnValueChangedListener { _, _, newVal ->
            mainViewModel.rightPressure = newVal
        }

        // 모드 선택 ("사용자 설정", "약", "중", "강")
        val modeOptions = resources.getStringArray(R.array.mode_list)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modeOptions)
        binding.spinnerMode.adapter = spinnerAdapter
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedMode = modeOptions[position]
                if (selectedMode == "사용자 설정") {
                    // 사용자 설정 선택 시 NumberPicker 활성화 및 오버레이 숨김
                    binding.leftNumberPicker.isEnabled = true
                    binding.rightNumberPicker.isEnabled = true
                    binding.controllerContainer.alpha = 1.0f
                    binding.overlayAutoMode.visibility = View.GONE
                    binding.tvAutoMode.visibility = View.GONE
                } else {
                    // 프리셋 모드 선택 시 NumberPicker 비활성화 및 오버레이 표시
                    binding.leftNumberPicker.isEnabled = false
                    binding.rightNumberPicker.isEnabled = false
                    binding.overlayAutoMode.visibility = View.VISIBLE
                    binding.tvAutoMode.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }

        // Bluetooth 버튼 클릭 이벤트 처리
        binding.btnBluetooth.setOnClickListener {
            // 블루투스 연결 권한이 없으면 권한 요청
            if (!hasBluetoothConnectPermission()) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_CODE_PERMISSIONS)
                return@setOnClickListener
            }
            // 블루투스가 꺼져 있으면 활성화 요청, 이미 켜져 있으면 기기 검색 시작
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            } else {
                Toast.makeText(requireContext(), "Bluetooth is already enabled.", Toast.LENGTH_SHORT).show()
                startDiscovery()
            }
        }

        // Sync 버튼 클릭 시 선택된 모드에 따라 기기에 명령 전송
        binding.btnSync.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Toast.makeText(requireContext(), "아직 기기에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedMode = binding.spinnerMode.selectedItem.toString()
            if (selectedMode == "사용자 설정") {
                val leftValue: Int = binding.leftNumberPicker.value
                val rightValue: Int = binding.rightNumberPicker.value
                val command = "U $leftValue,$rightValue.\n"
                try {
                    bluetoothSocket?.outputStream?.write(command.toByteArray())
                    Toast.makeText(requireContext(), "동기화 명령 전송 완료", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainFragment", "전송 실패: ${e.message}", e)
                    Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // 프로필의 성별에 따라 명령 값이 다르게 전송됨
                val currentGender = ProfileManager.currentProfile?.gender ?: "남"
                val command = when (selectedMode) {
                    "약" -> if (currentGender == "남") "L\n" else "l\n"
                    "중" -> if (currentGender == "남") "M\n" else "m\n"
                    "강" -> if (currentGender == "남") "H\n" else "h\n"
                    else -> "$selectedMode\n"
                }
                try {
                    bluetoothSocket?.outputStream?.write(command.toByteArray())
                    Toast.makeText(requireContext(), "$selectedMode 모드 명령 전송 완료", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainFragment", "전송 실패: ${e.message}", e)
                    Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // ReleaseBand 버튼 클릭 시 밴드 풀기 명령 전송
        binding.btnReleaseBand.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Toast.makeText(requireContext(), "아직 기기에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                bluetoothSocket?.outputStream?.write("e\n".toByteArray())
                Toast.makeText(requireContext(), "밴드 풀기 명령 전송 완료", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // 초기 버튼 색상 설정 (btnBuzzer, btnSilence 버튼 모두 white)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.default_white)
        val navyColor = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
        binding.btnBuzzer.setColorFilter(whiteColor)
        binding.btnSilence.setColorFilter(whiteColor)

        // btnBuzzer 버튼 클릭 시, 'B' 전송 및 버튼 색상 변경
        binding.btnBuzzer.setOnClickListener {
            sendData("B\n")
            binding.btnBuzzer.setColorFilter(navyColor)     // 선택된 버튼을 navy 색으로 변경
            binding.btnSilence.setColorFilter(whiteColor)   // 나머지 버튼은 white 색 유지
        }

        // btnSilence 버튼 클릭 시, 'S' 전송 및 버튼 색상 변경
        binding.btnSilence.setOnClickListener {
            sendData("S\n")
            binding.btnSilence.setColorFilter(navyColor)    // 선택된 버튼을 navy 색으로 변경
            binding.btnBuzzer.setColorFilter(whiteColor)    // 나머지 버튼은 white 색 유지
        }

        updateBluetoothButtonTint()                 // 블루투스 버튼의 색상 업데이트
        loadSelectedProfile()                       // 프로필 정보 재로드
        connectionHandler.post(connectionMonitor)   // 블루투스 연결 모니터링

        return root
    }

    // 타이머와 프로필의 사용 시간을 0으로 초기화
    private fun resetSessionTimer() {
        mainViewModel.elapsedSeconds.value = 0
        ProfileManager.currentProfile?.usedTimeSeconds = 0
        ProfileManager.updateCurrentProfile(ProfileManager.currentProfile ?: return, requireContext())
        binding.timerTextView.text = "사용한 시간: 00:00:00"
    }

    // 프래그먼트가 포그라운드에 나타날 때 사용자 프로필과 타이머를 업데이트하고 리시버 등록
    override fun onResume() {
        super.onResume()
        ProfileManager.loadCurrentProfile(requireContext())
        val current = ProfileManager.currentProfile
        if (current != null) {
            binding.textUserName.text = current.name
            val elapsed = mainViewModel.elapsedSeconds.value ?: current.usedTimeSeconds
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(elapsed)}"
            if (current.id != mainViewModel.previousProfileId) {
                mainViewModel.elapsedSeconds.value = 0
                mainViewModel.previousProfileId = current.id
            }
        } else {
            binding.textUserName.text = "USER"
            binding.timerTextView.text = "사용한 시간: 00:00:00"
            mainViewModel.elapsedSeconds.value = 0
            mainViewModel.previousProfileId = null
        }

        loadSelectedProfile()

        // 블루투스 상태 변화와 타이머 틱 이벤트를 수신하기 위해 리시버 등록
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireContext().registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        requireContext().registerReceiver(timerTickReceiver, IntentFilter("TIMER_TICK"), Context.RECEIVER_NOT_EXPORTED)
    }

    // 프래그먼트가 백그라운드로 전환될 때 연결 모니터링 중지 및 리시버 등록 해제
    override fun onPause() {
        super.onPause()
        connectionHandler.removeCallbacks(connectionMonitor)
        requireContext().unregisterReceiver(bluetoothStateReceiver)
        requireContext().unregisterReceiver(timerTickReceiver)
    }

    // onDestroyView: 뷰가 파괴될 때 바인딩 객체를 null로 설정하여 메모리 누수 방지
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // 초(seconds)형태의 시간을 HH:MM:SS 형식으로 변환하는 헬퍼 함수
    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // 선택된 사용자 프로필을 불러와서 UI의 이름과 타이머를 업데이트
    private fun loadSelectedProfile() {
        ProfileManager.loadCurrentProfile(requireContext())
        val current = ProfileManager.currentProfile
        if (current != null) {
            binding.textUserName.text = current.name
            val elapsed = mainViewModel.elapsedSeconds.value ?: current.usedTimeSeconds
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(elapsed)}"
        } else {
            binding.textUserName.text = "USER"
            binding.timerTextView.text = "사용한 시간: 00:00:00"
        }
    }

    // 블루투스 어댑터 초기화: 시스템의 BluetoothManager를 통해 어댑터를 얻음
    private fun initializeBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    // 블루투스 버튼의 색상을 현재 블루투스 상태와 권한에 따라 업데이트
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

    // 필요한 권한 확인 후 기기 검색 시작, 진행 상태 다이얼로그 표시
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

    // 검색된 블루투스 기기를 선택할 수 있는 다이얼로그 표시
    private fun showDevicesDialog() {
        val pairedDevices: Set<BluetoothDevice> =
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bondedDevices
            } else {
                emptySet()
            }

        // 등록된 기기와 검색된 기기를 모두 합쳐서 표시 (기기 이름이 null이 아닌 기기만 표시)
        val allDevices = mutableSetOf<BluetoothDevice>()
        allDevices.addAll(pairedDevices.filter { it.name?.isNotEmpty() == true })
        allDevices.addAll(discoveredDevices.filter { it.name?.isNotEmpty() == true })

        if (allDevices.isEmpty()) {
            Toast.makeText(requireContext(), "No Bluetooth devices found.", Toast.LENGTH_SHORT).show()
            return
        }

        // 각 기기를 이름과 주소 형태로 포맷하여 다이얼로그에 표시
        val deviceNames = allDevices.map { device ->
            val name = try {
                if (hasBluetoothConnectPermission()) device.name else "Permission not granted"
            } catch (e: SecurityException) {
                "Error"
            }
            if (pairedDevices.contains(device)) "$name (Registered)\n${device.address}" else "$name\n${device.address}"
        }.toTypedArray()

        // AlertDialog를 만들어 기기 선택 후 연결 시도
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

    // 선택된 블루투스 기기에 연결 시도 (백그라운드 스레드에서 실행)
    private fun connectToDevice(device: BluetoothDevice) {
        val connectDialog = ProgressDialog(requireContext()).apply {
            val deviceName = try {
                if (hasBluetoothConnectPermission()) device.name ?: device.address else device.address
            } catch (e: SecurityException) {
                device.address
            }
            setMessage("Connecting to $deviceName...")
            setCancelable(false)
            show()
        }
        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                bluetoothSocket = socket
                activity?.runOnUiThread {
                    connectDialog.dismiss()
                    Toast.makeText(requireContext(), "Connected to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                    ProfileManager.loadCurrentProfile(requireContext())
                    val serviceIntent = Intent(requireContext(), TimerService::class.java)
                    serviceIntent.putExtra("initialTime", 0)
                    requireContext().stopService(serviceIntent)
                    requireContext().startService(serviceIntent)
                    Log.d("MainFragment", "Bluetooth connected. Timer service started with initial time: 0")
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

    // 블루투스 활성화 요청 후 결과 처리
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

    // 블루투스 및 위치 관련 권한 요청 결과 처리
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

    // 블루투스 소켓을 통해 데이터를 전송하는 함수
    private fun sendData(data: String) {
        if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
            Toast.makeText(requireContext(), "기기에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            bluetoothSocket?.outputStream?.write(data.toByteArray())
        } catch (e: IOException) {
            Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MainFragment", "전송 실패: ${e.message}", e)
        }
    }
}
