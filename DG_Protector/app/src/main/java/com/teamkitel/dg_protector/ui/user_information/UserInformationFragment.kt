package com.teamkitel.dg_protector.ui.user_information

import android.app.Activity
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutUserInformationBinding
import com.teamkitel.dg_protector.datastore.getWeeklyUsageFlow
import com.teamkitel.dg_protector.ui.profile.ProfileData
import com.teamkitel.dg_protector.ui.profile.ProfileManager
import com.teamkitel.dg_protector.ui.profile.ProfilesActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import android.widget.TextView
import android.widget.ImageView
import android.widget.Button


class UserInformationFragment : Fragment() {

    private var _binding: LayoutUserInformationBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: UserInformationViewModel

    private val profilesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("selectedProfileName")
            val age = data?.getStringExtra("selectedProfileAge")
            val gender = data?.getStringExtra("selectedProfileGender")
            val height = data?.getStringExtra("selectedProfileHeight")
            val weight = data?.getStringExtra("selectedProfileWeight")
            updateProfileInfo(name, age, gender, height, weight)
            updateTodayUsage()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        viewModel = ViewModelProvider(this).get(UserInformationViewModel::class.java)
        _binding = LayoutUserInformationBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.btnProfileSetting2.setOnClickListener {
            val intent = Intent(requireContext(), ProfilesActivity::class.java)
            profilesLauncher.launch(intent)
        }

        binding.stretchButton1.setOnClickListener {
            showStretchDialog(
                "① 턱 당기기 스트레칭",
                R.drawable.chin_tuck2,
                "1. 등을 곧게 펴고 앉습니다.\n2. 목 뒤쪽이 길어지는 느낌이 들도록 턱을 살짝 당겨줍니다.\n3. 5초간 유지 후 이완합니다.\n총 10회 반복합니다."
            )
        }

        binding.stretchButton2.setOnClickListener {
            showStretchDialog(
                "② 견갑골(날개뼈) 모으기",
                R.drawable.scapular_squeezes2,
                "1. 의자에 앉아 허리를 곧게 펍니다.\n2. 양팔을 옆으로 벌리고 팔꿈치를 구부립니다.\n3. 날개뼈를 서로 모은다는 느낌으로 당깁니다.\n4. 5초 유지 후 이완.\n총 10회 반복합니다."
            )
        }

        binding.stretchButton3.setOnClickListener {
            showStretchDialog(
                "③ 가슴 근육 스트레칭",
                R.drawable.chest_muscle_stretching2,
                "1. 문틀 앞에 서서 양손을 어깨 높이로 올려 문틀에 댑니다.\n2. 한 발을 앞으로 내딛고 몸을 천천히 앞으로 기울이며 가슴을 내밀어줍니다.\n3. 가슴이 펴지는 느낌을 느끼며 20~30초간 유지합니다.\n4. 반대쪽도 반복합니다."
            )
        }

        binding.stretchButton4.setOnClickListener {
            showStretchDialog(
                "④ 상부 승모근 스트레칭",
                R.drawable.upper_trapezius2,
                "1. 한쪽 손으로 머리를 옆으로 당깁니다.\n2. 반대쪽 어깨는 내린 상태로 15초간 유지합니다.\n3. 반대쪽도 반복합니다."
            )
        }


        loadProfileInfo()
        updateTodayUsage()

        return root
    }

    private fun updateTodayUsage() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = dateFormat.format(Date())
        val currentProfileId = ProfileManager.currentProfile?.id ?: ""
        if (currentProfileId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                getWeeklyUsageFlow(requireContext(), currentProfileId, today, today).collect { usageMap ->
                    val todayUsageSeconds = usageMap[today] ?: 0
                    withContext(Dispatchers.Main) {
                        binding.todayUsageTextView.text = "오늘 ${formatSecondsToHMS(todayUsageSeconds)} 사용했습니다."
                        val targetSeconds = 90 * 60
                        val progress = ((todayUsageSeconds.toFloat() / targetSeconds) * 100).toInt().coerceAtMost(100)
                        binding.usageProgressBar.progress = progress
                        val encouragement = when {
                            todayUsageSeconds < 30 * 60 -> "아직 30분 미만입니다.\n조금만 더 사용해 보세요!"
                            todayUsageSeconds < 60 * 60 -> "30분 달성!\n계속 진행하세요!"
                            todayUsageSeconds < 90 * 60 -> "1시간 달성!\n아주 좋아요!"
                            else -> "축하합니다!\n오늘의 목표를 달성했습니다!"
                        }
                        binding.encouragementTextView.gravity = Gravity.CENTER
                        binding.encouragementTextView.text = encouragement
                    }
                }
            }
        }
    }

    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    fun updateProfileInfo(name: String?, age: String?, gender: String?, height: String?, weight: String?) {
        binding.newProfileName.text = name ?: "USER"
        binding.itemTextProfileLastUsage.text = "나이: ${age ?: "-"}, 성별: ${gender ?: "-"}\n키: ${height ?: "-"}cm, 몸무게: ${weight ?: "-"}kg"
        // 업데이트된 값으로 ProfileManager를 업데이트
        val current = ProfileManager.currentProfile ?: ProfileData(
            name = "USER",
            age = "0",
            gender = "x",
            height = "0",
            weight = "0"
        )
        val updatedProfile = current.copy(
            name = name ?: current.name,
            age = age ?: current.age,
            gender = gender ?: current.gender,
            height = height ?: current.height,
            weight = weight ?: current.weight
        )
        ProfileManager.updateCurrentProfile(updatedProfile, requireContext())
    }

    private fun loadProfileInfo() {
        ProfileManager.loadCurrentProfile(requireContext())
        val profile = ProfileManager.currentProfile ?: ProfileData(
            name = "USER",
            age = "0",
            gender = "x",
            height = "0",
            weight = "0"
        )
        updateProfileInfo(profile.name, profile.age, profile.gender, profile.height, profile.weight)
    }

    private fun showStretchDialog(title: String, imageResId: Int, description: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_stretch_explain, null)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.stretch_title).text = title
        dialogView.findViewById<ImageView>(R.id.stretch_image).setImageResource(imageResId)
        dialogView.findViewById<TextView>(R.id.stretch_description).text = description

        dialogView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
