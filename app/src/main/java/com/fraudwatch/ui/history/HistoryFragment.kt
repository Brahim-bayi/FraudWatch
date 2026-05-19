package com.fraudwatch.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fraudwatch.adapters.HistoryAdapter
import com.fraudwatch.databinding.FragmentHistoryBinding
import com.fraudwatch.utils.gone
import com.fraudwatch.utils.visible
import com.fraudwatch.viewmodel.HistoryViewModel

/**
 * Fragment de l'écran Historique.
 *
 * Affiche la liste des rapports de fraude de l'utilisateur connecté.
 * Permet de :
 *   - Rechercher par texte (fraudType, description, riskLevel)
 *   - Filtrer par niveau de risque via des chips (Tous / Faible / Moyen / Élevé / Critique)
 *   - Naviguer vers le détail d'un rapport en cliquant dessus
 *
 * Schéma de communication :
 *   HistoryFragment → HistoryViewModel → ReportRepository → Firestore/Cache
 *                  ←─────────────────── LiveData (filteredReports, loading)
 */
class HistoryFragment : Fragment() {

    // ViewBinding — accès aux vues sans findViewById (nullifié dans onDestroyView)
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // ViewModel partagé avec le cycle de vie du Fragment (survit aux rotations)
    private val historyViewModel: HistoryViewModel by viewModels()

    // Adapter de la liste — réutilisé pour éviter de recréer les vues
    private lateinit var adapter: HistoryAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView() // Configurer la liste
        setupSearch()       // Configurer la barre de recherche
        setupChips()        // Configurer les filtres par niveau
        observeViewModel()  // Observer les LiveData du ViewModel
    }

    /**
     * Configure le RecyclerView avec un LinearLayoutManager vertical et l'adapter.
     * Le clic sur un rapport navigue vers ResultFragment en passant l'ID du rapport.
     */
    private fun setupRecyclerView() {
        adapter = HistoryAdapter { report ->
            // Navigation vers le détail — l'ID permet de charger le rapport complet
            val action = HistoryFragmentDirections.actionHistoryFragmentToResultFragment(report.id)
            findNavController().navigate(action)
        }
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext()) // Liste verticale
            this.adapter = this@HistoryFragment.adapter
        }
    }

    /**
     * Configure la recherche textuelle.
     * doOnTextChanged est appelé à chaque frappe clavier → filtre en temps réel.
     * Le ViewModel combine ce texte avec le filtre de risque actif.
     */
    private fun setupSearch() {
        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            historyViewModel.search(text?.toString() ?: "")
        }
    }

    /**
     * Configure les chips de filtre par niveau de risque.
     * Chaque chip appelle filterByRiskLevel() dans le ViewModel.
     * Le filtre et la recherche s'appliquent simultanément.
     */
    private fun setupChips() {
        binding.chipAll.setOnClickListener     { historyViewModel.filterByRiskLevel("ALL")     }
        binding.chipFaible.setOnClickListener  { historyViewModel.filterByRiskLevel("FAIBLE")  }
        binding.chipMoyen.setOnClickListener   { historyViewModel.filterByRiskLevel("MOYEN")   }
        binding.chipEleve.setOnClickListener   { historyViewModel.filterByRiskLevel("ÉLEVÉ")   }
        binding.chipCritique.setOnClickListener{ historyViewModel.filterByRiskLevel("CRITIQUE") }
    }

    /**
     * Observe les LiveData du ViewModel pour mettre à jour l'UI.
     *
     * filteredReports : liste mise à jour après filtre/recherche
     *   → submitList() utilise DiffUtil pour n'animer que les changements réels
     *   → tvEmpty visible si la liste est vide
     *
     * loading : état de chargement
     *   → progressBar visible pendant le fetch Firestore
     */
    private fun observeViewModel() {
        historyViewModel.filteredReports.observe(viewLifecycleOwner) { reports ->
            adapter.submitList(reports)
            // Afficher un message si aucun rapport ne correspond aux filtres
            if (reports.isEmpty()) binding.tvEmpty.visible() else binding.tvEmpty.gone()
        }

        historyViewModel.loading.observe(viewLifecycleOwner) { loading ->
            if (loading) binding.progressBar.visible() else binding.progressBar.gone()
        }
    }

    /**
     * Nullifier le binding pour éviter les fuites mémoire.
     * Le Fragment peut survivre à sa vue — ne pas garder de référence à la vue.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
