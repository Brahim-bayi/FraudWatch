package com.fraudwatch.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.fraudwatch.R
import com.fraudwatch.data.model.Report
import com.fraudwatch.databinding.FragmentMapBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.MapViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/**
 * Fragment de l'écran Carte.
 *
 * Affiche une carte OpenStreetMap (via osmdroid) avec :
 *   - Les marqueurs de tous les rapports de fraude (position GPS)
 *   - La position de l'utilisateur en temps réel (si permission accordée)
 *   - Une carte de détails au clic sur un marqueur
 *
 * Bibliothèque carte : osmdroid (OpenStreetMap, open source, pas de clé API)
 *
 * Cycle de vie de la carte :
 *   onResume → mapView.onResume() + activer GPS
 *   onPause  → mapView.onPause() + désactiver GPS (économie batterie)
 */
class MapFragment : Fragment() {

    // ViewBinding — nullifié dans onDestroyView pour éviter les fuites
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    // ViewModel qui charge tous les rapports depuis Firestore/cache
    private val mapViewModel: MapViewModel by viewModels()

    // Vue principale de la carte osmdroid
    private lateinit var mapView: MapView

    // Overlay qui affiche et suit la position GPS de l'utilisateur
    private var myLocationOverlay: MyLocationNewOverlay? = null

    /**
     * Launcher de permission GPS.
     * Demande ACCESS_FINE_LOCATION ; si accordé → active l'overlay de position.
     * Utilise l'API ActivityResult (remplace requestPermissions + onRequestPermissionsResult).
     */
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableMyLocationOverlay()
        // Si refusé : la carte fonctionne mais sans point bleu de position
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupMap()      // Configurer osmdroid
        observeViewModel() // Observer les rapports du ViewModel
        setupClicks()   // Configurer les boutons (fermer détails, rafraîchir)

        // Vérifier la permission GPS et activer l'overlay ou demander la permission
        if (hasLocationPermission()) {
            enableMyLocationOverlay()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Initialise la carte osmdroid.
     *
     * - Source de tuiles : MAPNIK (OpenStreetMap standard)
     * - Multi-touch : pinch-to-zoom activé
     * - Vue initiale : Maroc (31.79°N, 7.09°W) zoom 5
     */
    private fun setupMap() {
        mapView = binding.map
        mapView.setTileSource(TileSourceFactory.MAPNIK) // Tuiles OpenStreetMap
        mapView.setMultiTouchControls(true)             // Zoom par pincement
        mapView.controller.setZoom(5.0)                 // Zoom initial : vue pays
        mapView.controller.setCenter(GeoPoint(31.7917, -7.0926)) // Centre : Maroc
    }

    /**
     * Observe les LiveData du ViewModel.
     *
     * reports : liste mise à jour → place les marqueurs sur la carte
     * loading : état de chargement → spinner visible/invisible
     */
    private fun observeViewModel() {
        mapViewModel.reports.observe(viewLifecycleOwner) { reports ->
            plotReports(reports) // Placer tous les marqueurs
        }
        mapViewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
        }
    }

    /**
     * Place les marqueurs de fraude sur la carte.
     *
     * - Supprime d'abord tous les marqueurs existants (sauf l'overlay de position)
     * - Ignore les rapports sans coordonnées GPS valides (lat=0, lon=0)
     * - Au clic sur un marqueur → affiche la carte de détails
     * - Si des rapports valides existent → recentre la carte sur le premier
     *
     * @param reports Liste de tous les rapports à afficher
     */
    private fun plotReports(reports: List<Report>) {
        // Supprimer uniquement les Markers (pas l'overlay de position)
        mapView.overlays.removeAll { it is Marker }

        // Filtrer les rapports sans coordonnées GPS (position par défaut 0,0)
        val validReports = reports.filter { it.latitude != 0.0 && it.longitude != 0.0 }

        validReports.forEach { report ->
            val marker = Marker(mapView).apply {
                position = GeoPoint(report.latitude, report.longitude)
                title = report.fraudType          // Titre de la popup
                snippet = "${report.riskLevel} — ${report.date}" // Sous-titre
                icon = getMarkerIcon(report.riskLevel) // Couleur selon le risque

                // Clic sur le marqueur → afficher les détails en bas d'écran
                setOnMarkerClickListener { _, _ ->
                    showDetails(report)
                    true // true = événement consommé (pas de popup par défaut)
                }
            }
            mapView.overlays.add(marker)
        }

        // Recentrer la carte sur le premier rapport si la liste n'est pas vide
        if (validReports.isNotEmpty()) {
            val first = validReports.first()
            mapView.controller.animateTo(GeoPoint(first.latitude, first.longitude))
            mapView.controller.setZoom(10.0) // Zoom ville pour voir le marqueur
        }

        mapView.invalidate() // Forcer le redessin de la carte
    }

    /**
     * Retourne l'icône Android système correspondant au niveau de risque.
     *
     * FAIBLE   → presence_online (vert)
     * CRITIQUE → presence_busy (rouge)
     * Autres   → presence_away (orange)
     *
     * Note : ces icônes sont des drawables système Android, pas des ressources custom.
     *
     * @param riskLevel Niveau de risque du rapport
     * @return Drawable de l'icône du marqueur
     */
    private fun getMarkerIcon(riskLevel: String): Drawable? {
        val color = when (riskLevel.uppercase()) {
            "FAIBLE"   -> android.R.drawable.presence_online // Vert
            "CRITIQUE" -> android.R.drawable.presence_busy   // Rouge
            else       -> android.R.drawable.presence_away   // Orange (MOYEN, ÉLEVÉ)
        }
        return ContextCompat.getDrawable(requireContext(), color)
    }

    /**
     * Active l'overlay de position GPS de l'utilisateur sur la carte.
     *
     * MyLocationNewOverlay affiche un point bleu qui suit la position en temps réel.
     * runOnFirstFix : quand le GPS obtient sa première position, recentre la carte
     * et zoome au niveau rue (zoom 14).
     *
     * L'overlay est ajouté en position 0 (sous les marqueurs de fraude).
     */
    @SuppressLint("MissingPermission") // Permission vérifiée dans hasLocationPermission()
    private fun enableMyLocationOverlay() {
        myLocationOverlay = MyLocationNewOverlay(
            GpsMyLocationProvider(requireContext()), mapView
        ).apply {
            enableMyLocation()     // Active la mise à jour GPS
            enableFollowLocation() // La carte suit la position automatiquement

            // Quand le GPS a sa première position → recentrer et zoomer
            runOnFirstFix {
                requireActivity().runOnUiThread {
                    mapView.controller.animateTo(myLocation) // Centrer sur l'utilisateur
                    mapView.controller.setZoom(14.0)         // Zoom quartier
                }
            }
        }
        // Ajouter en premier (index 0) pour être sous les marqueurs de fraude
        mapView.overlays.add(0, myLocationOverlay)
        mapView.invalidate()
    }

    /**
     * Affiche la carte de détails d'un rapport au bas de l'écran.
     * Chargement de l'image via Glide si une URL est disponible.
     *
     * @param report Rapport dont les détails sont à afficher
     */
    private fun showDetails(report: Report) {
        binding.cardDetails.visible()
        binding.tvDetailRisk.text = report.riskLevel
        binding.tvDetailType.text = report.fraudType
        binding.tvDetailDesc.text = report.description
        binding.tvDetailDate.text = report.date

        // Charger l'image si disponible (vide en mode anonyme)
        if (report.imageUrl.isNotEmpty()) {
            Glide.with(this)
                .load(report.imageUrl)
                .placeholder(R.drawable.ic_placeholder)
                .centerCrop()
                .into(binding.ivDetailImage)
        }
    }

    /**
     * Configure les boutons de la vue.
     * - btnCloseDetails : ferme la carte de détails
     * - btnRefresh : recharge les rapports depuis Firestore
     */
    private fun setupClicks() {
        binding.btnCloseDetails.setOnClickListener { binding.cardDetails.gone() }
        binding.btnRefresh.setOnClickListener { mapViewModel.loadReports() }
    }

    /**
     * Vérifie si la permission ACCESS_FINE_LOCATION est accordée.
     * Fine = GPS haute précision (vs Coarse = réseau cellulaire)
     */
    private fun hasLocationPermission() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    /** Reprendre les mises à jour GPS et le rendu de la carte à la reprise. */
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        myLocationOverlay?.enableMyLocation() // Réactiver le GPS
    }

    /** Suspendre les mises à jour GPS et le rendu pour économiser la batterie. */
    override fun onPause() {
        super.onPause()
        mapView.onPause()
        myLocationOverlay?.disableMyLocation() // Désactiver le GPS (économie batterie)
    }

    /** Libérer le binding pour éviter les fuites mémoire. */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
