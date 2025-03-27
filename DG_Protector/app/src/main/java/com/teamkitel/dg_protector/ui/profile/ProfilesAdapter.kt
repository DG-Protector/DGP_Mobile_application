package com.teamkitel.dg_protector.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.teamkitel.dg_protector.R

// RecyclerView 어댑터.
// 프로필 목록을 표시하며, 각 아이템은 프로필의 이름과 세부 정보(나이, 성별, 키, 몸무게)를 보여줍니다.
class ProfilesAdapter(
    private val profiles: MutableList<ProfileData>, // 프로필 데이터 리스트
    private val listener: OnProfileItemClickListener // 프로필 클릭 리스너
) : RecyclerView.Adapter<ProfilesAdapter.ProfileViewHolder>() {

    // 프로필 아이템 클릭 인터페이스
    interface OnProfileItemClickListener {
        fun onProfileItemClick(position: Int, profile: ProfileData)
    }

    // ViewHolder 클래스
    inner class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.new_profile_name) // 프로필 이름
        val detailsText: TextView = itemView.findViewById(R.id.profile_details_text) // 나이, 성별, 키, 몸무게 표시

        init {
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

    // ViewHolder에 데이터를 바인딩
    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        val profile = profiles[position]
        holder.nameText.text = profile.name
        // 나이, 성별, 키, 몸무게를 표시
        holder.detailsText.text = "나이: ${profile.age}, 성별: ${profile.gender}\n키: ${profile.height}cm, 몸무게: ${profile.weight}kg"
    }

    // 아이템 개수 반환
    override fun getItemCount(): Int = profiles.size
}
