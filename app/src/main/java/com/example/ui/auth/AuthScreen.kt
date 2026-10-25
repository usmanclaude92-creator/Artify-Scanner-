package com.example.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.ui.components.BrandGradient
import kotlinx.coroutines.launch

/**
 * Sign up / log in. Each callback returns an error message, or null on success (the caller then moves on).
 */
@Composable
fun AuthScreen(
    startInSignUp: Boolean,
    configured: Boolean,
    onEmailSignUp: suspend (name: String, email: String, password: String) -> String?,
    onEmailLogin: suspend (email: String, password: String) -> String?,
    onGoogle: suspend () -> String?,
    onProvider: suspend (providerId: String) -> String?,
    onForgotPassword: suspend (email: String) -> String?,
    onGuest: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var signUp by remember { mutableStateOf(startInSignUp) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }

    fun run(block: suspend () -> String?) {
        if (busy) return
        busy = true
        error = null
        info = null
        scope.launch {
            error = block()
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline
    )

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.logo_icon),
                    contentDescription = "Artify Scanner",
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(20.dp))
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (signUp) "Create your account" else "Welcome back",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    if (signUp) "Sign up to keep your scans organized" else "Log in to continue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(24.dp))

            if (!configured) {
                Text(
                    "Sign-in is not set up in this build yet. You can continue as a guest below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(14.dp)
                )
                Spacer(Modifier.height(16.dp))
            }

            if (signUp) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Full name") },
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Default.Email, null) },
                singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                leadingIcon = { Icon(Icons.Default.Lock, null) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, "Show password")
                    }
                },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                singleLine = true, shape = RoundedCornerShape(16.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )

            if (!signUp) {
                Text(
                    "Forgot password?",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = configured && !busy) {
                            run {
                                val err = onForgotPassword(email)
                                if (err == null) info = "Password reset email sent. Check your inbox."
                                err
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            }
            info?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (configured) BrandGradient else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline))
                    .clickable(enabled = configured && !busy) {
                        run {
                            when {
                                email.isBlank() || password.isBlank() -> "Enter your email and password."
                                signUp && name.isBlank() -> "Enter your name."
                                signUp -> onEmailSignUp(name, email, password)
                                else -> onEmailLogin(email, password)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(24.dp))
                else Text(if (signUp) "Create Account" else "Log In", color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp))
            }

            Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                Text(
                    if (signUp) "Already have an account? " else "New to Artify? ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (signUp) "Log in" else "Sign up",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        signUp = !signUp
                        error = null
                        info = null
                    }
                )
            }

            Row(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
                Text(
                    if (signUp) "  or sign up with  " else "  or log in with  ",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterHorizontally)) {
                SocialIconButton("Google", Color(0xFF4285F4), configured && !busy, { run { onGoogle() } }) { GoogleMark(Modifier.size(28.dp)) }
                SocialIconButton("Facebook", Color(0xFF1877F2), configured && !busy, { run { onProvider("facebook.com") } }) {
                    Text("f", color = Color(0xFF4D94FF), fontWeight = FontWeight.Bold, fontSize = 34.sp)
                }
                SocialIconButton("Microsoft", Color(0xFF00A4EF), configured && !busy, { run { onProvider("microsoft.com") } }) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.size(12.dp).background(Color(0xFFF25022)))
                            Box(Modifier.size(12.dp).background(Color(0xFF7FBA00)))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(Modifier.size(12.dp).background(Color(0xFF00A4EF)))
                            Box(Modifier.size(12.dp).background(Color(0xFFFFB900)))
                        }
                    }
                }
                SocialIconButton("Yahoo", Color(0xFF9646FF), configured && !busy, { run { onProvider("yahoo.com") } }) {
                    Text("Y!", color = Color(0xFFB078FF), fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                }
            }

            Text(
                "Continue as guest",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 24.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onGuest)
                    .padding(12.dp)
            )
        }
    }
}


/** Glass tile with a coloured rim and soft glow, holding a social brand mark. */
@Composable
private fun SocialIconButton(label: String, rim: Color, enabled: Boolean, onClick: () -> Unit, mark: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .size(62.dp)
            .shadow(10.dp, shape, ambientColor = rim, spotColor = rim)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, rim, shape)
            .semantics { contentDescription = label }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { mark() }
}

/** Google's four-colour "G", drawn with arcs so no image asset is needed. */
@Composable
private fun GoogleMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        fun arc(color: Color, start: Float) =
            drawArc(color, start, 90f, false, topLeft, arcSize, style = Stroke(width = stroke))
        arc(Color(0xFF4285F4), -45f)
        arc(Color(0xFF34A853), 45f)
        arc(Color(0xFFFBBC05), 135f)
        arc(Color(0xFFEA4335), 225f)
        drawRect(
            Color(0xFF4285F4),
            topLeft = Offset(size.width / 2f, size.height / 2f - stroke / 2f),
            size = Size(size.width / 2f - inset * 0.2f, stroke)
        )
    }
}
