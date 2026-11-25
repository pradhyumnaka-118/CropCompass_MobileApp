package com.cropcompass.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.cropcompass.app.navigation.CropCompassApp
import com.cropcompass.app.ui.theme.CropCompassTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CropCompassTheme { CropCompassApp((application as CropCompassApplication).container,intent.getStringExtra("open_route")) } }
    }
}
