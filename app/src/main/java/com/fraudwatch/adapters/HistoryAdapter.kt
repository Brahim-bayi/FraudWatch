package com.fraudwatch.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.fraudwatch.R
import com.fraudwatch.data.model.Report
import com.fraudwatch.databinding.ItemHistoryBinding
import com.fraudwatch.utils.toRiskColor

/**
 * Adapter RecyclerView pour la liste de l'historique des rapports.
 *
 * Utilise ListAdapter (avec DiffUtil) pour des mises à jour efficaces :
 * seuls les éléments réellement modifiés sont redessinés, pas toute la liste.
 *
 * @param onItemClick Callback appelé quand l'utilisateur clique sur un rapport.
 *                    Reçoit le Report cliqué → Navigation vers ResultFragment.
 */
class HistoryAdapter(
    private val onItemClick: (Report) -> Unit
) : ListAdapter<Report, HistoryAdapter.ViewHolder>(DiffCallback()) {

    /**
     * ViewHolder : représente une ligne de la liste (item_history.xml).
     * Chaque ViewHolder est réutilisé par RecyclerView pour économiser la mémoire.
     */
    inner class ViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        /**
         * Lie un objet Report aux vues de la ligne.
         * Appelé par onBindViewHolder à chaque affichage/recyclage.
         *
         * @param report Le rapport à afficher dans cette ligne
         */
        fun bind(report: Report) {
            with(binding) {
                // Type de fraude — "Type inconnu" si l'IA n'a pas pu identifier
                tvFraudType.text = report.fraudType.ifBlank { "Type inconnu" }

                // Date formatée "dd/MM/yyyy HH:mm"
                tvDate.text = report.date

                // Badge de risque : texte + couleur de fond selon le niveau
                tvRiskBadge.text = report.riskLevel
                tvRiskBadge.setBackgroundColor(report.riskLevel.toRiskColor())
                // toRiskColor() → extension dans Extensions.kt
                // FAIBLE=vert, MOYEN=orange, ÉLEVÉ/CRITIQUE=rouge

                // Aperçu de l'analyse IA (tronqué par le layout XML)
                tvDescription.text = report.description

                // Image miniature via Glide (chargement asynchrone + placeholder)
                if (report.imageUrl.isNotEmpty()) {
                    Glide.with(root.context)
                        .load(report.imageUrl)               // URL Firebase Storage
                        .placeholder(R.drawable.ic_placeholder) // Icône pendant le chargement
                        .centerCrop()                        // Recadrage centré
                        .into(ivThumbnail)
                } else {
                    // Pas d'image (mode anonyme) → icône placeholder
                    ivThumbnail.setImageResource(R.drawable.ic_placeholder)
                }

                // Clic sur la ligne → naviguer vers le détail du rapport
                root.setOnClickListener { onItemClick(report) }
            }
        }
    }

    /**
     * Crée un nouveau ViewHolder quand RecyclerView n'a pas de vue recyclable disponible.
     * Inflate le layout item_history.xml via ViewBinding.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    /**
     * Lie les données du rapport à la position donnée dans la liste.
     * RecyclerView appelle cette méthode quand une vue est affichée ou recyclée.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position)) // getItem() fourni par ListAdapter
    }

    /**
     * DiffUtil compare les anciens et nouveaux éléments pour calculer
     * le minimum de changements à appliquer (insertions, suppressions, mises à jour).
     * Beaucoup plus performant que notifyDataSetChanged().
     */
    class DiffCallback : DiffUtil.ItemCallback<Report>() {
        // Même ID = même rapport (même ligne dans la liste)
        override fun areItemsTheSame(o: Report, n: Report) = o.id == n.id

        // Même contenu = pas besoin de redessiner la vue
        override fun areContentsTheSame(o: Report, n: Report) = o == n
    }
}
