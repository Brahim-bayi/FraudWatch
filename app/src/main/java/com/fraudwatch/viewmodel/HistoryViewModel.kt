package com.fraudwatch.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.repository.ReportRepository
import kotlinx.coroutines.launch

/**
 * ViewModel de l'écran Historique (HistoryFragment).
 *
 * Responsabilités :
 *   - Charger les rapports de l'utilisateur connecté
 *   - Appliquer un filtre par niveau de risque (chips)
 *   - Appliquer une recherche textuelle (barre de recherche)
 *   - Combiner filtre + recherche simultanément
 *
 * Architecture :
 *   _allReports   = source brute depuis Firestore/cache
 *   _filteredReports = résultat après filtre + recherche → affiché dans la liste
 */
class HistoryViewModel : ViewModel() {

    private val reportRepository = ReportRepository()

    // Tous les rapports bruts chargés depuis le repository (non filtrés)
    private val _allReports = MutableLiveData<List<Report>>(emptyList())

    // Rapports filtrés exposés au Fragment pour l'affichage
    private val _filteredReports = MutableLiveData<List<Report>>(emptyList())
    val filteredReports: LiveData<List<Report>> = _filteredReports

    // État du chargement initial
    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    // Erreur à afficher si le chargement échoue
    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    // Filtre actif par niveau de risque ("ALL" = aucun filtre)
    private var activeFilter = "ALL"

    // Texte de recherche actuel (vide = pas de recherche)
    private var searchQuery = ""

    init {
        loadReports() // Charger les rapports au démarrage
    }

    /**
     * Charge les rapports de l'utilisateur connecté depuis Firestore + cache.
     * Appelé automatiquement à l'initialisation.
     * Déclenche applyFilter() après le chargement pour appliquer
     * les filtres/recherches déjà actifs.
     */
    fun loadReports() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val result = reportRepository.getUserReports()
                if (result.isSuccess) {
                    _allReports.value = result.getOrNull() ?: emptyList()
                    applyFilter() // Appliquer les filtres sur les nouvelles données
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Erreur de chargement"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur inconnue"
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Change le filtre actif par niveau de risque.
     * Appelé quand l'utilisateur clique sur un chip (Tous, Faible, Moyen, Élevé, Critique).
     *
     * @param level "ALL", "FAIBLE", "MOYEN", "ÉLEVÉ", ou "CRITIQUE"
     */
    fun filterByRiskLevel(level: String) {
        activeFilter = level
        applyFilter() // Recalculer la liste filtrée
    }

    /**
     * Met à jour la recherche textuelle.
     * Appelé à chaque caractère tapé dans la barre de recherche (doOnTextChanged).
     *
     * @param query Texte recherché (vide = pas de filtre texte)
     */
    fun search(query: String) {
        searchQuery = query
        applyFilter() // Recalculer avec le nouveau texte
    }

    /**
     * Applique simultanément le filtre de risque ET la recherche textuelle.
     *
     * Ordre des opérations :
     *   1. Filtrer par niveau de risque (si activeFilter != "ALL")
     *   2. Filtrer par texte sur : fraudType, description, riskLevel
     *
     * Le résultat est publié dans _filteredReports → observé par le Fragment.
     */
    private fun applyFilter() {
        val all = _allReports.value ?: emptyList()

        // Étape 1 : filtre par niveau de risque
        var result = if (activeFilter == "ALL") all
        else all.filter { it.riskLevel.uppercase() == activeFilter.uppercase() }

        // Étape 2 : filtre textuel (ignoreCase pour faciliter la recherche)
        if (searchQuery.isNotBlank()) {
            result = result.filter { report ->
                report.fraudType.contains(searchQuery, ignoreCase = true) ||
                report.description.contains(searchQuery, ignoreCase = true) ||
                report.riskLevel.contains(searchQuery, ignoreCase = true)
            }
        }

        _filteredReports.value = result // Notifie le Fragment via LiveData
    }
}
