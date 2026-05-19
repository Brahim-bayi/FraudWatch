package com.fraudwatch.ui.result

import android.content.Intent
import android.location.Geocoder
import android.os.Bundle
import java.util.Locale
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.fraudwatch.R
import com.fraudwatch.data.model.Report
import com.fraudwatch.data.repository.ReportCache
import com.fraudwatch.databinding.FragmentResultBinding
import com.fraudwatch.utils.NotificationHelper
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.toRiskColor
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.ResultViewModel

class ResultFragment : Fragment() {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!
    private val resultViewModel: ResultViewModel by viewModels()

    // Lecture directe depuis les arguments du fragment (pas besoin de Safe Args)
    private val reportId: String
        get() = arguments?.getString("reportId") ?: ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupClicks()
        observeViewModel()

        if (reportId.isNotBlank()) {
            resultViewModel.loadReport(reportId)
        } else {
            binding.tvDescription.text = getString(R.string.error_not_found)
        }
    }

    private fun observeViewModel() {
        resultViewModel.report.observe(viewLifecycleOwner) { report ->
            displayReport(report)
        }

        resultViewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
        }

        resultViewModel.error.observe(viewLifecycleOwner) { error ->
            binding.tvDescription.text = error
        }
    }

    private fun displayReport(report: Report) {
        with(binding) {
            tvRiskLevel.text = report.riskLevel
            tvRiskLevel.setBackgroundColor(report.riskLevel.toRiskColor())
            tvFraudType.text = report.fraudType.ifBlank { "Inconnu" }
            tvDescription.text = report.description.ifBlank { "Aucune description." }
            tvDate.text = report.date.ifBlank { "—" }
            tvLocation.text = getLocationName(report.latitude, report.longitude)

            val cachedImage = ReportCache.getImage(report.id)
            when {
                cachedImage != null -> Glide.with(requireContext())
                    .load(cachedImage)
                    .placeholder(R.drawable.ic_placeholder)
                    .centerCrop()
                    .into(ivReport)
                report.imageUrl.isNotEmpty() -> Glide.with(requireContext())
                    .load(report.imageUrl)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .centerCrop()
                    .into(ivReport)
            }

            // Notification si risque élevé ou critique
            val level = report.riskLevel.uppercase().trim()
            if (level == "ÉLEVÉ" || level == "ELEVE" || level == "CRITIQUE") {
                NotificationHelper.sendRiskNotification(
                    requireContext(), report.riskLevel, report.fraudType
                )
            }
        }
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnShare.setOnClickListener {
            val report = resultViewModel.report.value ?: return@setOnClickListener
            val text = buildString {
                appendLine("🚨 FraudWatch — Rapport de fraude")
                appendLine("━━━━━━━━━━━━━━━━━━")
                appendLine("Niveau de risque : ${report.riskLevel}")
                appendLine("Type de fraude   : ${report.fraudType}")
                appendLine("Description      : ${report.description}")
                appendLine("Localisation     : ${report.latitude}, ${report.longitude}")
                appendLine("Date             : ${report.date}")
            }
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                putExtra(Intent.EXTRA_SUBJECT, "Rapport FraudWatch — ${report.riskLevel}")
            }
            startActivity(Intent.createChooser(intent, "Partager le rapport"))
        }
    }

    private fun getLocationName(lat: Double, lng: Double): String {
        if (lat == 0.0 && lng == 0.0) return "Localisation non disponible"
        return try {
            val geocoder = Geocoder(requireContext(), Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                listOfNotNull(addr.locality, addr.countryName).joinToString(", ")
                    .ifBlank { "%.4f, %.4f".format(lat, lng) }
            } else {
                "%.4f, %.4f".format(lat, lng)
            }
        } catch (e: Exception) {
            "%.4f, %.4f".format(lat, lng)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
