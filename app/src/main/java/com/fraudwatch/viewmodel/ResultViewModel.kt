package com.fraudwatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.repository.ReportRepository
import kotlinx.coroutines.launch

class ResultViewModel : ViewModel() {

    private val reportRepository = ReportRepository()

    private val _report = MutableLiveData<Report>()
    val report: LiveData<Report> = _report

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun loadReport(reportId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = reportRepository.getReportById(reportId)
                if (result.isSuccess) {
                    _report.value = result.getOrNull()
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Rapport introuvable"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur inconnue"
            } finally {
                _loading.value = false
            }
        }
    }
}
