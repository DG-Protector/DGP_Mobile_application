package com.teamkitel.dg_protector.ui.user_information

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope  // 추가: lifecycleScope 사용을 위해
import com.google.gson.Gson
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutUserInformationBinding
import com.teamkitel.dg_protector.datastore.getWeeklyUsageFlow
import com.teamkitel.dg_protector.ui.profile.ProfileData
import com.teamkitel.dg_protector.ui.profile.ProfilesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.view.Gravity


class UserInformationFragment : Fragment() {

    private var _binding: LayoutUserInformationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: UserInformationViewModel

    // 프로필 선택 결과를 받아옴
    private val profilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            // 선택된 프로필 정보 받아와 updateProfileInfo()로 업데이트
            val name = data?.getStringExtra("selectedProfileName")
            val age = data?.getStringExtra("selectedProfileAge")
            val gender = data?.getStringExtra("selectedProfileGender")
            val height = data?.getStringExtra("selectedProfileHeight")
            val weight = data?.getStringExtra("selectedProfileWeight")
            updateProfileInfo(name, age, gender, height, weight)
            // 프로필이 바뀌면 오늘 사용량도 다시 업데이트
            updateTodayUsage()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this).get(UserInformationViewModel::class.java)
        _binding = LayoutUserInformationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // 프로필 설정 버튼 클릭 시 ProfilesActivity 실행
        binding.btnProfileSetting2.setOnClickListener {
            val intent = Intent(requireContext(), ProfilesActivity::class.java)
            profilesLauncher.launch(intent)
        }
        // 기존 프로필 정보 로드
        loadProfileInfo()
        // 오늘 누적 사용시간 업데이트
        updateTodayUsage()

        return root
    }

    /**
     * updateTodayUsage()
     *
     * - 오늘 날짜(yyyy-MM-dd)를 구한 후 현재 선택된 프로필의 ID를 읽어옵니다.
     * - getWeeklyUsageFlow()를 사용하여 오늘 사용량(초)을 Flow로 받아오고,
     *   이를 HH:MM:SS 형태로 변환하여 todayUsageTextView에 표시합니다.
     * - 또한 목표(90분)를 기준으로 ProgressBar(usageProgressBar)의 진행률을 업데이트하고,
     *   사용시간에 따른 격려 메시지를 encouragementTextView에 표시합니다.
     */
    private fun updateTodayUsage() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val currentProfileId = getCurrentProfileId(requireContext())
        if (currentProfileId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                // 시작일과 종료일을 오늘로 설정하여 오늘 사용량만 받아옴
                getWeeklyUsageFlow(requireContext(), currentProfileId, today, today).collect { usageMap ->
                    val todayUsageSeconds = usageMap[today] ?: 0
                    withContext(Dispatchers.Main) {
                        // 오늘 누적 사용시간 업데이트 (HH:MM:SS 형식)
                        binding.todayUsageTextView.text = "오늘 ${formatSecondsToHMS(todayUsageSeconds)} 사용했습니다."

                        // 목표: 1시간 30분 = 90분 (90*60초)
                        val targetSeconds = 90 * 60
                        // ProgressBar 진행률 (0~100)
                        val progress = ((todayUsageSeconds.toFloat() / targetSeconds) * 100).toInt().coerceAtMost(100)
                        binding.usageProgressBar.progress = progress

                        // 격려 메시지 결정 (예시)
                        val encouragement = when {
                            todayUsageSeconds < 30 * 60 -> "아직 30분 미만입니다.\n조금만 더 사용해 보세요!"
                            todayUsageSeconds < 60 * 60 -> "30분 달성!\n계속 진행하세요!"
                            todayUsageSeconds < 90 * 60 -> "1시간 달성!\n아주 좋아요!"
                            else -> "축하합니다!\n하루 권장 사용량을 달성했습니다!"
                        }
                        binding.encouragementTextView.gravity = Gravity.CENTER
                        binding.encouragementTextView.text = encouragement
                    }
                }
            }
        }
    }

    // SharedPreferences에서 현재 선택된 프로필의 ID를 반환
    private fun getCurrentProfileId(context: Context): String {
        val prefs = context.getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        return if (profileJson != null) {
            Gson().fromJson(profileJson, ProfileData::class.java).id
        } else {
            ""
        }
    }

    // 초 단위의 사용시간을 HH:MM:SS 형식으로 변환하는 함수
    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    /**
     * updateProfileInfo()
     * - SharedPreferences에 저장된 프로필 정보를 업데이트하고, 프로필 카드 영역의 텍스트를 변경합니다.
     */
    fun updateProfileInfo(name: String?, age: String?, gender: String?, height: String?, weight: String?) {
        binding.newProfileName.text = name ?: "USER"
        binding.itemTextProfileLastUsage.text = "나이: ${age ?: "-"}, 성별: ${gender ?: "-"}\n키: ${height ?: "-"}cm, 몸무게: ${weight ?: "-"}kg"

        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val gson = Gson()
        val existingJson = prefs.getString("selectedProfileJson", null)
        val existingProfile = if (existingJson != null) {
            gson.fromJson(existingJson, ProfileData::class.java)
        } else null

        val updatedProfile = if (existingProfile != null) {
            ProfileData(
                id = existingProfile.id,
                name = name ?: "USER",
                age = age ?: "-",
                gender = gender ?: "-",
                height = height ?: "-",
                weight = weight ?: "-",
                usedTimeSeconds = existingProfile.usedTimeSeconds,
                lastUsedTimestamp = existingProfile.lastUsedTimestamp
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

    // loadProfileInfo() - SharedPreferences에서 저장된 프로필 정보를 불러와 updateProfileInfo()를 호출
    private fun loadProfileInfo() {
        val prefs = requireContext().getSharedPreferences("selectedProfile", MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        if (profileJson != null) {
            val profile = Gson().fromJson(profileJson, ProfileData::class.java)
            updateProfileInfo(profile.name, profile.age, profile.gender, profile.height, profile.weight)
        } else {
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
        _binding = null
    }
}
