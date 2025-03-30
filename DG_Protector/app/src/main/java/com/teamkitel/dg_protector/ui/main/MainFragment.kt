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

    private lateinit var mainViewModel: MainViewModel
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var progressDialog: ProgressDialog? = null

    private var previousProfileId: String? = null

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

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

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

    private fun hasBluetoothConnectPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val timerTickReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val seconds = intent?.getIntExtra("elapsedSeconds", 0) ?: 0
            binding.timerTextView.text = "사용한 시간: ${formatSecondsToHMS(seconds)}"
            mainViewModel.elapsedSeconds.value = seconds
            Log.d("MainFragment", "Received timer tick: $seconds seconds")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        loadSelectedProfile()

        if (mainViewModel.elapsedSeconds.value == 0) {
            resetSessionTimer()
        }

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

        val modeOptions = resources.getStringArray(R.array.mode_list)
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, modeOptions)
        binding.spinnerMode.adapter = spinnerAdapter
        binding.spinnerMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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

        // 초기 tint 색상 설정 (두 버튼 모두 white로 시작)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.default_white)
        val navyColor = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
        binding.btnBuzzer.setColorFilter(whiteColor)
        binding.btnSilence.setColorFilter(whiteColor)

        // btnBuzzer 클릭 시 이벤트 처리
        binding.btnBuzzer.setOnClickListener {
            sendData("B\n") // 'B' 전송
            binding.btnBuzzer.setColorFilter(navyColor)   // 클릭된 버튼 navy로
            binding.btnSilence.setColorFilter(whiteColor)   // 다른 버튼 white로
        }

        // btnSilence 클릭 시 이벤트 처리
        binding.btnSilence.setOnClickListener {
            sendData("S\n") // 'S' 전송
            binding.btnSilence.setColorFilter(navyColor)    // 클릭된 버튼 navy로
            binding.btnBuzzer.setColorFilter(whiteColor)      // 다른 버튼 white로
        }

        updateBluetoothButtonTint()
        loadSelectedProfile()
        connectionHandler.post(connectionMonitor)

        return root
    }

    private fun resetSessionTimer() {
        mainViewModel.elapsedSeconds.value = 0
        ProfileManager.currentProfile?.usedTimeSeconds = 0
        ProfileManager.updateCurrentProfile(ProfileManager.currentProfile ?: return, requireContext())
        binding.timerTextView.text = "사용한 시간: 00:00:00"
    }

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

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        requireContext().registerReceiver(bluetoothStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        requireContext().registerReceiver(timerTickReceiver, IntentFilter("TIMER_TICK"), Context.RECEIVER_NOT_EXPORTED)
    }

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

    private fun initializeBluetooth() {
        val bluetoothManager = requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null) {
            Toast.makeText(requireContext(), "Bluetooth is not supported on this device.", Toast.LENGTH_SHORT).show()
        }
    }

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

    private fun showDevicesDialog() {
        val pairedDevices: Set<BluetoothDevice> =
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.bondedDevices
            } else {
                emptySet()
            }
        val allDevices = mutableSetOf<BluetoothDevice>()
        allDevices.addAll(pairedDevices.filter { it.name?.isNotEmpty() == true })
        allDevices.addAll(discoveredDevices.filter { it.name?.isNotEmpty() == true })

        if (allDevices.isEmpty()) {
            Toast.makeText(requireContext(), "No Bluetooth devices found.", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceNames = allDevices.map { device ->
            val name = try {
                if (hasBluetoothConnectPermission()) device.name else "Permission not granted"
            } catch (e: SecurityException) {
                "Error"
            }
            if (pairedDevices.contains(device)) "$name (Registered)\n${device.address}" else "$name\n${device.address}"
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
