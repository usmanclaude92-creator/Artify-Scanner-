package com.example.ui.review

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectDragGestures
import com.example.data.model.NormalizedCornerPoints
import com.example.imageprocessing.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Full-screen editor to drag the four document corners over the original capture. */
@Composable
fun CropDialog(
    originalImagePath: String,
    initial: NormalizedCornerPoints,
    onDismiss: () -> Unit,
    onConfirm: (NormalizedCornerPoints) -> Unit
) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, originalImagePath) {
        value = withContext(Dispatchers.IO) {
            ImageProcessor.decodeSampledBitmap(File(originalImagePath), 1600, 1600)
        }
    }

    // Normalized corners in order: top-left, top-right, bottom-right, bottom-left
    val points = remember(initial) {
        mutableStateListOf(
            Offset(initial.topLeft.x, initial.topLeft.y),
            Offset(initial.topRight.x, initial.topRight.y),
            Offset(initial.bottomRight.x, initial.bottomRight.y),
            Offset(initial.bottomLeft.x, initial.bottomLeft.y)
        )
    }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeCorner by remember { mutableIntStateOf(-1) }
    val grabRadiusPx = with(LocalDensity.current) { 48.dp.toPx() }
    val handleRadiusPx = with(LocalDensity.current) { 11.dp.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Drag the corners to fit the page",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val bmp = bitmap
                if (bmp != null) {
                    // Image is letterboxed ("fit") inside the canvas; corners are relative to the image
                    fun fitRect(): Pair<Offset, Size> {
                        val scale = minOf(canvasSize.width.toFloat() / bmp.width, canvasSize.height.toFloat() / bmp.height)
                        val w = bmp.width * scale
                        val h = bmp.height * scale
                        return Offset((canvasSize.width - w) / 2f, (canvasSize.height - h) / 2f) to Size(w, h)
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(bmp) {
                                detectDragGestures(
                                    onDragStart = { start ->
                                        val (origin, size) = fitRect()
                                        activeCorner = points.indices.minByOrNull { i ->
                                            val p = origin + Offset(points[i].x * size.width, points[i].y * size.height)
                                            (p - start).getDistance()
                                        }?.takeIf { i ->
                                            val p = origin + Offset(points[i].x * size.width, points[i].y * size.height)
                                            (p - start).getDistance() <= grabRadiusPx
                                        } ?: -1
                                    },
                                    onDragEnd = { activeCorner = -1 },
                                    onDragCancel = { activeCorner = -1 }
                                ) { change, drag ->
                                    change.consume()
                                    val i = activeCorner
                                    if (i >= 0) {
                                        val (_, size) = fitRect()
                                        val p = points[i]
                                        points[i] = Offset(
                                            (p.x + drag.x / size.width).coerceIn(0f, 1f),
                                            (p.y + drag.y / size.height).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                    ) {
                        val (origin, size) = fitRect()
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(origin.x.toInt(), origin.y.toInt()),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt())
                        )
                        val screen = points.map { origin + Offset(it.x * size.width, it.y * size.height) }
                        val outline = Path().apply {
                            moveTo(screen[0].x, screen[0].y)
                            for (i in 1..3) lineTo(screen[i].x, screen[i].y)
                            close()
                        }
                        drawPath(outline, Color(0x3338BDF8))
                        drawPath(outline, Color(0xFF38BDF8), style = Stroke(width = 3f))
                        screen.forEachIndexed { i, p ->
                            drawCircle(Color.White, handleRadiusPx + 3f, p)
                            drawCircle(if (i == activeCorner) Color(0xFF00E5FF) else Color(0xFF0284C7), handleRadiusPx, p)
                        }
                    }
                } else {
                    Text("Loading...", color = Color.White, modifier = Modifier.padding(16.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                OutlinedButton(onClick = {
                    val full = listOf(Offset(0f, 0f), Offset(1f, 0f), Offset(1f, 1f), Offset(0f, 1f))
                    full.forEachIndexed { i, o -> points[i] = o }
                }) { Text("Full page") }
                Button(onClick = {
                    onConfirm(
                        NormalizedCornerPoints(
                            topLeft = PointF(points[0].x, points[0].y),
                            topRight = PointF(points[1].x, points[1].y),
                            bottomRight = PointF(points[2].x, points[2].y),
                            bottomLeft = PointF(points[3].x, points[3].y)
                        )
                    )
                }) { Text("Apply") }
            }
        }
    }
}
