package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mfb.MfbApplication
import com.example.mfb.presentation.navigation.MfbAppRoot
import com.example.mfb.presentation.viewmodel.MfbViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MfbViewModel by viewModels {
        val appContainer = (application as MfbApplication).container
        MfbViewModel.provideFactory(appContainer)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MfbAppRoot(viewModel = viewModel)
                }
            }
        }
    }
}
