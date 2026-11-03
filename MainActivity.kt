package com.example.customdialer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.customdialer.ui.DialerScreen
import com.example.customdialer.ui.DialerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: DialerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    DialerScreen(viewModel = viewModel)
                }
            }
        }
    }
}
