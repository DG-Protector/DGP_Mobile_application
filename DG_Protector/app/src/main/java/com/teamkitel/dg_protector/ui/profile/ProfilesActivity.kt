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

class ProfilesActivity : AppCompatActivity(), ProfilesAdapter.OnProfileItemClickListener {

    private var _binding: LayoutProfilesBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val REQUEST_CODE_CREATE_PROFILE = 102
    }

    private val profilesList = mutableListOf<ProfileData>()
    private lateinit var adapter: ProfilesAdapter
    private var mode: String = "select"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _binding = LayoutProfilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mode = intent.getStringExtra("mode") ?: "select"

        adapter = ProfilesAdapter(profilesList, this)
        binding.recyclerViewProfiles.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewProfiles.adapter = adapter

        loadProfiles()

        binding.btnProfileCreate.setOnClickListener {
            if (mode == "select") {
                val intent = Intent(this, CreateProfileActivity::class.java)
                intent.putExtra("mode", "select")
                startActivityForResult(intent, REQUEST_CODE_CREATE_PROFILE)
            } else {
                mode = "select"
                binding.userName.text = "계정을 선택해주세요."
                binding.btnProfileCreate.text = "사용자 추가"
                Toast.makeText(this, "수정 모드 종료", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnModeEdit.setOnClickListener {
            mode = "edit"
            binding.userName.text = "수정할 계정을 선택해주세요."
            binding.btnProfileCreate.text = "완료"
            Toast.makeText(this, "수정 모드로 전환됨", Toast.LENGTH_SHORT).show()
        }

        binding.btnModeDelete.setOnClickListener {
            mode = "delete"
            binding.userName.text = "삭제할 계정을 선택해주세요."
            binding.btnProfileCreate.text = "완료"
            Toast.makeText(this, "삭제 모드로 전환됨", Toast.LENGTH_SHORT).show()
        }

        binding.userName.text = "계정을 선택해주세요."
    }

    override fun onProfileItemClick(position: Int, profile: ProfileData) {
        when (mode) {
            "edit" -> {
                AlertDialog.Builder(this)
                    .setTitle("프로필 수정")
                    .setMessage("프로필을 수정하시겠습니까?")
                    .setPositiveButton("확인") { _, _ ->
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
            }
            "delete" -> {
                AlertDialog.Builder(this)
                    .setTitle("프로필 삭제")
                    .setMessage("프로필을 삭제하시겠습니까?")
                    .setPositiveButton("확인") { _, _ ->
                        val deletedProfile = profilesList[position]
                        profilesList.removeAt(position)
                        adapter.notifyItemRemoved(position)
                        Toast.makeText(this, "프로필이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                        saveProfiles()

                        // 현재 선택된 프로필과 삭제된 프로필이 동일하다면 ProfileManager 업데이트
                        ProfileManager.loadCurrentProfile(this)
                        val current = ProfileManager.currentProfile
                        if (current != null && current.id == deletedProfile.id) {
                            if (profilesList.isNotEmpty()) {
                                // 목록에 남은 첫 번째 프로필로 업데이트 (원하는 동작에 따라 수정)
                                ProfileManager.updateCurrentProfile(profilesList.first(), this)
                            } else {
                                ProfileManager.clearCurrentProfile(this)
                            }
                        }
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            else -> {
                // 선택 모드: ProfileManager를 통해 현재 프로필 업데이트
                ProfileManager.updateCurrentProfile(profile, this)
                Toast.makeText(this, "선택된 프로필: ${profile.name}", Toast.LENGTH_SHORT).show()

                val resultIntent = Intent().apply {
                    putExtra("selectedProfileName", profile.name)
                    putExtra("selectedProfileAge", profile.age)
                    putExtra("selectedProfileGender", profile.gender)
                    putExtra("selectedProfileHeight", profile.height)
                    putExtra("selectedProfileWeight", profile.weight)
                }
                setResult(RESULT_OK, resultIntent)
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_PROFILE && resultCode == RESULT_OK) {
            val profileName = data?.getStringExtra("profileName") ?: "Unknown"
            val profileAge = data?.getStringExtra("profileAge") ?: "Unknown"
            val profileGender = data?.getStringExtra("profileGender") ?: "Unknown"
            val profileHeight = data?.getStringExtra("profileHeight") ?: "Unknown"
            val profileWeight = data?.getStringExtra("profileWeight") ?: "Unknown"
            val editPosition = data?.getIntExtra("editPosition", -1) ?: -1

            if (editPosition >= 0) {
                val oldProfile = profilesList[editPosition]
                val updatedProfile = ProfileData(
                    id = oldProfile.id,
                    name = profileName,
                    age = profileAge,
                    gender = profileGender,
                    height = profileHeight,
                    weight = profileWeight,
                    usedTimeSeconds = oldProfile.usedTimeSeconds,
                    lastUsedTimestamp = oldProfile.lastUsedTimestamp
                )
                profilesList[editPosition] = updatedProfile
                adapter.notifyItemChanged(editPosition)
                Toast.makeText(this, "프로필이 수정되었습니다.", Toast.LENGTH_SHORT).show()
            } else {
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
            mode = "select"
            binding.userName.text = "계정을 선택해주세요."
            binding.btnProfileCreate.text = "사용자 추가"
            saveProfiles()
        }
    }

    private fun saveProfiles() {
        val prefs = getSharedPreferences("profiles", MODE_PRIVATE)
        val gson = Gson()
        val json = gson.toJson(profilesList)
        prefs.edit().putString("profilesList", json).apply()
    }

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
        _binding = null
    }
}