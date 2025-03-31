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
                    // weeklyUsage: Map<String, Int> (key: "yyyy-MM-dd", value: 사용한 시간)
                    // 기존처럼 텍스트뷰에 주간 사용 데이터를 출력
                    val usageText = weeklyUsage.entries.joinToString("\n") { entry ->
                        val seconds = entry.value ?: 0
                        val formattedTime = formatSecondsToTime(seconds)
                        "${entry.key}: $formattedTime"
                    }
                    // x축는 날짜의 '일(day)'만 사용
                    val entries = mutableListOf<Entry>()
                    val sortedDates = weeklyUsage.keys.sorted() // yyyy-MM-dd 형식이면 올바른 순서
                    for (dateStr in sortedDates) {
                        // 날짜 문자열에서 일(day)만 추출하여 float으로 변환
                        val day = dateStr.substring(8, 10).toFloat()
                        val usageSeconds = weeklyUsage[dateStr]?.toFloat() ?: 0f
                        entries.add(Entry(day, usageSeconds))
                    }
                    // 기존 데이터셋 생성 후에 커스텀 포맷터 적용
                    val dataSet = LineDataSet(entries, "Using Time").apply {
                        axisDependency = YAxis.AxisDependency.LEFT
                        color = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
                        valueTextColor = ContextCompat.getColor(requireContext(), R.color.kitel_navy_700)
                        // 데이터 라벨에 시간 포맷 적용
                        valueFormatter = TimeValueFormatter()
                    }
                    val lineData = LineData(dataSet)

                    withContext(Dispatchers.Main) {
                        binding.weeklyUsageTextView.text = usageText
                        binding.lineChart.data = lineData

                        binding.lineChart.xAxis.valueFormatter = DayValueFormatter()
                        binding.lineChart.xAxis.granularity = 1f

                        binding.lineChart.description.isEnabled = false

                        // y축 범위를 0 ~ 6시간(21600초)으로 설정
                        binding.lineChart.axisLeft.axisMinimum = 0f
                        binding.lineChart.axisLeft.axisMaximum = 6 * 60 * 60f  // 21600초
                        // 1시간(3600초) 단위로 레이블이 표시되도록 설정
                        binding.lineChart.axisLeft.granularity = 3600f
                        binding.lineChart.axisLeft.setLabelCount(7, true)
                        binding.lineChart.axisLeft.valueFormatter = HourAxisValueFormatter()
                        binding.lineChart.axisRight.isEnabled = false

                        binding.lineChart.invalidate()
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

// 데이터셋에 사용할 시간 포맷터
class TimeValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        val totalSeconds = value.toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val sb = StringBuilder()
        if (hours > 0) {
            sb.append("${hours}h")
        }
        if (hours > 0 || minutes > 0) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append("${minutes}m")
        }
        if (seconds > 0) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append("${seconds}s")
        }
        return sb.toString()
    }
}

// y축에 사용할 포맷터 (0은 빈 문자열 처리하여 1h부터 표시)
class HourAxisValueFormatter : ValueFormatter() {
    override fun getFormattedValue(value: Float): String {
        // 3600초 미만은 빈 문자열로 처리 (즉, 0h는 표시하지 않음)
        if (value < 3600f) return ""
        val hours = (value / 3600).toInt()
        return "${hours}h"
    }
}

private fun formatSecondsToTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02d:%02d:%02d", hours, minutes, secs)
}
