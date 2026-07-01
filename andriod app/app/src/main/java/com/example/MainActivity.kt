package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.EcoPulseApp
import com.example.ui.EcoPulseViewModel
import com.example.ui.theme.EcoPulseTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EcoPulseViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EcoPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.ui.graphics.Color(0xFFFBFDF8)
                ) {
                    EcoPulseApp(viewModel)
                }
            }
        }
    }
}
