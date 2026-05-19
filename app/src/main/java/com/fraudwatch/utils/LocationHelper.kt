package com.fraudwatch.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper pour obtenir la position GPS de l'appareil.
 *
 * Utilise FusedLocationProviderClient (Google Play Services) qui combine
 * GPS, WiFi et réseau cellulaire pour une localisation rapide et précise.
 *
 * Si la permission n'est pas accordée ou si la localisation échoue,
 * retourne Casablanca comme position par défaut.
 */
class LocationHelper(private val context: Context) {

    // Client Google qui fusionne GPS + réseau pour une localisation optimale
    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Récupère la position actuelle de l'appareil de manière asynchrone.
     *
     * Utilise suspendCancellableCoroutine pour convertir l'API callback
     * de Google en fonction suspend (compatible coroutines).
     *
     * Priorité PRIORITY_HIGH_ACCURACY : utilise le GPS pour une précision maximale.
     * En cas d'échec ou de permission manquante → retourne Casablanca (33.57, -7.58).
     *
     * @return Location avec latitude/longitude, jamais null
     */
    @SuppressLint("MissingPermission") // Permission vérifiée via hasPermission()
    suspend fun getCurrentLocation(): Location {
        // Si pas de permission GPS → position par défaut
        if (!hasPermission()) return casablancaLocation()

        return suspendCancellableCoroutine { cont ->
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { location ->
                    // location peut être null si le GPS n'a pas de fix
                    cont.resume(location ?: casablancaLocation())
                }
                .addOnFailureListener {
                    // GPS indisponible → position par défaut
                    cont.resume(casablancaLocation())
                }
        }
    }

    /**
     * Position GPS de secours : centre de Casablanca, Maroc.
     * Utilisée quand le GPS est indisponible ou non autorisé.
     * "default" est le nom du provider factice (pas un vrai provider Android).
     */
    private fun casablancaLocation() = Location("default").apply {
        latitude = 33.5731
        longitude = -7.5898
    }

    /**
     * Vérifie si la permission ACCESS_FINE_LOCATION est accordée.
     * Fine location = GPS haute précision (contrairement à COARSE qui utilise le réseau).
     */
    private fun hasPermission() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}
