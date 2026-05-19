package com.fraudwatch.data.model

import com.google.firebase.firestore.DocumentId
import java.io.Serializable

/**
 * Modèle de données d'un rapport de fraude.
 * Chaque rapport est créé après une analyse IA d'une image capturée.
 * Il est stocké dans Firestore (si connecté) et dans le cache local (ReportCache).
 *
 * @param id          Identifiant unique généré par UUID (aussi clé Firestore)
 * @param imageUrl    URL Firebase Storage de l'image uploadée (vide si mode anonyme)
 * @param riskLevel   Niveau de risque : "FAIBLE", "MOYEN", "ÉLEVÉ", "CRITIQUE"
 * @param fraudType   Type de fraude détecté par l'IA (ex: "Faux billet", "Phishing")
 * @param description Analyse textuelle complète retournée par le modèle moondream
 * @param latitude    Coordonnée GPS — latitude au moment de la capture
 * @param longitude   Coordonnée GPS — longitude au moment de la capture
 * @param date        Date/heure formatée "dd/MM/yyyy HH:mm"
 * @param userId      UID Firebase de l'utilisateur (ou "anonymous")
 */
data class Report(
    @DocumentId
    val id: String = "",           // Clé du document Firestore
    val imageUrl: String = "",     // URL image sur Firebase Storage
    val riskLevel: String = "",    // Niveau de risque évalué par l'IA
    val fraudType: String = "",    // Type de fraude identifié
    val description: String = "",  // Analyse détaillée de l'IA
    val latitude: Double = 0.0,   // Position GPS latitude
    val longitude: Double = 0.0,  // Position GPS longitude
    val date: String = "",         // Date formatée de la capture
    val userId: String = ""        // UID du créateur du rapport
) : Serializable
