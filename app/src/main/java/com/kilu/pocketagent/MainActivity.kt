package com.kilu.pocketagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.kilu.pocketagent.core.ui.NavGraph
import com.kilu.pocketagent.core.ui.theme.KiluTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            KiluTheme {
                NavGraph()
            }
        }
    }
}
