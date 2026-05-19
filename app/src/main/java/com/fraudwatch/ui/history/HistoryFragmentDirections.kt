package com.fraudwatch.ui.history

import android.os.Bundle
import androidx.navigation.NavDirections
import com.fraudwatch.R

class HistoryFragmentDirections private constructor() {
    companion object {
        fun actionHistoryFragmentToResultFragment(reportId: String): NavDirections =
            object : NavDirections {
                override val actionId: Int = R.id.action_historyFragment_to_resultFragment
                override val arguments: Bundle = Bundle().apply {
                    putString("reportId", reportId)
                }
            }
    }
}
