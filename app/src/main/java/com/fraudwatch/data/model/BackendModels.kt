package com.fraudwatch.data.model

import com.google.gson.annotations.SerializedName

data class ImageUploadResponse(
    @SerializedName("id")       val id: String,
    @SerializedName("url")      val url: String,
    @SerializedName("filename") val filename: String
)
