package com.teamkitel.dg_protector.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teamkitel.dg_protector.R

// recyclerview 어댑터.
// 프로필 목록 표시하는 Class.
class ProfilesAdapter(
    private val profiles: MutableList<ProfileData>, // 프로필 데이터 리스트
    private val listener: OnProfileItemClickListener // profile click 리스너
) : RecyclerView.Adapter<ProfilesAdapter.ProfileViewHolder>() {

    // 프로필 아이템 클릭 인터페이스
    interface OnProfileItemClickListener {
        fun onProfileItemClick(position: Int, profile: ProfileData) // 아이템 클릭 시 호출됨.
    }

    // ViewHolder 클래스
    // 아이템 레이아웃의 view들을 참조
    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.new_profile_name) // 프로필 이름 표시 텍스트뷰임.
        val usageText: TextView = itemView.findViewById(R.id.item_text_profile_usage) // 사용 시간 표시 텍스트뷰임.
        val lastUsageText: TextView = itemView.findViewById(R.id.item_text_profile_last_usage) // 마지막 사용 시간 표시 텍스트뷰임.

        init {
            // 아이템 클릭 시 리스너에 전달
            itemView.setOnClickListener {
                listener.onProfileItemClick(adapterPosition, profiles[adapterPosition])
            }
        }
    }

    // 새로운 ViewHolder 생성
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_item_profile, parent, false)
        return ProfileViewHolder(view)
    }

    // ViewHolder에 데이터를 Binding함
    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.nameText.text = profile.name // 프로필 이름 setting
        holder.usageText.text = "사용한 시간: " + formatSecondsToHMS(profile.usedTimeSeconds) // 사용 시간 포맷팅해서 셋팅함.
        val hoursAgo = calculateHoursAgo(profile.lastUsedTimestamp)
        holder.lastUsageText.text = "마지막 사용: $hoursAgo hour ago" // 마지막 사용 시간 표시함.
    }

    // 초를 00:00:00(=시:분:초) 형식으로 변환
    private fun formatSecondsToHMS(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    // 마지막 사용 시각과 현재 시간의 차이를 시간 단위로 계산험
    private fun calculateHoursAgo(lastUsedTimestamp: Long): Int {
        if (lastUsedTimestamp == 0L) return 0 // 기록 없으면 0 반환
        val diffMillis = System.currentTimeMillis() - lastUsedTimestamp
        return (diffMillis / (1000 * 3600)).toInt()
    }

    // 아이템 개수 반환
    override fun getItemCount(): Int = profiles.size
}
