package com.teamkitel.dg_protector.ui.statistics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.teamkitel.dg_protector.databinding.LayoutStatisticsBinding
import java.text.SimpleDateFormat
import java.util.*

class StatisticsFragment : Fragment() {

    private var _binding: LayoutStatisticsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val statisticsViewModel =
            ViewModelProvider(this).get(StatisticsViewModel::class.java)
        _binding = LayoutStatisticsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // CalendarView에 오늘 날짜가 선택되어 있으므로 이를 기준으로 차트 업데이트
        updateChartWithSelectedDate(binding.calendarView.date)

        // CalendarView의 날짜 변경 리스너 설정
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val calendar = Calendar.getInstance()
            calendar.set(year, month, dayOfMonth)
            updateChartWithSelectedDate(calendar.time.time)
        }

        return root
    }

    // 선택된 날짜를 기준으로 전 주(월~일)의 라벨과 데이터를 계산하여 차트를 업데이트
    private fun updateChartWithSelectedDate(selectedDateMillis: Long) {
        val selectedDate = Date(selectedDateMillis)
        val xLabels = getPreviousWeekLabels(selectedDate)
        val entries = loadWeeklyUsageData(xLabels.size, selectedDate)
        setupLineChart(binding.lineChart, xLabels, entries)
    }

    // 선택된 날짜 기준 전 주의 날짜(월요일~일요일)의 일(day-of-month)을 구함
    private fun getPreviousWeekLabels(selectedDate: Date): List<String> {
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        // 설정: 선택된 날짜의 주의 월요일이 아닌, 바로 전 주의 월요일
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.DAY_OF_MONTH, -7)
        val labels = mutableListOf<String>()
        val dateFormat = SimpleDateFormat("d", Locale.getDefault())
        for (i in 0 until 7) {
            labels.add(dateFormat.format(calendar.time))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return labels
    }

    // 선택된 날짜 기준 전 주의 각 날짜의 사용시간(초)을 SharedPreferences에서 불러와 Entry 목록으로 만듦
    private fun loadWeeklyUsageData(numDays: Int, selectedDate: Date): List<Entry> {
        val prefs = requireContext().getSharedPreferences("dailyUsage", android.content.Context.MODE_PRIVATE)
        val calendar = Calendar.getInstance()
        calendar.time = selectedDate
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        calendar.add(Calendar.DAY_OF_MONTH, -7) // 전 주 월요일로 이동
        val entries = mutableListOf<Entry>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (i in 0 until numDays) {
            val dateKey = dateFormat.format(calendar.time)
            val usage = prefs.getInt(dateKey, 0)
            entries.add(Entry(i.toFloat(), usage.toFloat()))
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return entries
    }

    private fun setupLineChart(lineChart: LineChart, xLabels: List<String>, entries: List<Entry>) {
        val dataSet = LineDataSet(entries, "Weekly Usage").apply {
            lineWidth = 2f
            color = Color.BLUE
            setCircleColor(Color.BLUE)
            circleRadius = 4f
            valueTextSize = 10f
            setDrawValues(false)
        }
        val lineData = LineData(dataSet)
        lineChart.data = lineData

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.valueFormatter = IndexAxisValueFormatter(xLabels)

        lineChart.axisLeft.axisMinimum = 0f
        lineChart.axisLeft.axisMaximum = 24f  // 필요에 따라 조정하세요.
        lineChart.axisRight.isEnabled = false

        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = true

        // 차트의 여백을 조금 늘려서 데이터가 잘 보이도록 함
        lineChart.setExtraOffsets(10f, 10f, 10f, 10f)
        lineChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
