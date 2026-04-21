package com.notetaker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.notetaker.ui.NotetakerNavGraph
import com.notetaker.ui.theme.NotetakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as NotetakerApp).container.noteRepository
        setContent {
            NotetakerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NotetakerNavGraph(repository = repository)
                }
            }
        }
    }
}
