package com.fraudwatch.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.fraudwatch.data.model.Report
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Cache local des rapports de fraude.
 *
 * Rôle : stocker les rapports en mémoire RAM + SharedPreferences pour deux cas :
 *   1. Mode anonyme (pas de Firebase) — seul stockage disponible
 *   2. Mode connecté — évite des appels Firestore répétés (perf + offline)
 *
 * Les images capturées sont gardées en RAM uniquement (trop lourdes pour SharedPrefs).
 * Les rapports (texte) sont persistés en JSON dans SharedPreferences.
 */
object ReportCache {

    // Map en mémoire : id du rapport → objet Report
    private val reports = mutableMapOf<String, Report>()

    // Map en mémoire : id du rapport → bytes de l'image capturée
    private val images = mutableMapOf<String, ByteArray>()

    // Accès aux SharedPreferences (initialisé via init())
    private var prefs: SharedPreferences? = null

    // Sérialiseur JSON pour persister la liste des rapports
    private val gson = Gson()

    /**
     * Initialise le cache avec le contexte Android.
     * Doit être appelé au démarrage de l'app (dans FraudWatchApplication).
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences("fraudwatch_cache", Context.MODE_PRIVATE)
        loadFromPrefs() // Recharge les rapports sauvegardés précédemment
    }

    /**
     * Charge les rapports depuis SharedPreferences en RAM au démarrage.
     * Ignore silencieusement les données corrompues.
     */
    private fun loadFromPrefs() {
        val json = prefs?.getString("reports", null) ?: return
        try {
            val type = object : TypeToken<List<Report>>() {}.type
            val list: List<Report> = gson.fromJson(json, type)
            list.forEach { reports[it.id] = it }
        } catch (e: Exception) { /* données corrompues — on repart vide */ }
    }

    /**
     * Persiste la map actuelle en JSON dans SharedPreferences.
     * Appelé après chaque modification (put).
     */
    private fun saveToPrefs() {
        val json = gson.toJson(reports.values.toList())
        prefs?.edit()?.putString("reports", json)?.apply()
    }

    /**
     * Ajoute ou met à jour un rapport dans le cache.
     * Si des bytes d'image sont fournis, ils sont gardés en RAM.
     *
     * @param report     Le rapport à stocker
     * @param imageBytes Bytes de l'image capturée (optionnel)
     */
    fun put(report: Report, imageBytes: ByteArray? = null) {
        reports[report.id] = report
        if (imageBytes != null) images[report.id] = imageBytes
        saveToPrefs() // Persiste immédiatement
    }

    /** Retourne un rapport par son ID, ou null s'il n'est pas en cache. */
    fun get(reportId: String): Report? = reports[reportId]

    /** Retourne les bytes de l'image d'un rapport (RAM uniquement). */
    fun getImage(reportId: String): ByteArray? = images[reportId]

    /** Retourne tous les rapports du cache, triés du plus récent au plus ancien. */
    fun getAll(): List<Report> = reports.values.toList().sortedByDescending { it.date }

    /**
     * Calcule les statistiques de risque à partir du cache local.
     * Utilisé en mode anonyme ou en fallback si Firestore est inaccessible.
     *
     * @return Map avec les clés : "total", "critique", "eleve", "moyen", "faible"
     */
    fun getStats(): Map<String, Int> {
        val all = reports.values.toList()
        return mapOf(
            "total"    to all.size,
            "critique" to all.count { it.riskLevel.uppercase().trim() == "CRITIQUE" },
            "eleve"    to all.count { it.riskLevel.uppercase().trim() in listOf("ÉLEVÉ", "ELEVE") },
            "moyen"    to all.count { it.riskLevel.uppercase().trim() == "MOYEN" },
            "faible"   to all.count { it.riskLevel.uppercase().trim() == "FAIBLE" }
        )
    }
}
