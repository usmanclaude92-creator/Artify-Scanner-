package com.example.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.DisposableEffect
import com.example.camera.BurstScanner
import androidx.compose.material.icons.filled.BurstMode
import com.example.ui.review.CropDialog
import com.example.data.model.NormalizedCornerPoints
import com.example.data.model.CapturedPage
import android.graphics.PointF
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.example.imageprocessing.ImageProcessor
import androidx.activity.result.IntentSenderRequest
import com.example.data.model.ScanFilter
import androidx.compose.material.icons.filled.DocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.camera.CameraPreviewView
import com.example.camera.ScanningSessionManager
import com.example.camera.takePhoto
import com.example.data.model.ScanMode
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraScannerScreen(
    onNavigateBack: () -> Unit,
    onFinishScanning: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission is required to scan documents", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Gallery image picker
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                for (uri in uris) {
                    ScanningSessionManager.addPageFromUri(context, uri)
                }
            }
        }
    }

    // Google's ML Kit Document Scanner: live edge detection, auto-capture, crop and enhancement
    val smartScanLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        if (result.resultCode == android.app.Activity.RESULT_OK && scan != null) {
            scope.launch {
                for (page in scan.pages.orEmpty()) {
                    ScanningSessionManager.addPageFromUri(context, page.imageUri, ScanFilter.ORIGINAL, autoCrop = false)
                }
            }
        }
    }

    fun startSmartScan() {
        val activity = context as? android.app.Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                smartScanLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                Toast.makeText(context, "Smart Scan unavailable: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    var activeImageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var pendingCropPage by remember { mutableStateOf<CapturedPage?>(null) }
    var pendingCropGuess by remember { mutableStateOf<NormalizedCornerPoints?>(null) }

    pendingCropPage?.let { page ->
        CropDialog(
            originalImagePath = page.originalImagePath,
            initial = pendingCropGuess ?: NormalizedCornerPoints(
                PointF(0.04f, 0.04f), PointF(0.96f, 0.04f), PointF(0.96f, 0.96f), PointF(0.04f, 0.96f)
            ),
            onDismiss = {
                pendingCropPage = null
                if (ScanningSessionManager.scanMode.value == ScanMode.SINGLE_PAGE) onFinishScanning()
            },
            onConfirm = { corners ->
                pendingCropPage = null
                ScanningSessionManager.updatePageCrop(context, scope, page.id, corners)
                if (ScanningSessionManager.scanMode.value == ScanMode.SINGLE_PAGE) onFinishScanning()
            }
        )
    }

    fun captureFromCamera() {
        if (ScanningSessionManager.isProcessingCapture.value) return
        takePhoto(
            context = context,
            imageCapture = activeImageCapture,
            onSuccess = { photo ->
                scope.launch {
                    // The whole sensor frame is kept (widest field of view); detection finds the page in it
                    val detected = withContext(Dispatchers.Default) { ImageProcessor.detectDocumentCornersOrNull(photo) }
                    val page = ScanningSessionManager.addPageFromBitmap(context, photo, autoCrop = false, corners = detected)
                    // Detection only pre-places the corners; the user confirms or drags them
                    if (detected == null || ScanningSessionManager.confirmCropAfterCapture.value) {
                        pendingCropPage = page
                        pendingCropGuess = detected
                    } else if (ScanningSessionManager.scanMode.value == ScanMode.SINGLE_PAGE) {
                        onFinishScanning()
                    }
                }
            },
            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
        )
    }

    // Burst Scan: hands-free capture, page after page, until the user finishes
    val burst = remember { BurstScanner() }
    val burstMode = ScanningSessionManager.scanMode.value == ScanMode.BURST_SCAN

    fun captureBurst(quad: NormalizedCornerPoints) {
        takePhoto(
            context = context,
            imageCapture = activeImageCapture,
            onSuccess = { photo ->
                scope.launch {
                    // Sharpen the live outline with a detection on the full-resolution photo when they agree
                    val refined = withContext(Dispatchers.Default) { ImageProcessor.detectDocumentCornersOrNull(photo) }
                    val corners = if (refined != null && cornerShift(refined, quad) < 0.08f) refined else quad
                    ScanningSessionManager.addPageFromBitmap(context, photo, autoCrop = false, corners = corners)
                    burst.captureFinished(success = true)
                }
            },
            onError = { err ->
                Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                burst.captureFinished(success = false)
            }
        )
    }

    DisposableEffect(burst) {
        burst.onCaptureRequested = { quad -> captureBurst(quad) }
        onDispose { burst.onCaptureRequested = null }
    }
    LaunchedEffect(burstMode) { burst.reset() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // Live CameraX Preview
            CameraPreviewView(
                isFlashOn = ScanningSessionManager.isFlashOn.value,
                isAutoCapture = ScanningSessionManager.isAutoCapture.value,
                burst = if (burstMode) burst else null,
                onCaptureReady = { activeImageCapture = it },
                onAutoCapture = { captureFromCamera() },
                onError = { err ->
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            )
        } else {
            // Permission request fallback card
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Camera Access Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Grant camera permission to capture document pages continuously.",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Grant Permission")
                }
            }
        }

        // TOP HUD BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .testTag("camera_back_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Mode Selector Pill
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = ScanningSessionManager.scanMode.value.title,
                        color = Color(0xFF00E5FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Burst Scan toggle (hands-free capture, page after page)
                IconButton(
                    onClick = {
                        ScanningSessionManager.scanMode.value =
                            if (burstMode) ScanMode.BATCH_DOCUMENTS else ScanMode.BURST_SCAN
                        Toast.makeText(
                            context,
                            if (burstMode) "Burst Scan off" else "Burst Scan on: hold each page steady, then turn it",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(if (burstMode) Color(0xFF10B981) else Color.Black.copy(alpha = 0.5f))
                        .testTag("camera_burst_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.BurstMode,
                        contentDescription = "Burst Scan",
                        tint = Color.White
                    )
                }

                // Smart Scan (Google document scanner with accurate edge detection)
                IconButton(
                    onClick = { startSmartScan() },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7))
                        .testTag("camera_smart_scan")
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = "Smart Scan",
                        tint = Color.White
                    )
                }

                // Flash Toggle
                IconButton(
                    onClick = {
                        ScanningSessionManager.isFlashOn.value = !ScanningSessionManager.isFlashOn.value
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .testTag("camera_flash_toggle")
                ) {
                    Icon(
                        imageVector = if (ScanningSessionManager.isFlashOn.value) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Flash",
                        tint = if (ScanningSessionManager.isFlashOn.value) Color(0xFFFBBF24) else Color.White
                    )
                }

                // Auto/Manual Capture Toggle
                IconButton(
                    onClick = {
                        ScanningSessionManager.isAutoCapture.value = !ScanningSessionManager.isAutoCapture.value
                        val modeName = if (ScanningSessionManager.isAutoCapture.value) "Auto-Capture ON" else "Manual Shutter Mode"
                        Toast.makeText(context, modeName, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (ScanningSessionManager.isAutoCapture.value)
                                Color(0xFF00E5FF).copy(alpha = 0.3f)
                            else
                                Color.Black.copy(alpha = 0.5f)
                        )
                        .testTag("camera_autocapture_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlashAuto,
                        contentDescription = "Auto Capture",
                        tint = if (ScanningSessionManager.isAutoCapture.value) Color(0xFF00E5FF) else Color.White
                    )
                }
            }
        }

        // Processing / Status Floating Toast
        AnimatedVisibility(
            visible = ScanningSessionManager.isProcessingCapture.value,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 64.dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = ScanningSessionManager.statusMessage.value ?: "Processing page...",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // BOTTOM HUD CONTROLS (Floating over camera preview)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black)
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Live Thumbnail Strip of Captured Pages
            if (ScanningSessionManager.capturedPages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Page Count Badge
                    Surface(
                        color = Color(0xFF0284C7),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${ScanningSessionManager.capturedPages.size} ${if (ScanningSessionManager.capturedPages.size == 1) "Page" else "Pages"}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    // Page Thumbnails
                    ScanningSessionManager.capturedPages.forEachIndexed { idx, page ->
                        Box(
                            modifier = Modifier
                                .size(width = 46.dp, height = 60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.5.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .background(Color.DarkGray)
                        ) {
                            AsyncImage(
                                model = File(page.localImagePath),
                                contentDescription = "Page thumbnail",
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop
                            )
                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(2.dp),
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "${idx + 1}",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Single | Multi-Page selector
            if (ScanningSessionManager.scanMode.value == ScanMode.BATCH_DOCUMENTS ||
                ScanningSessionManager.scanMode.value == ScanMode.SINGLE_PAGE
            ) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(3.dp)
                ) {
                    listOf(ScanMode.SINGLE_PAGE to "Single", ScanMode.BATCH_DOCUMENTS to "Multi-Page").forEach { (mode, label) ->
                        val selected = ScanningSessionManager.scanMode.value == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(19.dp))
                                .background(if (selected) Color(0xFF1E6BFF) else Color.Transparent)
                                .clickable { ScanningSessionManager.scanMode.value = mode }
                                .padding(horizontal = 22.dp, vertical = 8.dp)
                        ) {
                            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }

            // Primary Control Row: Gallery Import, Shutter Button, Undo/Finish
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Gallery import
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .testTag("gallery_import_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Import Gallery",
                            tint = Color.White
                        )
                    }
                    Text("Gallery", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                }

                // Shutter Button (Prominent circular trigger)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .testTag("shutter_button")
                        .clickable { captureFromCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF2AA5FF), Color(0xFF1E6BFF))))
                    )
                }

                // Undo / Finish Scanning
                if (ScanningSessionManager.capturedPages.isNotEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { ScanningSessionManager.removeLastPage() },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .testTag("undo_page_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo Last Page",
                                tint = Color.White
                            )
                        }
                        Text("Undo", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { ScanningSessionManager.isFlashOn.value = !ScanningSessionManager.isFlashOn.value },
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = if (ScanningSessionManager.isFlashOn.value) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (ScanningSessionManager.isFlashOn.value) Color(0xFFFBBF24) else Color.White
                            )
                        }
                        Text("Flash", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                }
            }

            // Finish Scanning Action Button
            if (ScanningSessionManager.capturedPages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onFinishScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("finish_scanning_button"),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E6BFF)
                    )
                ) {
                    Text(
                        text = "Finish Scanning (${ScanningSessionManager.capturedPages.size} ${if (ScanningSessionManager.capturedPages.size == 1) "Page" else "Pages"})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun cornerShift(a: NormalizedCornerPoints, b: NormalizedCornerPoints): Float {
    fun d(p: PointF, q: PointF) = maxOf(kotlin.math.abs(p.x - q.x), kotlin.math.abs(p.y - q.y))
    return maxOf(
        d(a.topLeft, b.topLeft), d(a.topRight, b.topRight),
        d(a.bottomRight, b.bottomRight), d(a.bottomLeft, b.bottomLeft)
    )
}
