package com.example.ui.security

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.security.AppLock

@Composable
fun LockScreen(activity: FragmentActivity) {
    fun unlock() = AppLock.authenticate(
        activity = activity,
        onSuccess = { AppLock.locked.value = false },
        onFailure = { msg -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
    )

    LaunchedEffect(Unit) { unlock() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("Artify Scanner is locked", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { unlock() }) { Text("Unlock") }
        }
    }
}
