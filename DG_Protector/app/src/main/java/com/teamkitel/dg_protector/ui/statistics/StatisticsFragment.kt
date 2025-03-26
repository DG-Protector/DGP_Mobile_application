package com.teamkitel.dg_protector.ui.statistics

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.teamkitel.dg_protector.databinding.LayoutStatisticsBinding
import com.teamkitel.dg_protector.datastore.getWeeklyUsageFlow
import com.teamkitel.dg_protector.ui.profile.ProfileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import com.teamkitel.dg_protector.R

class StatisticsFragment : Fragment(R.layout.layout_statistics) {

    private var _binding: LayoutStatisticsBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = LayoutStatisticsBinding.bind(view)

        // 오늘 날짜와 6일 전(즉, 7일치) 계산
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val today = Date()
        val endDate = dateFormat.format(today)
        val calendar = Calendar.getInstance().apply { time = today }
        calendar.add(Calendar.DATE, -6)
        val startDate = dateFormat.format(calendar.time)

        // SharedPreferences에서 현재 선택된 프로필 ID를 불러오기
        val currentProfileId = getCurrentProfileId(requireContext())

        // 프로필 ID가 비어있지 않다면, DataStore Flow를 통해 실시간 업데이트
        if (currentProfileId.isNotEmpty()) {
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                getWeeklyUsageFlow(requireContext(), currentProfileId, startDate, endDate).collect { weeklyUsage ->
                    withContext(Dispatchers.Main) {
                        binding.weeklyUsageTextView.text = weeklyUsage.entries.joinToString("\n") {
                            "${it.key}: ${it.value}초"
                        }
                    }
                }
            }
        }
    }

    private fun getCurrentProfileId(context: Context): String {
        val prefs = context.getSharedPreferences("selectedProfile", Context.MODE_PRIVATE)
        val profileJson = prefs.getString("selectedProfileJson", null)
        return if (profileJson != null) {
            Gson().fromJson(profileJson, ProfileData::class.java).id
        } else {
            ""
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
