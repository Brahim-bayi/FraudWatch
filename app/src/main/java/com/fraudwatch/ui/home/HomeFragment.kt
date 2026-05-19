package com.fraudwatch.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.fraudwatch.R
import com.fraudwatch.databinding.FragmentHomeBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.HomeViewModel
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val user = FirebaseAuth.getInstance().currentUser
        binding.tvWelcome.text = "Bienvenue, ${user?.email?.substringBefore('@') ?: "Agent"}"

        observeViewModel()
        setupClicks()
    }

    private fun observeViewModel() {
        homeViewModel.stats.observe(viewLifecycleOwner) { stats ->
            binding.tvTotalValue.text = (stats["total"] ?: 0).toString()
            binding.tvCritiqueValue.text = (stats["critique"] ?: 0).toString()
            binding.tvEleveValue.text = (stats["eleve"] ?: 0).toString()
            binding.tvMoyenValue.text = (stats["moyen"] ?: 0).toString()
        }

        homeViewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
        }
    }

    private fun setupClicks() {
        binding.btnScanFraud.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_camera)
        }

        // Naviguer vers les destinations de la bottom nav directement
        binding.btnHistory.setOnClickListener {
            findNavController().navigate(R.id.history)
        }

        binding.btnMap.setOnClickListener {
            findNavController().navigate(R.id.map)
        }
    }

    override fun onResume() {
        super.onResume()
        homeViewModel.loadStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
