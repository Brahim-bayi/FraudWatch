package com.fraudwatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fraudwatch.data.repository.ReportRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val reportRepository = ReportRepository()

    private val _stats = MutableLiveData<Map<String, Int>>()
    val stats: LiveData<Map<String, Int>> = _stats

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch {
            _loading.value = true
            try {
                _stats.value = reportRepository.getStats()
            } catch (e: Exception) {
                _stats.value = emptyMap()
            } finally {
                _loading.value = false
            }
        }
    }
}
