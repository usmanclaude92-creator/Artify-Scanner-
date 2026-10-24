package com.example.ui.scan

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.ScanningSessionManager
import com.example.data.model.ScanMode
import com.example.ui.components.GradientButton
import com.example.ui.theme.BrandBlue
import com.example.ui.theme.BrandCyan

/** Pages Google's scanner may return in one session for each mode. */
fun maxPagesFor(mode: ScanMode): Int = when (mode) {
    ScanMode.SINGLE_PAGE -> 1
    ScanMode.ID_CARD -> 2
    ScanMode.RECEIPT_INVOICE -> 10
    ScanMode.BURST_SCAN -> 100
    ScanMode.BATCH_DOCUMENTS -> 30
}

/** Dotted border, corner brackets and a glowing line sweeping up and down over a faint page. */
@Composable
fun ScanningFrame(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val sweep = transition.animateFloat(
        initialValue = 0.04f, targetValue = 0.96f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    val dash = transition.animateFloat(
        initialValue = 0f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "dash"
    )

    Canvas(modifier) {
        val d = density
        val w = size.width
        val h = size.height
        val radius = CornerRadius(18f * d, 18f * d)

        // Faint page with text lines
        drawRoundRect(Color.White.copy(alpha = 0.06f), size = Size(w, h), cornerRadius = radius)
        var y = h * 0.12f
        var i = 0
        while (y < h * 0.9f) {
            val lineW = if (i % 4 == 3) w * 0.45f else w * (0.72f - (i % 3) * 0.08f)
            drawRoundRect(
                Color.White.copy(alpha = 0.10f),
                topLeft = Offset(w * 0.12f, y),
                size = Size(lineW, 5f * d),
                cornerRadius = CornerRadius(3f * d, 3f * d)
            )
            y += 22f * d
            i++
        }

        // Moving dotted border
        drawRoundRect(
            color = BrandCyan.copy(alpha = 0.75f),
            size = Size(w, h),
            cornerRadius = radius,
            style = Stroke(width = 2f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * d, 8f * d), dash.value * d))
        )

        // Corner brackets
        val len = 34f * d
        val sw = 5f * d
        val c = BrandCyan
        fun corner(x: Float, yy: Float, dx: Float, dy: Float) {
            drawLine(c, Offset(x, yy), Offset(x + dx * len, yy), sw, StrokeCap.Round)
            drawLine(c, Offset(x, yy), Offset(x, yy + dy * len), sw, StrokeCap.Round)
        }
        corner(0f, 0f, 1f, 1f)
        corner(w, 0f, -1f, 1f)
        corner(0f, h, 1f, -1f)
        corner(w, h, -1f, -1f)

        // Scan line with glow
        val ly = h * sweep.value
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, BrandCyan.copy(alpha = 0.28f)),
                startY = ly - 70f * d, endY = ly
            ),
            topLeft = Offset(0f, (ly - 70f * d).coerceAtLeast(0f)),
            size = Size(w, (70f * d).coerceAtMost(ly))
        )
        drawLine(
            brush = Brush.horizontalGradient(listOf(Color.Transparent, BrandCyan, Color.White, BrandCyan, Color.Transparent)),
            start = Offset(0f, ly), end = Offset(w, ly), strokeWidth = 3.5f * d
        )
    }
}
