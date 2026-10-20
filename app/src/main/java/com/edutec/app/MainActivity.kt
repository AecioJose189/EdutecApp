package com.edutec.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edutec.app.ui.EdutecApp
import com.edutec.app.ui.theme.EdutecTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdutecTheme {
                EdutecApp()
            }
        }
    }
}
