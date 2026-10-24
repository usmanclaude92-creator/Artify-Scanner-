package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.auth.AuthManager
import com.example.auth.NotificationCenter
import com.example.ui.theme.ThemeController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Circular profile picture: chosen photo, Google photo, or initials on the brand gradient. */
@Composable
fun UserAvatar(size: Dp, modifier: Modifier = Modifier, fontSize: Int = 14) {
    val context = LocalContext.current
    val profile = AuthManager.profile.value
    val version = AuthManager.photoVersion.intValue
    val local = remember(version) { AuthManager.photoFile(context) }
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        when {
            local != null -> AsyncImage(
                model = local, contentDescription = "Profile picture",
                modifier = Modifier.size(size), contentScale = ContentScale.Crop
            )
            profile?.photoUrl != null -> AsyncImage(
                model = profile.photoUrl, contentDescription = "Profile picture",
                modifier = Modifier.size(size), contentScale = ContentScale.Crop
            )
            else -> Text(
                text = (profile?.name?.trim()?.take(1) ?: "A").uppercase(),
                color = Color.White, fontSize = fontSize.sp, fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Top bar of the main screens: brand on the left; notifications, light/dark switch and profile on the right. */
@Composable
fun AppHeader(onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showNotifications by remember { mutableStateOf(false) }
    val isDark = when (ThemeController.mode.intValue) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    if (showNotifications) NotificationsDialog(onDismiss = { showNotifications = false })

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.logo_icon),
            contentDescription = "Artify Scanner logo",
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
        )
        Spacer(Modifier.width(10.dp))
        Text("Artify Scanner", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)

        // Notifications
        Box {
            IconButton(onClick = {
                showNotifications = true
                NotificationCenter.markAllRead(context)
            }) {
                Icon(
                    imageVector = if (NotificationCenter.unread.intValue > 0) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                    contentDescription = "Notifications",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            if (NotificationCenter.unread.intValue > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 10.dp, end = 10.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                )
            }
        }

        // Light / dark switch
        IconButton(onClick = { ThemeController.set(context, if (isDark) 1 else 2) }) {
            Icon(
                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                contentDescription = if (isDark) "Switch to light mode" else "Switch to dark mode",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        UserAvatar(size = 36.dp, modifier = Modifier.clickable(onClick = onOpenProfile))
    }
}

@Composable
private fun NotificationsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notifications") },
        text = {
            if (NotificationCenter.items.isEmpty()) {
                Text("You're all caught up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(NotificationCenter.items) { item ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(item.time)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (NotificationCenter.items.isNotEmpty()) {
                TextButton(onClick = { NotificationCenter.clear(context) }) { Text("Clear all") }
            }
        }
    )
}
