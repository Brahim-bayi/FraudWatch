package com.fraudwatch.data.model

import com.google.gson.annotations.SerializedName

data class AIResponse(
    @SerializedName("riskLevel")
    val riskLevel: String = "MOYEN",
    @SerializedName("fraudType")
    val fraudType: String = "Inconnu",
    @SerializedName("description")
    val description: String = ""
)
