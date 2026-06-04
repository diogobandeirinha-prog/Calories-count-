package com.caloriescount.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.caloriescount.app.ui.AppRoot
import com.caloriescount.app.ui.theme.CaloriesCountTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CaloriesCountTheme {
                AppRoot()
            }
        }
    }
}
