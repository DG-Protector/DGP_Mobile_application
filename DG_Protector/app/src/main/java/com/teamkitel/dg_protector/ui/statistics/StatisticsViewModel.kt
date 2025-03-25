package com.teamkitel.dg_protector.ui.statistics

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class StatisticsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "StatisticsViewModel"
    }
    val text: LiveData<String> = _text
}