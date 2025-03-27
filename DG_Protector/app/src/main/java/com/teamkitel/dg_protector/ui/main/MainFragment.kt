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
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainFragment : Fragment() {

    // ViewBinding
    private var _binding: LayoutMainBinding? = null
    private val binding get() = _binding!!

    // ViewModel 및 Bluetooth 관련 변수들
    private lateinit var mainViewModel: MainViewModel
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var progressDialog: ProgressDialog? = null

    // 현재 선택된 프로필 데이터 및 이전 프로필 id
    private var currentProfile: ProfileData? = null
    private var previousProfileId: String? = null

    // Bluetooth 연결 상태 모니터링을 위한 Handler와 Runnable
    private val connectionHandler = Handler(Looper.getMainLooper())
    private val connectionMonitor = object : Runnable {
        override fun run() {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                val serviceIntent = Intent(requireContext(), TimerService::class.java)
                requireContext().stopService(serviceIntent)
                Log.d("MainFragment", "Bluetooth 연결 끊김 감지 - TimerService stopped")
            }
            connectionHandler.postDelayed(this, 1000)
        }
    }

    // 실제 페어링 상태 변수
    private var isPaired: Boolean = false

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    // Bluetooth 상태 변경 BroadcastReceiver
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
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

    // Bluetooth 장치 검색 BroadcastReceiver
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
    private val discoveredDevices = mutableListOf<BluetoothDevice>()

    // BLUETOOTH_CONNECT 권한 확인 함수
    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    // TimerService의 타이머 틱 이벤트 수신 BroadcastReceiver
    private val timerTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val seconds = intent?.getIntExtra("elapsedSeconds", 0) ?: 0
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(seconds)}"
            mainViewModel.elapsedSeconds.value = seconds
            Log.d("MainFragment", "Received timer tick: $seconds seconds")
        }
    }

    override fun onStart() {
        super.onStart()
        val permissionsNeeded = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasBluetoothConnectPermission()) {
            permissionsNeeded.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ViewModel은 Activity 범위로 가져와 프로세스가 살아있을 때 값을 유지합니다.
        mainViewModel = ViewModelProvider(requireActivity()).get(MainViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = LayoutMainBinding.inflate(inflater, container, false)
        val root: View = binding.root

        initializeBluetooth()
        loadSelectedProfile() // 프로필과 타이머 값을 불러옴

        // ViewModel의 타이머 값이 0인 경우(즉, 새 프로세스에서 처음 시작된 경우)만 세션 타이머를 초기화합니다.
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

        // Spinner 설정
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
                if (selectedMode == "사용자 설정") {
                    binding.leftNumberPicker.isEnabled = true
                    binding.rightNumberPicker.isEnabled = true
                    binding.controllerContainer.alpha = 1.0f
                    binding.overlayAutoMode.visibility = View.GONE
                    binding.tvAutoMode.visibility = View.GONE
                } else {
                    binding.leftNumberPicker.isEnabled = false
                    binding.rightNumberPicker.isEnabled = false
                    binding.overlayAutoMode.visibility = View.VISIBLE
                    binding.tvAutoMode.visibility = View.VISIBLE
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }

        // Bluetooth 버튼 클릭 이벤트
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

        // 동기화 버튼 클릭 이벤트
        binding.btnSync.setOnClickListener {
            if (bluetoothSocket == null || !bluetoothSocket!!.isConnected) {
                Toast.makeText(requireContext(), "아직 기기에 연결되지 않았습니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedMode = binding.spinnerMode.selectedItem.toString()
            if (selectedMode == "사용자 설정") {
                val leftValue: Int = binding.leftNumberPicker.value
                val rightValue: Int = binding.rightNumberPicker.value
                val command = "${'U'} ${leftValue},${rightValue}.\n"
                try {
                    bluetoothSocket?.outputStream?.write(command.toByteArray())
                    Toast.makeText(requireContext(), "동기화 명령 전송 완료", Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    Log.e("MainFragment", "전송 실패: ${e.message}", e)
                    Toast.makeText(requireContext(), "전송 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                val currentGender = currentProfile?.gender ?: "남"
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

        updateBluetoothButtonTint()
        loadSelectedProfile()
        connectionHandler.post(connectionMonitor)

        return root
    }

    // 프로필 전환 시 호출되는 함수 – 세션 타이머를 00:00:00으로 초기화하고 TimerService를 재시작
    private fun onProfileChanged() {
        val serviceIntent = Intent(requireContext(), TimerService::class.java)
        requireContext().stopService(serviceIntent)

        currentProfile?.usedTimeSeconds = 0
        mainViewModel.elapsedSeconds.value = 0

        // UI 직접 업데이트: 타이머 텍스트를 00:00:00으로 설정
        binding.timerTextView.text = "사용한 시간: 00:00:00"

        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        prefs.edit().putString("selectedProfileJson", Gson().toJson(currentProfile)).commit()

        loadSelectedProfile() // 새 값(0)이 반영되도록

        serviceIntent.putExtra("initialTime", 0)
        requireContext().startService(serviceIntent)
    }

    private fun resetSessionTimer() {
        // 세션 타이머 값을 0으로 초기화
        mainViewModel.elapsedSeconds.value = 0
        currentProfile?.usedTimeSeconds = 0

        // 변경된 프로필 정보를 SharedPreferences에 저장
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        prefs.edit().putString("selectedProfileJson", Gson().toJson(currentProfile)).apply()

        // UI 업데이트 (타이머 텍스트를 00:00:00으로)
        binding.timerTextView.text = "사용한 시간: 00:00:00"
    }

    override fun onResume() {
        super.onResume()
        // SharedPreferences에서 프로필을 불러옴
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        if (profileJson != null) {
            val loadedProfile = Gson().fromJson(profileJson, ProfileData::class.java)
            // 기존 프로필 ID가 다를 때만 타이머 초기화 (새 프로세스인 경우 ViewModel 값은 0)
            if (loadedProfile.id != mainViewModel.previousProfileId) {
                mainViewModel.elapsedSeconds.value = 0
                mainViewModel.previousProfileId = loadedProfile.id
            }
        }
        loadSelectedProfile()

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireContext().registerReceiver(bluetoothStateReceiver, filter)
        requireContext().registerReceiver(timerTickReceiver, IntentFilter("TIMER_TICK"))
    }

    // onPause()에서는 타이머 관련 업데이트 함수를 호출하지 않고, Receiver들을 해제합니다.
    override fun onPause() {
        super.onPause()
        connectionHandler.removeCallbacks(connectionMonitor)
        requireContext().unregisterReceiver(bluetoothStateReceiver)
        requireContext().unregisterReceiver(timerTickReceiver)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // SharedPreferences에서 프로필을 로드하여 UI에 반영
    private fun loadSelectedProfile() {
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        if (profileJson != null) {
            val loadedProfile = Gson().fromJson(profileJson, ProfileData::class.java)
            currentProfile = loadedProfile
            binding.textUserName.text = currentProfile?.name ?: "USER1"
            // 세션 타이머는 ViewModel의 값 또는 프로필의 usedTimeSeconds 값을 사용합니다.
            val elapsed = mainViewModel.elapsedSeconds.value ?: currentProfile?.usedTimeSeconds ?: 0
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(elapsed)}"
        } else {
            binding.textUserName.text = "USER1"
            binding.timerTextView.text = "사용한 시간: 00:00:00"
        }
    }

    // TimerService에서 주간 통계를 업데이트하므로, 여기서는 사용하지 않습니다.
    private fun updateCurrentProfileUsage() {
        // 사용하지 않음
    }

    private fun saveUsageTimeForToday(usedSeconds: Int) {
        val prefs = requireContext().getSharedPreferences("dailyUsage", Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        prefs.edit().putInt(today, usedSeconds).apply()
        Log.d("MainFragment", "Saved usage for $today: $usedSeconds seconds")
    }

    // BluetoothAdapter 초기화
    private fun initializeBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth 버튼 색상 업데이트
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

    // Bluetooth 장치 검색 시작
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

    // 검색된 Bluetooth 장치와 페어링된 장치를 다이얼로그로 표시
    private fun showDevicesDialog() {
        val pairedDevices: Set<BluetoothDevice> =
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bondedDevices
            } else {
                emptySet()
            }
        val allDevices = mutableSetOf<BluetoothDevice>()
        allDevices.addAll(pairedDevices.filter { it.name != null && it.name.isNotEmpty() })
        allDevices.addAll(discoveredDevices.filter { it.name != null && it.name.isNotEmpty() })

        if (allDevices.isEmpty()) {
            Toast.makeText(requireContext(), "No Bluetooth devices found.", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = allDevices.map { device ->
            val name = try {
                if (hasBluetoothConnectPermission()) {
                    device.name
                } else {
                    "Permission not granted"
                }
            } catch (e: SecurityException) {
                "Error"
            }
            if (pairedDevices.contains(device)) "$name (Registered)\n${device.address}"
            else "$name\n${device.address}"
        }.toTypedArray()

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

    // 선택된 Bluetooth 장치와 연결 시도
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
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                bluetoothSocket = socket
                activity?.runOnUiThread {
                    connectDialog.dismiss()
                    Toast.makeText(requireContext(), "Connected to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                    isPaired = true
                    loadSelectedProfile() // 프로필 정보 로드

                    // 연결 후 타이머 서비스를 0부터 시작하도록 초기화합니다.
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

    // Bluetooth 활성화 요청 결과 처리
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

    // 권한 요청 결과 처리
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
}
