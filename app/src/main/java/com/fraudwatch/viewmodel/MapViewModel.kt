package com.fraudwatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.repository.ReportRepository
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran Carte (MapFragment).
 *
 * Responsabilités :
 *   - Charger tous les rapports de fraude depuis le repository
 *   - Exposer la liste via LiveData pour que MapFragment place les marqueurs
 *   - Gérer les états de chargement et d'erreur
 *
 * Survit aux rotations d'écran grâce à l'architecture ViewModel.
 * Les rapports sont chargés automatiquement au premier accès (bloc init).
 */
class MapViewModel : ViewModel() {

    // Source unique de vérité pour les données
    private val reportRepository = ReportRepository()

    // Liste de tous les rapports à afficher sur la carte
    private val _reports = MutableLiveData<List<Report>>()
    val reports: LiveData<List<Report>> = _reports // Exposé en lecture seule au Fragment

    // État du chargement (true = spinner visible)
    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    // Message d'erreur à afficher en cas d'échec
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    init {
        // Charger les rapports dès la création du ViewModel
        loadReports()
    }

    /**
     * Charge tous les rapports de fraude depuis Firestore + cache local.
     * Appelé automatiquement à l'initialisation et sur le bouton "Rafraîchir".
     *
     * Utilise viewModelScope pour annuler automatiquement si le ViewModel
     * est détruit (évite les fuites mémoire).
     */
    fun loadReports() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = reportRepository.getAllReports() // Firestore + cache
                if (result.isSuccess) {
                    _reports.value = result.getOrNull() ?: emptyList()
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Erreur de chargement"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur inconnue"
            } finally {
                _loading.value = false // Toujours cacher le spinner
            }
        }
    }
}
