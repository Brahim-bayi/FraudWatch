package com.fraudwatch

import android.app.Application
import com.fraudwatch.data.repository.ReportCache
import com.fraudwatch.utils.NotificationHelper
import org.osmdroid.config.Configuration

class FraudWatchApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        ReportCache.init(this)
        Configuration.getInstance().apply {
            userAgentValue = "FraudWatch/1.0"
            load(this@FraudWatchApplication,
                getSharedPreferences("osmdroid", MODE_PRIVATE))
        }
    }
}
