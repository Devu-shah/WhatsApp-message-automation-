package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.ReachHubApp
import com.example.ui.theme.ReachHubTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val appContainer = (application as ReachHubApplication).container
    setContent { ReachHubTheme { ReachHubApp(appContainer = appContainer) } }
  }
}
