package com.kilu.pocketagent

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.kilu.pocketagent.core.network.OperatorHeartbeat
import com.kilu.pocketagent.core.storage.DeviceProfileStore
import com.kilu.pocketagent.core.ui.NavGraph
import com.kilu.pocketagent.core.ui.theme.KiluTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private var heartbeatStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KiluTheme {
                NavGraph()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Start heartbeat loop once per activity lifecycle:
        //   - Approver devices: this is the ONLY heartbeat sender (no always-on service)
        //   - Hub devices: extra ping on app open; HubRuntimeService also runs the loop
        if (!heartbeatStarted) {
            heartbeatStarted = true
            val store = DeviceProfileStore(this)
            OperatorHeartbeat.startLoop(lifecycleScope, store)
        }
    }
}
