package com.teamkitel.dg_protector.ui.statistics

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.gson.Gson
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutStatisticsBinding
import com.teamkitel.dg_protector.datastore.getWeeklyUsageFlow
import com.teamkitel.dg_protector.ui.profile.ProfileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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
                    // weeklyUsage: Map<String, Int> (key: "yyyy-MM-dd", value: 사용한 초)
                    // 기존처럼 텍스트뷰에 주간 사용 데이터를 출력
                    val usageText = weeklyUsage.entries.joinToString("\n") {
                        "${it.key}: ${it.value}초"
                    }
                    // x축는 날짜의 '일(day)'만 사용 (예: "2025-03-27" → 27)
                    val entries = mutableListOf<Entry>()
                    val sortedDates = weeklyUsage.keys.sorted() // yyyy-MM-dd 형식이면 올바른 순서
                    for (dateStr in sortedDates) {
                        // 날짜 문자열에서 일(day)만 추출하여 float으로 변환
                        val day = dateStr.substring(8, 10).toFloat()
                        val usageSeconds = weeklyUsage[dateStr]?.toFloat() ?: 0f
                        entries.add(Entry(day, usageSeconds))
                    }
                    val dataSet = LineDataSet(entries, "Using Time").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
                        valueTextColor = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
                        // 추가 설정 (예: 선 굵기, 원형 점 등) 가능
                    }
                    val lineData = LineData(dataSet)

                    withContext(Dispatchers.Main) {
                        // 텍스트뷰에 주간 사용 데이터를 표시
                        binding.weeklyUsageTextView.text = usageText

                        // 선그래프에 데이터 적용
                        binding.lineChart.data = lineData

                        // x축 설정: ValueFormatter를 사용해 소수점 없이 정수 문자열로 보이게 함
                        binding.lineChart.xAxis.valueFormatter = DayValueFormatter()
                        binding.lineChart.xAxis.granularity = 1f

                        // 차트 오른쪽 아래 Description Label 제거
                        binding.lineChart.description.isEnabled = false

                        // y축 설정: 테스트용으로 0 ~ 20분(1200초) 범위로 제한
                        binding.lineChart.axisLeft.axisMinimum = 0f
                        binding.lineChart.axisLeft.axisMaximum = 20 * 60f
                        binding.lineChart.axisRight.isEnabled = false

                        binding.lineChart.invalidate() // 차트 갱신
                    }
                }
            }
        }
    }

    // 현재 선택된 프로필 ID를 SharedPreferences에서 가져오는 함수
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

    // x축 값을 정수로 포맷하는 ValueFormatter 클래스
    class DayValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return value.toInt().toString()
        }
    }
}
