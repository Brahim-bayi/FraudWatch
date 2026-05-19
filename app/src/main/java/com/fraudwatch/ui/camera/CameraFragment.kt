package com.fraudwatch.ui.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.speech.RecognizerIntent
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.fraudwatch.databinding.FragmentCameraBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.CameraViewModel
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment principal de la caméra.
 * Permet de capturer une image, d'ajouter une description vocale,
 * puis d'envoyer l'image à l'IA (Ollama/moondream) pour analyse de fraude.
 */
class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private val cameraViewModel: CameraViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private var flashEnabled = false
    private var voiceDescription: String = ""        // Description vocale de l'utilisateur
    private lateinit var cameraExecutor: ExecutorService

    // Demande les permissions caméra et localisation au démarrage
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.CAMERA] == true) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "La permission caméra est requise", Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    // Récupère le texte reconnu par la reconnaissance vocale
    private val voiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        if (!matches.isNullOrEmpty()) {
            voiceDescription = matches[0]
            binding.tvVoiceHint.text = "🎤 \"$voiceDescription\""
            binding.tvVoiceHint.setTextColor(
                ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        observeViewModel()
        setupClicks()
        checkAndRequestPermissions()
    }

    /**
     * Observe les états de l'analyse IA et met à jour l'UI en conséquence.
     */
    private fun observeViewModel() {
        cameraViewModel.analysisState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is CameraViewModel.AnalysisState.Analyzing -> {
                    // Affiche l'overlay d'analyse avec message et spinner
                    binding.overlayAnalysis.visible()
                    binding.progressBar.visible()
                    binding.tvStatus.visible()
                    binding.tvTimer.visible()
                    binding.btnCancelAnalysis.visible()
                    binding.tvStatus.text = state.message
                    binding.btnCapture.isEnabled = false
                    binding.btnMic.isEnabled = false
                }
                is CameraViewModel.AnalysisState.Tick -> {
                    // Met à jour le chronomètre chaque seconde
                    binding.tvTimer.text = "⏱ ${state.seconds}s"
                }
                is CameraViewModel.AnalysisState.Success -> {
                    // Navigue vers le fragment résultat avec l'ID du rapport
                    resetOverlay()
                    val action = CameraFragmentDirections
                        .actionCameraFragmentToResultFragment(state.report.id)
                    findNavController().navigate(action)
                }
                is CameraViewModel.AnalysisState.Error -> {
                    resetOverlay()
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Cache l'overlay d'analyse et réactive les boutons
    private fun resetOverlay() {
        binding.overlayAnalysis.gone()
        binding.progressBar.gone()
        binding.tvStatus.gone()
        binding.tvTimer.gone()
        binding.btnCancelAnalysis.gone()
        binding.btnCapture.isEnabled = true
        binding.btnMic.isEnabled = true
    }

    // Configure les listeners des boutons de l'interface
    private fun setupClicks() {
        binding.btnCapture.setOnClickListener { capturePhoto() }
        binding.btnMic.setOnClickListener { startVoiceInput() }
        binding.btnCancelAnalysis.setOnClickListener {
            cameraViewModel.cancelAnalysis()
            resetOverlay()
        }
        binding.btnFlash.setOnClickListener {
            // Toggle du flash
            flashEnabled = !flashEnabled
            imageCapture?.flashMode = if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            binding.btnFlash.alpha = if (flashEnabled) 1.0f else 0.5f
        }
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
    }

    /**
     * Lance la reconnaissance vocale pour obtenir une description de la fraude.
     * La description est envoyée en contexte supplémentaire à l'IA.
     */
    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Décrivez la fraude suspectée...")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Reconnaissance vocale non disponible", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Initialise CameraX avec preview + capture d'image sur la caméra arrière.
     */
    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            // Configuration de la capture : mode rapide, flash désactivé par défaut
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Erreur caméra: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /**
     * Capture une photo et la transmet pour traitement et analyse IA.
     */
    private fun capturePhoto() {
        val capture = imageCapture ?: run {
            Toast.makeText(requireContext(), "Caméra non prête", Toast.LENGTH_SHORT).show()
            return
        }
        binding.btnCapture.isEnabled = false
        capture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    // Lit les bytes bruts du buffer de l'image capturée
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()
                    processAndAnalyze(bytes)
                }
                override fun onError(e: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    Toast.makeText(requireContext(), "Erreur capture: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    /**
     * Redimensionne et compresse l'image puis l'encode en base64 pour Ollama.
     * Limite la taille à 1024px max pour éviter des requêtes trop lourdes.
     */
    private fun processAndAnalyze(rawBytes: ByteArray) {
        try {
            val bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: throw Exception("Impossible de décoder l'image")

            // Redimensionne si l'image dépasse 1024px
            val maxDim = 1024
            val scale = minOf(1.0f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )

            // Compression JPEG 80% pour réduire la taille du base64
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            val compressed = out.toByteArray()
            val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)

            // Lance l'analyse IA avec l'image et la description vocale
            cameraViewModel.analyzeAndSave(compressed, base64, voiceDescription)
        } catch (e: Exception) {
            binding.btnCapture.isEnabled = true
            Toast.makeText(requireContext(), "Erreur: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Vérifie les permissions et les demande si nécessaires
    private fun checkAndRequestPermissions() {
        val missing = mutableListOf<String>()
        if (!hasPerm(Manifest.permission.CAMERA)) missing.add(Manifest.permission.CAMERA)
        if (!hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)) missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (missing.isEmpty()) startCamera() else permissionLauncher.launch(missing.toTypedArray())
    }

    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(requireContext(), p) == PackageManager.PERMISSION_GRANTED

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
