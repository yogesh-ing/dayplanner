package com.yourname.dayplanner.alarm

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val title = intent.getStringExtra(AlarmScheduler.EXTRA_TITLE) ?: "Task"
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { AlarmScreen(title) { dismiss() } }
            }
        }
    }

    private fun dismiss() {
        startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
        finish()
    }
}

@Composable
private fun AlarmScreen(title: String, onStop: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("TIME TO START", fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Text(title.uppercase(), fontSize = 34.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(48.dp))
        Button(onClick = onStop, modifier = Modifier.fillMaxWidth().height(64.dp)) {
            Text("STOP", fontSize = 20.sp)
        }
    }
}