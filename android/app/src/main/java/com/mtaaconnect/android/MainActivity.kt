package com.mtaaconnect.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mtaaconnect.android.ui.AppViewModel
import com.mtaaconnect.android.ui.MtaaApp
import com.mtaaconnect.android.ui.MtaaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MtaaTheme {
                val appViewModel: AppViewModel = viewModel()
                MtaaApp(appViewModel)
            }
        }
    }
}
