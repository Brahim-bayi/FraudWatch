package com.fraudwatch.data.model

import com.google.gson.annotations.SerializedName

/**
 * Requête envoyée à Ollama pour analyser une image.
 * Le modèle "moondream" est un modèle de vision léger (1.7 GB)
 * capable d'analyser des images et de répondre en texte.
 */
data class OllamaRequest(
    @SerializedName("model")
    val model: String = "moondream",       // Modèle IA de vision utilisé

    @SerializedName("prompt")
    val prompt: String,                     // Question posée à l'IA sur l'image

    @SerializedName("images")
    val images: List<String>,              // Liste d'images en base64

    @SerializedName("stream")
    val stream: Boolean = false            // false = réponse complète d'un coup
)

/**
 * Réponse reçue depuis Ollama après analyse de l'image.
 */
data class OllamaResponse(
    @SerializedName("response")
    val response: String = "",             // Texte de réponse de l'IA (JSON)

    @SerializedName("done")
    val done: Boolean = false              // true = génération terminée
)
