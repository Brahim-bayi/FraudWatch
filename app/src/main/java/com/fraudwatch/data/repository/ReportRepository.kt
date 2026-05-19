package com.fraudwatch.data.repository

import android.util.Log
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.remote.RetrofitClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.*

/**
 * Repository central pour les rapports de fraude.
 *
 * Gère deux sources de données :
 *   - Firebase Firestore : stockage cloud (utilisateurs connectés)
 *   - ReportCache        : stockage local RAM + SharedPrefs (mode anonyme + fallback offline)
 *
 * Stratégie : Firestore en priorité, ReportCache en fallback si Firestore échoue ou si
 * l'utilisateur n'est pas connecté.
 */
class ReportRepository {

    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val imageApi  = RetrofitClient.backendImageService

    companion object {
        private const val TAG = "ReportRepository"
        const val COLLECTION = "reports" // Nom de la collection Firestore
    }

    /**
     * Sauvegarde un rapport de fraude après analyse IA.
     *
     * Flux :
     *   1. Génère un ID unique (UUID)
     *   2. Upload l'image sur Firebase Storage (si connecté)
     *   3. Construit l'objet Report
     *   4. Stocke dans ReportCache (toujours)
     *   5. Stocke dans Firestore (si connecté)
     *
     * @param imageBytes  Bytes JPEG de la photo capturée
     * @param riskLevel   Niveau de risque évalué par l'IA
     * @param fraudType   Type de fraude détecté
     * @param description Analyse textuelle de l'IA
     * @param latitude    Latitude GPS au moment de la capture
     * @param longitude   Longitude GPS au moment de la capture
     * @return Result<Report> — succès avec le rapport créé, ou échec avec l'exception
     */
    suspend fun saveReport(
        imageBytes: ByteArray,
        riskLevel: String,
        fraudType: String,
        description: String,
        latitude: Double,
        longitude: Double
    ): Result<Report> {
        return try {
            val userId = auth.currentUser?.uid ?: "anonymous"
            val isAnonymous = auth.currentUser == null
            val reportId = UUID.randomUUID().toString() // ID unique du rapport
            val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

            // Upload image seulement si l'utilisateur est connecté
            val imageUrl = if (isAnonymous) "" else uploadImage(imageBytes, reportId)

            // Construction du rapport
            val report = Report(
                id = reportId,
                imageUrl = imageUrl,
                riskLevel = riskLevel,
                fraudType = fraudType,
                description = description,
                latitude = latitude,
                longitude = longitude,
                date = date,
                userId = userId
            )

            // Toujours mettre en cache local (utilisé en mode anonyme et comme fallback)
            ReportCache.put(report, imageBytes)

            if (!isAnonymous) {
                // Utilisateur connecté : persister dans Firestore
                firestore.collection(COLLECTION).document(reportId).set(report).await()
                Log.d(TAG, "Rapport sauvegardé Firestore: $reportId")
            } else {
                // Mode anonyme : uniquement cache local
                Log.d(TAG, "Mode anonyme — rapport en cache local: $reportId")
            }

            Result.success(report)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur sauvegarde rapport", e)
            Result.failure(e)
        }
    }

    /**
     * Upload une image sur le backend personnel (FastAPI).
     * @return URL de téléchargement backend, ou "" en cas d'échec
     */
    private suspend fun uploadImage(bytes: ByteArray, reportId: String): String {
        return try {
            val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "$reportId.jpg", requestBody)
            val idPart = reportId.toRequestBody("text/plain".toMediaTypeOrNull())
            val response = imageApi.uploadImage(part, idPart)
            if (response.isSuccessful) {
                response.body()?.url ?: ""
            } else {
                Log.e(TAG, "Upload backend échoué : ${response.code()}")
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur upload image backend", e)
            ""
        }
    }

    /**
     * Récupère TOUS les rapports (tous utilisateurs) pour la carte.
     *
     * Flux :
     *   1. Firestore — collection complète triée par date DESC
     *   2. Merge avec le cache local (évite les doublons par ID)
     *   3. Si Firestore échoue → fallback cache local uniquement
     *
     * Utilisé par MapFragment pour afficher les marqueurs sur la carte.
     *
     * @return Result<List<Report>> triée du plus récent au plus ancien
     */
    suspend fun getAllReports(): Result<List<Report>> {
        return try {
            val snapshot = firestore.collection(COLLECTION)
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()
            val firestoreReports = snapshot.toObjects(Report::class.java)
            val cached = ReportCache.getAll()
            // Merge Firestore + cache, on garde un seul exemplaire par ID
            val merged = (firestoreReports + cached).distinctBy { it.id }
            Result.success(merged)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur chargement rapports, fallback cache", e)
            Result.success(ReportCache.getAll()) // Fallback : cache local
        }
    }

    /**
     * Récupère les rapports de l'utilisateur connecté pour l'historique.
     *
     * - Mode anonyme : retourne uniquement le cache local
     * - Mode connecté : Firestore filtré par userId + merge cache
     *
     * Utilisé par HistoryFragment pour afficher l'historique personnel.
     *
     * @return Result<List<Report>> de l'utilisateur courant
     */
    suspend fun getUserReports(): Result<List<Report>> {
        val isAnonymous = auth.currentUser == null
        if (isAnonymous) {
            // Pas de compte → uniquement les rapports en cache local
            return Result.success(ReportCache.getAll())
        }
        val userId = auth.currentUser!!.uid
        return try {
            val snapshot = firestore.collection(COLLECTION)
                .whereEqualTo("userId", userId)   // Filtrer par utilisateur
                .orderBy("date", Query.Direction.DESCENDING)
                .get().await()
            val firestoreReports = snapshot.toObjects(Report::class.java)
            val cached = ReportCache.getAll().filter { it.userId == userId }
            val merged = (firestoreReports + cached).distinctBy { it.id }
            Result.success(merged)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur rapports utilisateur, fallback cache", e)
            Result.success(ReportCache.getAll()) // Fallback : tout le cache
        }
    }

    /**
     * Récupère un rapport spécifique par son ID.
     *
     * Priorité : cache local d'abord (plus rapide), puis Firestore.
     * Le rapport récupéré depuis Firestore est mis en cache pour les accès suivants.
     *
     * @param reportId ID du rapport à charger
     * @return Result<Report> ou failure si introuvable
     */
    suspend fun getReportById(reportId: String): Result<Report> {
        // Vérifier le cache avant d'appeler Firestore
        ReportCache.get(reportId)?.let { return Result.success(it) }
        return try {
            val doc = firestore.collection(COLLECTION).document(reportId).get().await()
            val report = doc.toObject(Report::class.java)
                ?: return Result.failure(Exception("Rapport introuvable"))
            ReportCache.put(report) // Mise en cache pour les prochains accès
            Result.success(report)
        } catch (e: Exception) {
            Log.e(TAG, "Erreur getReportById", e)
            Result.failure(e)
        }
    }

    /**
     * Calcule les statistiques globales des rapports pour le tableau de bord.
     *
     * - Mode anonyme : stats du cache local uniquement
     * - Mode connecté : stats Firestore + cache mergés
     *
     * @return Map avec clés : "total", "critique", "eleve", "moyen", "faible"
     */
    suspend fun getStats(): Map<String, Int> {
        val cacheStats = ReportCache.getStats()
        if (auth.currentUser == null) return cacheStats // Mode anonyme
        return try {
            val snapshot = firestore.collection(COLLECTION).get().await()
            val reports = snapshot.toObjects(Report::class.java)
            val all = (reports + ReportCache.getAll()).distinctBy { it.id }
            mapOf(
                "total"    to all.size,
                "critique" to all.count { it.riskLevel.uppercase().trim() == "CRITIQUE" },
                "eleve"    to all.count { it.riskLevel.uppercase().trim() in listOf("ÉLEVÉ", "ELEVE") },
                "moyen"    to all.count { it.riskLevel.uppercase().trim() == "MOYEN" },
                "faible"   to all.count { it.riskLevel.uppercase().trim() == "FAIBLE" }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur stats", e)
            cacheStats // Fallback stats locales
        }
    }
}
