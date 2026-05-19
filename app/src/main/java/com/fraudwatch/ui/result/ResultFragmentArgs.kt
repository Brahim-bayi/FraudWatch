package com.fraudwatch.ui.result

import android.os.Bundle
import androidx.navigation.NavArgs

data class ResultFragmentArgs(val reportId: String) : NavArgs {

    fun toBundle(): Bundle = Bundle().apply {
        putString("reportId", reportId)
    }

    companion object {
        @JvmStatic
        fun fromBundle(bundle: Bundle): ResultFragmentArgs {
            return ResultFragmentArgs(
                reportId = bundle.getString("reportId") ?: ""
            )
        }
    }
}
