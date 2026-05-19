package com.fraudwatch.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fraudwatch.data.model.AIResponse
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.repository.AIRepository
import com.fraudwatch.data.repository.ReportRepository
import com.fraudwatch.utils.LocationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ViewModel du fragment caméra.
 * Gère l'analyse IA de l'image capturée, la récupération de la localisation
 * et la sauvegarde du rapport dans Firebase.
 */
class CameraViewModel(application: Application) : AndroidViewModel(application) {

    private val aiRepository = AIRepository()
    private val reportRepository = ReportRepository()
    private val locationHelper = LocationHelper(application)

    // État de l'analyse exposé au fragment via LiveData
    private val _analysisState = MutableLiveData<AnalysisState>()
    val analysisState: LiveData<AnalysisState> = _analysisState

    private var analysisJob: Job? = null  // Coroutine principale d'analyse
    private var timerJob: Job? = null     // Coroutine du chronomètre affiché

    /**
     * Lance l'analyse complète : localisation → IA → sauvegarde Firebase.
     * @param imageBytes  Bytes bruts de l'image compressée (pour Firebase Storage)
     * @param base64Image Image encodée en base64 (pour Ollama)
     * @param voiceDescription Description vocale optionnelle de l'utilisateur
     */
    fun analyzeAndSave(imageBytes: ByteArray, base64Image: String, voiceDescription: String = "") {
        // Annule une analyse en cours si l'utilisateur relance
        analysisJob?.cancel()
        timerJob?.cancel()

        // Chronomètre mis à jour chaque seconde pendant l'analyse
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1000)
                seconds++
                _analysisState.postValue(AnalysisState.Tick(seconds))
            }
        }

        analysisJob = viewModelScope.launch {
            _analysisState.value = AnalysisState.Analyzing("Analyse de l'image...")
            try {
                // Étape 1 : Récupérer la position GPS
                val location = locationHelper.getCurrentLocation()

                // Étape 2 : Envoyer l'image à Ollama pour analyse IA
                _analysisState.value = AnalysisState.Analyzing("Intelligence artificielle en cours...")
                val aiResult = aiRepository.analyzeImage(base64Image, voiceDescription)

                if (aiResult.isFailure) {
                    timerJob?.cancel()
                    _analysisState.value = AnalysisState.Error(
                        aiResult.exceptionOrNull()?.message ?: "Erreur d'analyse IA"
                    )
                    return@launch
                }

                // Étape 3 : Sauvegarder le rapport dans Firebase
                val ai = aiResult.getOrNull()!!
                _analysisState.value = AnalysisState.Analyzing("Sauvegarde du rapport...")
                val saveResult = reportRepository.saveReport(
                    imageBytes = imageBytes,
                    riskLevel = ai.riskLevel,
                    fraudType = ai.fraudType,
                    description = ai.description,
                    latitude = location.latitude,
                    longitude = location.longitude
                )

                timerJob?.cancel()
                if (saveResult.isSuccess) {
                    _analysisState.value = AnalysisState.Success(ai, saveResult.getOrNull()!!)
                } else {
                    _analysisState.value = AnalysisState.Error(
                        saveResult.exceptionOrNull()?.message ?: "Erreur de sauvegarde"
                    )
                }
            } catch (e: Exception) {
                timerJob?.cancel()
                _analysisState.value = AnalysisState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    // Annule l'analyse en cours (bouton Annuler dans le fragment)
    fun cancelAnalysis() {
        analysisJob?.cancel()
        timerJob?.cancel()
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        timerJob?.cancel()
    }

    /**
     * États possibles de l'analyse IA.
     */
    sealed class AnalysisState {
        data class Analyzing(val message: String) : AnalysisState()  // En cours
        data class Tick(val seconds: Int) : AnalysisState()           // Tick chronomètre
        data class Success(val aiResponse: AIResponse, val report: Report) : AnalysisState() // Succès
        data class Error(val message: String) : AnalysisState()       // Erreur
    }
}
