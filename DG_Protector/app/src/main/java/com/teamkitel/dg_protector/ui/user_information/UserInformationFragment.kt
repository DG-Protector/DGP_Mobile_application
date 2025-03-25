package com.teamkitel.dg_protector.ui.user_information

import android.app.Activity
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.gson.Gson
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutUserInformationBinding
import com.teamkitel.dg_protector.ui.profile.ProfileData
import com.teamkitel.dg_protector.ui.profile.ProfilesActivity

// 사용자 정보 Fragment Class
class UserInformationFragment : Fragment() {

    private var _binding: LayoutUserInformationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: UserInformationViewModel

    // 프로필 선택 결과를 받아옴
    private val profilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // 선택된 프로필 정보 받아와서 업데이트함 (selectedProfileJson 형식으로 저장)
            val name = data?.getStringExtra("selectedProfileName")
            val age = data?.getStringExtra("selectedProfileAge")
            val gender = data?.getStringExtra("selectedProfileGender")
            val height = data?.getStringExtra("selectedProfileHeight")
            val weight = data?.getStringExtra("selectedProfileWeight")
            updateProfileInfo(name, age, gender, height, weight)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // viewModel 초기화
        viewModel = ViewModelProvider(this).get(UserInformationViewModel::class.java)
        // ViewBinging 인플레이트
        _binding = LayoutUserInformationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 프로필 설정 버튼 클릭 시 ProfilesActivity 실행
        binding.btnProfileSetting2.setOnClickListener {
            val intent = Intent(requireContext(), ProfilesActivity::class.java)
            profilesLauncher.launch(intent)
        }
        // 저장된 프로필 정보 불러옴
        loadProfileInfo()
        return root
    }


    // updateProfileInfo() - UI 업데이트하고 선택된 프로필 정보를 SharedPreferences에 저장
    fun updateProfileInfo(name: String?, age: String?, gender: String?, height: String?, weight: String?) {
        // UI 업데이트함
        binding.newProfileName.text = name ?: "USER"
        binding.itemTextProfileLastUsage.text =
            "나이: ${age ?: "-"}, 성별: ${gender ?: "-"}\n키: ${height ?: "-"}cm, 몸무게: ${weight ?: "-"}kg"

        // SharedPreferences에 프로필 정보 저장
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString("selectedProfileJson", null)
        val existingProfile = if (existingJson != null) {
            gson.fromJson(existingJson, ProfileData::class.java)
        } else null

        // 기존 프로필 있으면 기존 usedTimeSeconds, lastUsedTimestamp 유지하고, 없으면 초기값 사용함
        val updatedProfile = if (existingProfile != null && existingProfile.id == (existingProfile.id)) {
            ProfileData(
                id = existingProfile.id, // 기존 id 유지
                name = name ?: "USER",
                age = age ?: "-",
                gender = gender ?: "-",
                height = height ?: "-",
                weight = weight ?: "-",
                usedTimeSeconds = existingProfile.usedTimeSeconds, // 기존 사용 시간 유지
                lastUsedTimestamp = existingProfile.lastUsedTimestamp // 기존 마지막 사용 시각 유지
            )
        } else {
            ProfileData(
                name = name ?: "USER",
                age = age ?: "-",
                gender = gender ?: "-",
                height = height ?: "-",
                weight = weight ?: "-",
                usedTimeSeconds = 0,
                lastUsedTimestamp = 0L
            )
        }
        prefs.edit().putString("selectedProfileJson", gson.toJson(updatedProfile)).apply()
    }


    // loadProfileInfo() - 저장된 SharedPreferences에서 프로필 정보를 불러와 UI 업데이트
    // "selectedProfileJson" 있으면 사용, 없으면 개별 키(fallback) 사용

    private fun loadProfileInfo() {
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        if (profileJson != null) {
            val profile = Gson().fromJson(profileJson, ProfileData::class.java)
            updateProfileInfo(profile.name, profile.age, profile.gender, profile.height, profile.weight)
        } else {
            // JSON 데이터 없으면 fallback으로 개별 키 불러옴
            val name = prefs.getString("name", null)
            if (name != null) {
                val age = prefs.getString("age", null)
                val gender = prefs.getString("gender", null)
                val height = prefs.getString("height", null)
                val weight = prefs.getString("weight", null)
                updateProfileInfo(name, age, gender, height, weight)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 뷰바인딩 해제
        _binding = null
    }
}
