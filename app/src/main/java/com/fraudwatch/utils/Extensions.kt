package com.fraudwatch.utils

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

fun View.visible() { visibility = View.VISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.invisible() { visibility = View.INVISIBLE }

fun Context.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun View.snackbar(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG).show()

fun String.toRiskColor(): Int = when (this.uppercase()) {
    "FAIBLE" -> Color.parseColor("#4CAF50")
    "MOYEN" -> Color.parseColor("#FF9800")
    "ÉLEVÉ", "ELEVE" -> Color.parseColor("#F44336")
    "CRITIQUE" -> Color.parseColor("#212121")
    else -> Color.parseColor("#9E9E9E")
}
