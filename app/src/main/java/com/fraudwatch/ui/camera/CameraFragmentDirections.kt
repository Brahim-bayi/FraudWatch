package com.fraudwatch.ui.camera

import android.os.Bundle
import androidx.navigation.NavDirections
import com.fraudwatch.R

class CameraFragmentDirections private constructor() {
    companion object {
        fun actionCameraFragmentToResultFragment(reportId: String): NavDirections =
            object : NavDirections {
                override val actionId: Int = R.id.action_cameraFragment_to_resultFragment
                override val arguments: Bundle = Bundle().apply {
                    putString("reportId", reportId)
                }
            }
    }
}
