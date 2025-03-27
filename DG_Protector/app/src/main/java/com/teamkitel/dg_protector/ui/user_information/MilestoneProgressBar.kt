package com.teamkitel.dg_protector.ui.user_information

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * MilestoneProgressBar
 *
 * 목표 시간을 기준으로 진행률을 표시하고,
 * 마일스톤(30분, 1시간, 1시간 30분)에 해당하는 지점을 원과 텍스트로 표시하는 커스텀 뷰입니다.
 *
 * 기본 목표 시간은 1시간 30분(90분)이며,
 * 각 마일스톤은 30분, 60분, 90분을 의미합니다.
 *
 * [progress] 값 (0~100)을 설정하면 진행률이 업데이트됩니다.
 */
class MilestoneProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 좌우 여백 (마커와 텍스트가 잘리지 않도록 하기 위함)
    private val sideMargin = 20f

    // 배경 바 그리기용 Paint
    private val backgroundPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 진행률 영역 그리기용 Paint
    private val progressPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // 마일스톤(원 및 텍스트) 그리기용 Paint
    private val milestonePaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 25f
        isAntiAlias = true
    }

    // 진행률 0 ~ 100
    var progress: Int = 0
        set(value) {
            field = value
            invalidate() // 진행률 변경 시 뷰를 다시 그림
        }


    // 목표 시간 (초) : 기본 90분 (1시간 30분)
    var targetSeconds: Int = 90 * 60

    // 각 마일스톤은 30분, 60분, 90분을 의미.
    private val milestone1: Float get() = (30 * 60).toFloat() / targetSeconds * 100f
    private val milestone2: Float get() = (60 * 60).toFloat() / targetSeconds * 100f
    private val milestone3: Float get() = 100f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 전체 가용 너비 = width - 좌우 여백
        val effectiveWidth = width - 2 * sideMargin

        // 배경 바 높이와 위치 (전체 높이의 1/4 정도)
        val barHeight = height / 4f
        val barTop = (height - barHeight) / 2f

        // 배경 바 그리기 (좌측 여백부터 우측 여백 전까지)
        canvas.drawRect(sideMargin, barTop, width - sideMargin, barTop + barHeight, backgroundPaint)

        // 진행 영역 그리기 (progress 값에 따라)
        val progressWidth = effectiveWidth * progress / 100f
        canvas.drawRect(sideMargin, barTop, sideMargin + progressWidth, barTop + barHeight, progressPaint)

        // 마일스톤 원들의 x좌표 계산 (좌측 여백을 기준으로 effectiveWidth 비율)
        val m1x = sideMargin + effectiveWidth * (milestone1 / 100f)
        val m2x = sideMargin + effectiveWidth * (milestone2 / 100f)
        val m3x = sideMargin + effectiveWidth // 100% 지점

        // 마커 원 그리기
        val markerRadius = 10f
        val markerCenterY = barTop + barHeight / 2f
        canvas.drawCircle(m1x, markerCenterY, markerRadius, milestonePaint)
        canvas.drawCircle(m2x, markerCenterY, markerRadius, milestonePaint)
        canvas.drawCircle(m3x, markerCenterY, markerRadius, milestonePaint)

        // 마일스톤 라벨: 원 밑에 작게 표시 (아래쪽 여백 20f)
        val labelY = markerCenterY + markerRadius + 20f

        // 첫번째, 두번째 라벨은 중앙 정렬
        milestonePaint.textAlign = Paint.Align.CENTER
        canvas.drawText("30m", m1x, labelY, milestonePaint)
        canvas.drawText("1h", m2x, labelY, milestonePaint)

        // 마지막 라벨은 오른쪽 정렬해서, 오른쪽 여백 내에서 표시되도록 함
        milestonePaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("1h 30m", width - sideMargin, labelY, milestonePaint)

        // 다른 그리기 작업 후 텍스트 정렬을 복구
        milestonePaint.textAlign = Paint.Align.CENTER
    }
}
