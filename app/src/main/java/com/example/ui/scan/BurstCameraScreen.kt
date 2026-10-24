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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
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
    var torchOn by remember { mutableStateOf(false) }
    var autoMode by remember { mutableStateOf(true) }
    var cameraRef by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
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
                cameraRef = provider.bindToLifecycle(context as LifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
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

    val lastPage = ScanningSessionManager.capturedPages.lastOrNull()
    val panel = Color(0xFF121212)

    // Full screen like Google's scanner: hide the system bars while the camera is open
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, previewView) }
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

    Column(Modifier.fillMaxSize().background(panel)) {
        // Top bar: close left, flash right, Done once there are pages
        Row(
            Modifier.fillMaxWidth().height(80.dp).background(Color.Black).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (pageCount > 0) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE8EAED)).clickable { onDone() }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Text("  Done ($pageCount)", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
            IconButton(onClick = {
                torchOn = !torchOn
                cameraRef?.cameraControl?.enableTorch(torchOn)
            }) {
                Icon(if (torchOn) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "Flash", tint = Color.White)
            }
        }

        // Camera preview (4:3, full width) with Google's status pill
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            Text(
                status.message,
                color = Color.White,
                fontSize = 17.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color(0xCC202124)).padding(horizontal = 22.dp, vertical = 11.dp)
            )
        }

        Spacer(Modifier.height(26.dp))

        // Page thumbnail, shutter, undo
        Box(Modifier.fillMaxWidth().height(76.dp)) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(start = 70.dp).size(54.dp)
                    .border(2.dp, Color.White, CircleShape).padding(4.dp).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (lastPage != null) {
                    AsyncImage(model = File(lastPage.localImagePath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Text(
                        "$pageCount", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color(0x99000000), CircleShape).padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
            Box(
                Modifier.align(Alignment.Center).size(76.dp).border(3.dp, Color.White, CircleShape).padding(6.dp).clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = status.phase != BurstTrigger.Phase.CAPTURING) { takePage() }
            )
            Box(
                Modifier.align(Alignment.CenterEnd).padding(end = 70.dp).size(54.dp)
                    .border(2.dp, if (pageCount > 0) Color.White else Color(0x55FFFFFF), CircleShape).clip(CircleShape)
                    .clickable(enabled = pageCount > 0) { ScanningSessionManager.removeLastPage() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo last page",
                    tint = if (pageCount > 0) Color.White else Color(0x55FFFFFF), modifier = Modifier.size(24.dp)
                )
            }
        }

        // Manual | Auto capture selector
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 18.dp)
                .clip(RoundedCornerShape(24.dp)).background(Color(0xFF2B2B2B))
        ) {
            listOf(false to "Manual", true to "Auto capture").forEach { (auto, label) ->
                val selected = autoMode == auto
                Text(
                    label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (selected) Color(0xFFE8EAED) else Color.Transparent)
                        .clickable { autoMode = auto; trigger.enabled = auto }
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                )
            }
        }

        Text(
            "Artify Scanner will have access only to the images that you scan",
            color = Color.White,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 12.dp)
        )
    }
}
