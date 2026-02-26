package com.kilu.pocketagent

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kilu.pocketagent.core.hub.service.HubRuntimeService
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServiceSmokeTest {

    @Test
    fun testHubServiceStartsAndStops() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        
        val startIntent = Intent(context, HubRuntimeService::class.java).apply {
            action = HubRuntimeService.ACTION_START
        }
        
        val componentName = context.startService(startIntent)
        assertNotNull("Service should start successfully", componentName)
        
        // Wait briefly to let the service create its notification and job
        Thread.sleep(1000)
        
        val stopIntent = Intent(context, HubRuntimeService::class.java).apply {
            action = HubRuntimeService.ACTION_STOP
        }
        context.startService(stopIntent)
    }
}
