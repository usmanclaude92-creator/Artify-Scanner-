package com.example.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.media.ExifInterface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.camera.BurstTrigger
import com.example.camera.ScanningSessionManager
import com.example.data.model.ScanFilter
import com.example.imageprocessing.ImageProcessor
import com.example.ui.theme.BrandCyan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Burst Scan camera: pages are captured one after another without touching the screen. Hold a page
 * steady and it is taken; turn the page and the next one is taken, until the user taps Done.
 * (Google's scanner stops after one auto-captured page, so this mode uses its own camera.)
 */
@Composable
fun BurstCameraScreen(onDone: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    if (!hasPermission) {
        Column(
            Modifier.fillMaxSize().background(Color(0xFF030B1C)).statusBarsPadding().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Camera access is needed for Burst Scan", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Box(
                Modifier.padding(top = 20.dp).clip(RoundedCornerShape(24.dp)).background(BrandCyan)
                    .clickable { permissionLauncher.launch(Manifest.permission.CAMERA) }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) { Text("Allow camera", color = Color.Black, fontWeight = FontWeight.SemiBold) }
            Box(Modifier.padding(top = 12.dp).clickable { onClose() }.padding(12.dp)) { Text("Cancel", color = Color(0xFFB9CBEF)) }
        }
        return
    }

    val trigger = remember { BurstTrigger() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val pageCount = ScanningSessionManager.capturedPages.size
    val status = trigger.status.value

    fun takePage() {
        val file = File(context.cacheDir, "burst_${UUID.randomUUID()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            try {
                                val bitmap = ImageProcessor.decodeSampledBitmap(file) ?: return@withContext false
                                val degrees = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                                    else -> 0
                                }
                                ScanningSessionManager.addPageFromBitmap(context, ImageProcessor.rotateBitmap(bitmap, degrees), ScanFilter.MAGIC_COLOR)
                                true
                            } catch (e: Exception) {
                                android.util.Log.w("Artify", "Burst page failed", e)
                                false
                            } finally {
                                file.delete()
                            }
                        }
                        trigger.captureFinished(ok)
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    file.delete()
                    trigger.captureFinished(false)
                }
            }
        )
    }

    DisposableEffect(Unit) {
        trigger.reset()
        trigger.onCaptureRequested = { takePage() }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { a -> a.setAnalyzer(analysisExecutor) { image -> try { trigger.analyze(image) } finally { image.close() } } }
                provider.unbindAll()
                provider.bindToLifecycle(context as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            } catch (e: Exception) {
                Toast.makeText(context, "Camera unavailable: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            trigger.onCaptureRequested = null
            try { ProcessCameraProvider.getInstance(context).get().unbindAll() } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        ScanningFrame(Modifier.align(Alignment.Center).padding(bottom = 40.dp).fillMaxWidth(0.82f).aspectRatio(0.72f))

        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp).align(Alignment.TopStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            Text("Burst scan", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                status.message,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0x99000000)).padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    Text("$pageCount", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                // Manual shutter as a fallback
                Box(
                    Modifier.size(72.dp).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                        .background(if (status.progress > 0f) BrandCyan else Color.White)
                        .clickable(enabled = status.phase != BurstTrigger.Phase.CAPTURING) { takePage() }
                )
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(if (pageCount > 0) BrandCyan else Color(0x66FFFFFF))
                        .clickable(enabled = pageCount > 0) { onDone() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.Black) }
            }
        }
    }
}
