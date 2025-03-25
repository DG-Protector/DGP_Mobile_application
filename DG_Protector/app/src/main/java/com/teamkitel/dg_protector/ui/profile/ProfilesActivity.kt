package com.teamkitel.dg_protector.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutProfilesBinding

// 프로필 목록 관리를 위한 액티비티 Class
class ProfilesActivity : AppCompatActivity(), ProfilesAdapter.OnProfileItemClickListener {

    // 뷰바인딩 변수, 레이아웃 연결용임
    private var _binding: LayoutProfilesBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val REQUEST_CODE_CREATE_PROFILE = 102  // 프로필 생성/수정 요청 코드
    }

    // 프로필 데이터를 담을 리스트
    private val profilesList = mutableListOf<ProfileData>()
    // recyclerView 어댑터 선언
    private lateinit var adapter: ProfilesAdapter

    // 모드 변수, "select", "edit", "delete" 중 하나로 동작 모드 결정함
    private var mode: String = "select"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge to edge 활성화함
        enableEdgeToEdge()
        // layout inflate해서 Viewbinding 초기화
        _binding = LayoutProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 인텐트로 받은 모드 값 저장함, 없으면 기본 "select" 모드 사용함
        mode = intent.getStringExtra("mode") ?: "select"

        // 어댑터 초기화하고 recyclerView에 설정함
        adapter = ProfilesAdapter(profilesList, this)
        binding.recyclerViewProfiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewProfiles.adapter = adapter

        // 저장된 프로필 로드
        loadProfiles()

        // "프로필 추가" 버튼 클릭 시 동작 설정함
        binding.btnProfileCreate.setOnClickListener {
            if (mode == "select") {
                // 선택 모드이면 새 프로필 생성 액티비티 실행함
                val intent = Intent(this, CreateProfileActivity::class.java)
                intent.putExtra("mode", "select")
                startActivityForResult(intent, REQUEST_CODE_CREATE_PROFILE)
            } else {
                // 수정/삭제 모드였다면 모드를 "select"로 전환함(추가 설명: 수정/삭제 모드에서는 '프로필 추가'버튼이 '완료'버튼으로 바뀜)
                mode = "select"
                binding.userName.text = "계정을 선택해주세요."
                binding.btnProfileCreate.text = "사용자 추가"
                Toast.makeText(this, "수정 모드 종료", Toast.LENGTH_SHORT).show()
            }
        }

        // "수정 모드" 버튼 클릭 시 동작 설정
        binding.btnModeEdit.setOnClickListener {
            mode = "edit"
            binding.userName.text = "수정할 계정을 선택해주세요."
            binding.btnProfileCreate.text = "완료"
            Toast.makeText(this, "수정 모드로 전환됨", Toast.LENGTH_SHORT).show()
        }

        // "삭제 모드" 버튼 클릭 시 동작 설정
        binding.btnModeDelete.setOnClickListener {
            mode = "delete"
            binding.userName.text = "삭제할 계정을 선택해주세요."
            binding.btnProfileCreate.text = "완료"
            Toast.makeText(this, "삭제 모드로 전환됨", Toast.LENGTH_SHORT).show()
        }

        // 기본 텍스트 설정
        binding.userName.text = "계정을 선택해주세요."
    }

    // 프로필 아이템 클릭 시 호출 (어댑터 인터페이스 구현)
    override fun onProfileItemClick(position: Int, profile: ProfileData) {
        when (mode) {
            "edit" -> {
                // 수정 모드: 프로필을 수정할 것인지 사용자에게 묻는 다이얼로그 표시
                AlertDialog.Builder(this)
                    .setTitle("프로필 수정")
                    .setMessage("프로필을 수정하시겠습니까?")
                    .setPositiveButton("확인") { _, _ ->
                        // 수정 액티비티 실행함, 기존 데이터 전달함
                        val intent = Intent(this, CreateProfileActivity::class.java).apply {
                            putExtra("profileName", profile.name)
                            putExtra("profileAge", profile.age)
                            putExtra("profileGender", profile.gender)
                            putExtra("profileHeight", profile.height)
                            putExtra("profileWeight", profile.weight)
                            putExtra("editPosition", position)
                            putExtra("mode", "edit")
                        }
                        startActivityForResult(intent, REQUEST_CODE_CREATE_PROFILE)
                    }
                    .setNegativeButton("취소", null)
                    .show()
                // 수정 모드에서는 액티비티 종료하지 않음
            }
            "delete" -> {
                // 삭제 모드: 프로필을 삭제할 것인지 사용자에게 묻는 다이얼로그 표시
                AlertDialog.Builder(this)
                    .setTitle("프로필 삭제")
                    .setMessage("프로필을 삭제하시겠습니까?")
                    .setPositiveButton("확인") { _, _ ->
                        profilesList.removeAt(position)  // 리스트에서 삭제
                        adapter.notifyItemRemoved(position)
                        Toast.makeText(this, "프로필이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        saveProfiles()  // 변경사항 저장
                    }
                    .setNegativeButton("취소", null)
                    .show()
                // 삭제 모드에서는 액티비티 종료x
            }
            else -> {
                // 선택 모드: 선택된 프로필을 저장하고 결과 반환
                val gson = Gson()
                val prefs = getSharedPreferences("selectedProfile", MODE_PRIVATE)
                val existingJson = prefs.getString("selectedProfileJson", null)
                val existingProfile = if (existingJson != null) {
                    gson.fromJson(existingJson, ProfileData::class.java)
                } else null

                // 기존에 선택된 프로필과 동일하면 사용시간 유지, 아니면 새 프로필 사용
                val selectedProfile = if (existingProfile != null && existingProfile.id == profile.id) {
                    profile.copy(
                        usedTimeSeconds = existingProfile.usedTimeSeconds,
                        lastUsedTimestamp = existingProfile.lastUsedTimestamp
                    )
                } else {
                    profile
                }

                prefs.edit().putString("selectedProfileJson", gson.toJson(selectedProfile)).apply()
                Toast.makeText(this, "선택된 프로필: ${selectedProfile.name}", Toast.LENGTH_SHORT).show()

                // 결과 인텐트에 선택된 프로필 정보 담아서 반환
                val resultIntent = Intent().apply {
                    putExtra("selectedProfileName", selectedProfile.name)
                    putExtra("selectedProfileAge", selectedProfile.age)
                    putExtra("selectedProfileGender", selectedProfile.gender)
                    putExtra("selectedProfileHeight", selectedProfile.height)
                    putExtra("selectedProfileWeight", selectedProfile.weight)
                }
                setResult(RESULT_OK, resultIntent)
                finish()  // 액티비티 종료
            }
        }
    }

    // 프로필 생성/수정 액티비티 결과 처리
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_PROFILE && resultCode == RESULT_OK) {
            // 전달받은 프로필 데이터 읽어옴, 없으면 "Unknown"으로 처리
            val profileName = data?.getStringExtra("profileName") ?: "Unknown"
            val profileAge = data?.getStringExtra("profileAge") ?: "Unknown"
            val profileGender = data?.getStringExtra("profileGender") ?: "Unknown"
            val profileHeight = data?.getStringExtra("profileHeight") ?: "Unknown"
            val profileWeight = data?.getStringExtra("profileWeight") ?: "Unknown"
            val editPosition = data?.getIntExtra("editPosition", -1) ?: -1

            if (editPosition >= 0) {
                // 수정 모드: 기존 프로필의 누적 사용 시간 유지
                val oldProfile = profilesList[editPosition]
                val updatedProfile = ProfileData(
                    id = oldProfile.id,  // 기존 고유 식별자 유지함
                    name = profileName,
                    age = profileAge,
                    gender = profileGender,
                    height = profileHeight,
                    weight = profileWeight,
                    usedTimeSeconds = oldProfile.usedTimeSeconds,       // 기존 사용 시간
                    lastUsedTimestamp = oldProfile.lastUsedTimestamp      // 기존 마지막 사용 시각
                )
                profilesList[editPosition] = updatedProfile
                adapter.notifyItemChanged(editPosition)
                Toast.makeText(this, "프로필이 수정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
                // 새 프로필 추가
                val newProfile = ProfileData(
                    name = profileName,
                    age = profileAge,
                    gender = profileGender,
                    height = profileHeight,
                    weight = profileWeight
                )
                profilesList.add(newProfile)
                adapter.notifyItemInserted(profilesList.size - 1)
                Toast.makeText(this, "새 프로필이 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
            // 모드를 "select"로 전환하고 UI 업데이트함
            mode = "select"
            binding.userName.text = "계정을 선택해주세요."
            binding.btnProfileCreate.text = "사용자 추가"
            saveProfiles()  // 변경된 프로필 리스트 저장
        }
    }

    // 프로필 리스트를 SharedPreferences에 저장
    private fun saveProfiles() {
        val prefs = getSharedPreferences("profiles", MODE_PRIVATE)
        val editor = prefs.edit()
        val gson = Gson()
        val json = gson.toJson(profilesList)
        editor.putString("profilesList", json)
        editor.apply()
    }

    // SharedPreferences에서 저장된 프로필 리스트를 로드
    private fun loadProfiles() {
        val prefs = getSharedPreferences("profiles", MODE_PRIVATE)
        val gson = Gson()
        val json = prefs.getString("profilesList", null)
        if (json != null) {
            val type = object : TypeToken<List<ProfileData>>() {}.type
            val savedList: List<ProfileData> = gson.fromJson(json, type)
            profilesList.clear()
            profilesList.addAll(savedList)
            adapter.notifyDataSetChanged()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 뷰바인딩 해제
        _binding = null
    }
}
