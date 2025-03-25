package com.teamkitel.dg_protector.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.teamkitel.dg_protector.R
import com.teamkitel.dg_protector.databinding.LayoutCreateProfileBinding

// 프로필 생성 및 수정모드에서의 관련 액티비티 Class

class CreateProfileActivity : AppCompatActivity() {
    // ViewBinding 변수, layout에 연결하기 위해 사용
    private var _binding: LayoutCreateProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge to edge 모드 활성화
        enableEdgeToEdge()
        // layout을 inflate해서 Binding 초기화
        _binding = LayoutCreateProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 스피너 어댑터 초기화, 성별 목록 불러옴
        val genderArray = resources.getStringArray(R.array.gender_list)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, genderArray)
        binding.spinnerGender.adapter = spinnerAdapter

        // 수정 모드 여부 체크함, editPosition이 0 이상이면 수정 모드임
        val editPosition = intent.getIntExtra("editPosition", -1)
        if (editPosition >= 0) {
            // 기존 프로필 데이터 불러와서 입력 필드에 셋팅함
            binding.editTextName.setText(intent.getStringExtra("profileName"))
            binding.editTextNumberSigned.setText(intent.getStringExtra("profileAge"))
            binding.editTextHeight.setText(intent.getStringExtra("profileHeight"))
            binding.editTextWeight.setText(intent.getStringExtra("profileWeight"))
            val gender = intent.getStringExtra("profileGender")
            // 스피너에서 전달된 성별과 일치하는 항목 선택함
            for (i in 0 until spinnerAdapter.count) {
                if (spinnerAdapter.getItem(i) == gender) {
                    binding.spinnerGender.setSelection(i)
                    break
                }
            }
        }

        // 완료 버튼 클릭 리스너 설정
        binding.btnComplete.setOnClickListener {
            // 입력값들을 trim 처리해서 변수에 저장
            val name = binding.editTextName.text.toString().trim()
            val age = binding.editTextNumberSigned.text.toString().trim()
            val gender = binding.spinnerGender.selectedItem.toString()
            val height = binding.editTextHeight.text.toString().trim()
            val weight = binding.editTextWeight.text.toString().trim()
            // 결과 인텐트에 입력값들 담음
            val resultIntent = Intent().apply {
                putExtra("profileName", name)
                putExtra("profileAge", age)
                putExtra("profileGender", gender)
                putExtra("profileHeight", height)
                putExtra("profileWeight", weight)
                if (editPosition >= 0) {
                    putExtra("editPosition", editPosition)
                }
            }
            // 결과 전달하고 액티비티 종료함
            setResult(RESULT_OK, resultIntent)
            finish()
        }
        // 뒤로 가기 버튼 클릭 시 액티비티 종료함
        binding.btnBack.setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // ViewBinding 해제
        _binding = null
    }
}
