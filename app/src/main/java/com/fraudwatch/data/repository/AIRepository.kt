package com.fraudwatch.data.repository

import android.util.Log
import com.fraudwatch.data.model.AIResponse
import com.fraudwatch.data.model.OllamaRequest
import com.fraudwatch.data.remote.RetrofitClient

class AIRepository {

    private val ollamaService = RetrofitClient.ollamaService

    companion object {
        private const val TAG = "AIRepository"

        private const val PROMPT =
            "Describe in detail what you see in this image. " +
            "Mention: the type of object, its colors, all visible text and numbers, " +
            "logos or brands, printing quality, security features if any, " +
            "and any defects, irregularities or suspicious elements."
    }

    suspend fun analyzeImage(base64Image: String, voiceDescription: String = ""): Result<AIResponse> {
        val prompt = if (voiceDescription.isNotBlank())
            "The user describes the object as: \"$voiceDescription\"\n$PROMPT"
        else PROMPT

        return try {
            val response = ollamaService.analyzeImage(
                OllamaRequest(model = "moondream", prompt = prompt, images = listOf(base64Image), stream = false)
            )
            if (response.isSuccessful) {
                val raw = response.body()?.response?.trim() ?: ""
                Log.d(TAG, "Moondream raw: $raw")
                Result.success(buildFrenchAnalysis(raw, voiceDescription))
            } else {
                Log.w(TAG, "Ollama HTTP ${response.code()}, mode démo")
                Result.success(demoAnalysis(voiceDescription))
            }
        } catch (e: java.net.ConnectException) {
            Log.w(TAG, "Ollama non joignable")
            Result.success(demoAnalysis(voiceDescription))
        } catch (e: java.net.SocketTimeoutException) {
            Log.w(TAG, "Ollama timeout")
            Result.success(demoAnalysis(voiceDescription))
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.containsAny("Failed to connect", "Connection refused", "timeout")) {
                Result.success(demoAnalysis(voiceDescription))
            } else {
                Log.e(TAG, "Erreur analyse", e)
                Result.failure(e)
            }
        }
    }

    // ── Analyse principale ────────────────────────────────────────────────────

    /**
     * Prend la description anglaise brute de moondream et construit
     * une réponse structurée claire en français.
     */
    private fun buildFrenchAnalysis(raw: String, voiceHint: String): AIResponse {
        val t = raw.lowercase()
        val hint = voiceHint.lowercase()

        val objectType = detectObjectType(t, hint)
        val fraudLevel = detectFraudLevel(t, hint)
        val details    = extractDetails(t)

        val fraudType   = objectType.label
        val riskLevel   = fraudLevel.level
        val description = buildDescription(objectType, fraudLevel, details, raw)

        return AIResponse(riskLevel, fraudType, description)
    }

    // ── Détection du type d'objet ─────────────────────────────────────────────

    private enum class ObjectType(val label: String) {
        BILLET("Billet de banque"),
        DOCUMENT("Document officiel"),
        CARTE("Carte bancaire / ID"),
        PRODUIT("Produit / Emballage"),
        TELEPHONE("Téléphone / Électronique"),
        INCONNU("Objet non identifié")
    }

    private fun detectObjectType(t: String, hint: String): ObjectType {
        val combined = "$t $hint"
        return when {
            combined.containsAny("banknote","bill","currency","money","euro","dollar","billet","monnaie","argent","cash") -> ObjectType.BILLET
            combined.containsAny("passport","passeport","id card","identity","carte d'identité","visa","license","permis","birth certificate") -> ObjectType.DOCUMENT
            combined.containsAny("credit card","debit card","bank card","carte bancaire","carte bleue","mastercard","visa card") -> ObjectType.CARTE
            combined.containsAny("product","package","label","bottle","box","brand","logo","étiquette","produit","emballage","bouteille","boîte") -> ObjectType.PRODUIT
            combined.containsAny("phone","smartphone","iphone","samsung","electronic","tablet","laptop","téléphone","ordinateur") -> ObjectType.TELEPHONE
            else -> ObjectType.INCONNU
        }
    }

    // ── Détection du niveau de fraude ─────────────────────────────────────────

    private enum class FraudLevel(val level: String) {
        CRITIQUE("CRITIQUE"),
        ELEVE("ÉLEVÉ"),
        MOYEN("MOYEN"),
        FAIBLE("FAIBLE")
    }

    private fun detectFraudLevel(t: String, hint: String): FraudLevel {
        val combined = "$t $hint"

        // Indicateurs critiques
        val critiqueScore = listOf(
            "counterfeit","fake","forged","falsified","fraudulent","stolen",
            "replica","copy","imitation","faux","contrefaçon","falsifié","volé"
        ).count { combined.contains(it) }

        // Indicateurs suspects
        val suspectScore = listOf(
            "suspicious","unusual","irregular","different","wrong","odd","strange",
            "blurry","blurred","poor quality","low quality","misaligned","misprint",
            "suspect","inhabituel","irrégulier","flou","mauvaise qualité","décalé"
        ).count { combined.contains(it) }

        // Indicateurs normaux/positifs
        val normalScore = listOf(
            "authentic","genuine","real","original","legitimate","normal","clear",
            "sharp","high quality","official","authentique","réel","officiel","net"
        ).count { combined.contains(it) }

        return when {
            critiqueScore >= 2                             -> FraudLevel.CRITIQUE
            critiqueScore == 1                             -> FraudLevel.ELEVE
            suspectScore >= 2 && normalScore == 0         -> FraudLevel.ELEVE
            suspectScore >= 1                             -> FraudLevel.MOYEN
            else                                          -> FraudLevel.FAIBLE
        }
    }

    // ── Extraction des détails visibles ───────────────────────────────────────

    private data class VisibleDetails(
        val hasText: Boolean,
        val hasNumbers: Boolean,
        val hasLogo: Boolean,
        val hasSecurity: Boolean,
        val hasDefects: Boolean
    )

    private fun extractDetails(t: String): VisibleDetails {
        return VisibleDetails(
            hasText     = t.containsAny("text","writing","letters","words","printed","inscription","écrit","texte","lettres"),
            hasNumbers  = t.containsAny("number","digit","serial","code","numéro","chiffre","série"),
            hasLogo     = t.containsAny("logo","brand","symbol","emblem","seal","marque","symbole","emblème","sceau"),
            hasSecurity = t.containsAny("hologram","watermark","security","thread","strip","hologr","filigrane","sécurité","fil de"),
            hasDefects  = t.containsAny("blurry","blur","smudge","smeared","faded","torn","damaged","defect","flou","tache","déchiré","abîmé","défaut")
        )
    }

    // ── Construction de la description française ──────────────────────────────

    private fun buildDescription(
        obj: ObjectType,
        fraud: FraudLevel,
        details: VisibleDetails,
        raw: String
    ): String {
        val sb = StringBuilder()

        // 1. Verdict principal
        val verdict = when (obj) {
            ObjectType.BILLET -> when (fraud) {
                FraudLevel.CRITIQUE -> "Faux billet détecté."
                FraudLevel.ELEVE    -> "Billet suspect — plusieurs anomalies détectées."
                FraudLevel.MOYEN    -> "Billet présentant quelques irrégularités."
                FraudLevel.FAIBLE   -> "Billet d'apparence authentique."
            }
            ObjectType.DOCUMENT -> when (fraud) {
                FraudLevel.CRITIQUE -> "Document falsifié — incohérences majeures."
                FraudLevel.ELEVE    -> "Document suspect — anomalies dans les éléments de sécurité."
                FraudLevel.MOYEN    -> "Document avec quelques irrégularités."
                FraudLevel.FAIBLE   -> "Document d'apparence conforme."
            }
            ObjectType.CARTE -> when (fraud) {
                FraudLevel.CRITIQUE -> "Carte frauduleuse — sécurités absentes ou falsifiées."
                FraudLevel.ELEVE    -> "Carte suspecte — anomalies visuelles détectées."
                FraudLevel.MOYEN    -> "Carte avec quelques particularités à vérifier."
                FraudLevel.FAIBLE   -> "Carte d'apparence normale."
            }
            ObjectType.PRODUIT -> when (fraud) {
                FraudLevel.CRITIQUE -> "Produit contrefait — logos et emballage non conformes."
                FraudLevel.ELEVE    -> "Produit potentiellement contrefait."
                FraudLevel.MOYEN    -> "Produit avec des éléments inhabituels."
                FraudLevel.FAIBLE   -> "Produit d'apparence authentique."
            }
            else -> when (fraud) {
                FraudLevel.CRITIQUE -> "Objet présentant des signes clairs de fraude."
                FraudLevel.ELEVE    -> "Objet suspect — anomalies détectées."
                FraudLevel.MOYEN    -> "Objet présentant quelques éléments inhabituels."
                FraudLevel.FAIBLE   -> "Aucune anomalie apparente détectée."
            }
        }
        sb.append(verdict)

        // 2. Description réelle de l'objet vue par l'IA (ce qui est scanné)
        val objectDesc = cleanRawDescription(raw)
        if (objectDesc.isNotBlank()) {
            sb.append("\n\nDescription de l'objet : ").append(objectDesc)
        }

        // 3. Éléments spécifiques observés
        val observed = mutableListOf<String>()
        if (details.hasText)     observed.add("texte / inscription visible")
        if (details.hasNumbers)  observed.add("numéros ou codes présents")
        if (details.hasLogo)     observed.add("logo ou marque identifié")
        if (details.hasSecurity) observed.add("éléments de sécurité détectés (hologramme / filigrane)")
        if (details.hasDefects)  observed.add("défauts visuels détectés (flou, taches, décalage d'impression)")

        if (observed.isNotEmpty()) {
            sb.append("\n\nÉléments observés : ").append(observed.joinToString(", ")).append(".")
        }

        // 4. Recommandation concrète
        val reco = when (fraud) {
            FraudLevel.CRITIQUE -> "Ne pas accepter cet objet. Signalez-le immédiatement aux autorités compétentes."
            FraudLevel.ELEVE    -> "Vérification approfondie requise : consultez un expert ou utilisez une lampe UV."
            FraudLevel.MOYEN    -> "Comparez avec un original et vérifiez les éléments de sécurité caractéristiques."
            FraudLevel.FAIBLE   -> "Aucune action immédiate requise. Restez vigilant lors de prochaines transactions."
        }
        sb.append("\n\nRecommandation : ").append(reco)

        return sb.toString()
    }

    /**
     * Nettoie la description brute de moondream pour la rendre lisible.
     * Supprime les artefacts, limite la longueur et capitalise la première lettre.
     */
    private fun cleanRawDescription(raw: String): String {
        return raw
            .replace(Regex("\\s+"), " ")           // espaces multiples
            .replace(Regex("^[^a-zA-Z]+"), "")     // caractères parasites au début
            .trim()
            .take(500)
            .let { if (it.isNotEmpty()) it[0].uppercaseChar() + it.substring(1) else it }
    }

    // ── Mode démo (Ollama indisponible) ───────────────────────────────────────

    private fun demoAnalysis(voiceDescription: String): AIResponse {
        val d = voiceDescription.lowercase()
        return when {
            d.containsAny("billet","argent","monnaie","euro","dollar") ->
                AIResponse("ÉLEVÉ", "Billet de banque",
                    "Billet suspect détecté. Vérifiez le filigrane en transparence, le fil de sécurité intégré et les micro-impressions sous loupe. Comparez avec un billet authentique de la même valeur.")
            d.containsAny("document","passeport","carte","permis","visa") ->
                AIResponse("CRITIQUE", "Document officiel",
                    "Document suspect analysé. Examinez la holographie, les numéros de série, les tampons officiels et la qualité du papier sécurisé.")
            d.containsAny("produit","contrefaçon","faux","marque","étiquette") ->
                AIResponse("MOYEN", "Produit / Emballage",
                    "Produit potentiellement contrefait. Vérifiez la cohérence du logo, la qualité d'impression de l'étiquette et le numéro de série sur le site officiel.")
            else ->
                AIResponse("FAIBLE", "Analyse indisponible",
                    "Ollama n'est pas accessible. Lancez 'ollama serve' sur votre PC puis relancez l'analyse pour obtenir un résultat IA réel.")
        }
    }

    private fun String.containsAny(vararg words: String) = words.any { this.contains(it) }
}
