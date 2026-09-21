package com.ayush.madv2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ayush.madv2.ui.CallAiApp
import com.ayush.madv2.ui.theme.MADv2Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MADv2Theme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CallAiApp()
                }
            }
        }
    }
}
