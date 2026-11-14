package com.chewplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import com.chewplay.ui.AppRoot
import com.chewplay.ui.ChewPlayTheme

class MainActivity : ComponentActivity() {

    private lateinit var controller: AppController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Видео на весь экран, включая область под статус-баром.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        controller = AppController(this)
        setContent {
            ChewPlayTheme {
                AppRoot(controller)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.onStart()
    }

    override fun onStop() {
        controller.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }
}
