package com.example.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.core.view.doOnLayout
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.camera2.interop.Camera2CameraInfo
import android.hardware.camera2.CameraCharacteristics
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.nio.ByteBuffer
import java.util.concurrent.Executors

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    isFlashOn: Boolean,
    isAutoCapture: Boolean,
    onCaptureReady: (ImageCapture?) -> Unit,
    onAutoCapture: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(5f) }
    var lenses by remember { mutableStateOf<List<LensOption>>(emptyList()) }
    var lensIndex by remember { mutableStateOf(0) }
    var rebind: (() -> Unit)? by remember { mutableStateOf(null) }
    var lensReported by remember { mutableStateOf(false) }
    val autoCaptureEnabled by rememberUpdatedState(isAutoCapture)
    val autoCaptureCallback by rememberUpdatedState(onAutoCapture)
    val stabilityDetector = remember { StabilityDetector() }

    // Flash control update
    LaunchedEffect(isFlashOn, imageCapture) {
        imageCapture?.flashMode = if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            onCaptureReady(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
                        val newZoom = (currentZoom * zoom).coerceIn(minZoom, maxOf(minZoom, minOf(maxZoom, 8f)))
                        camera?.cameraControl?.setZoomRatio(newZoom)
                        zoomRatio = newZoom
                    }
                },
            factory = { ctx ->
                val view = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
                previewView = view

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = view.surfaceProvider
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(if (isFlashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF)
                        .build()

                    imageCapture = capture
                    onCaptureReady(capture)

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also {
                            it.setAnalyzer(cameraExecutor) { image ->
                                val trigger = try {
                                    autoCaptureEnabled && stabilityDetector.shouldCapture(image)
                                } finally {
                                    image.close()
                                }
                                if (trigger) {
                                    ContextCompat.getMainExecutor(ctx).execute { autoCaptureCallback() }
                                }
                            }
                        }
                    val bind = {
                        // The viewport makes the captured photo match what the preview shows
                        val viewPort = view.viewPort
                        if (lenses.isEmpty()) {
                            lenses = buildLensOptions(ctx, cameraProvider)
                            // Start on the widest lens (smallest focal length), as scanning needs the whole page in view
                            lensIndex = 0
                        }
                        val option = lenses.getOrNull(lensIndex)
                        try {
                            cameraProvider.unbindAll()
                            val group = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(capture)
                                .addUseCase(analysis)
                                .apply { viewPort?.let { setViewPort(it) } }
                                .build()
                            val bound = try {
                                cameraProvider.bindToLifecycle(lifecycleOwner, option?.selector ?: CameraSelector.DEFAULT_BACK_CAMERA, group)
                            } catch (e: Exception) {
                                // That lens could not be opened (some phones restrict it): use the main camera
                                android.util.Log.w("Artify", "Lens bind failed: ${option?.label}", e)
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                            }
                            camera = bound
                            val zoomState = bound.cameraInfo.zoomState.value
                            minZoom = zoomState?.minZoomRatio ?: 1f
                            maxZoom = zoomState?.maxZoomRatio ?: 5f

                            // A single logical camera may still reach its ultra-wide lens as zoom < 1x
                            if (lenses.size <= 1 && minZoom < 1f) {
                                lenses = listOf(
                                    LensOption(CameraSelector.DEFAULT_BACK_CAMERA, "${"%.1f".format(minZoom)}x wide", 0f, zoomToMin = true),
                                    LensOption(CameraSelector.DEFAULT_BACK_CAMERA, "1x main", 1f)
                                )
                                lensIndex = 0
                            }
                            val target = if (lenses.getOrNull(lensIndex)?.zoomToMin == true) minZoom else 1f
                            bound.cameraControl.setZoomRatio(target)
                            zoomRatio = target

                            if (!lensReported) {
                                lensReported = true
                                val msg = if (lenses.size > 1) "Lenses found: ${lenses.joinToString { it.label }}. Tap the lens pill to switch."
                                else "Only one camera lens is exposed to apps on this phone (see Settings > Camera diagnostics)."
                                android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            onError("Camera initialization failed: ${e.message}")
                        }
                    }
                    rebind = bind
                    if (view.width > 0 && view.height > 0) bind() else view.doOnLayout { bind() }
                }, ContextCompat.getMainExecutor(ctx))

                view
            }
        )

        // Viewfinder HUD Overlay with animated laser beam
        DocumentScanOverlay(isAutoCapture = isAutoCapture)

        // Lens switcher: cycles through every lens the phone exposes
        if (lenses.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 210.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .clickable {
                        lensIndex = (lensIndex + 1) % lenses.size
                        rebind?.invoke()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Lens: ${lenses.getOrNull(lensIndex)?.label ?: ""}",
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Captures a high-resolution photo from the active ImageCapture use case.
 */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onSuccess: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    val capture = imageCapture ?: run {
        onError("Camera is not ready yet")
        return
    }

    capture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val bitmap = imageProxyToBitmap(imageProxy, rotationDegrees)
                imageProxy.close()
                if (bitmap != null) {
                    onSuccess(bitmap)
                } else {
                    onError("Failed to decode captured frame")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Capture failed: ${exception.message}")
            }
        }
    )
}

/** One selectable lens. [focal] is the 35mm-equivalent focal length in mm (0 = unknown). */
private class LensOption(
    val selector: CameraSelector,
    val label: String,
    val focal: Float,
    val zoomToMin: Boolean = false
)

/**
 * Lists every back lens the phone exposes, widest first: separate camera IDs CameraX knows, the physical
 * lenses behind logical multi-cameras (via CameraX and Camera2), each selectable on its own.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun buildLensOptions(context: Context, provider: ProcessCameraProvider): List<LensOption> {
    val default = listOf(LensOption(CameraSelector.DEFAULT_BACK_CAMERA, "main", 0f))
    return try {
        val mgr = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val backInfos = provider.availableCameraInfos.filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
        if (backInfos.isEmpty()) return default

        val raw = ArrayList<Triple<CameraSelector, Float, String>>() // selector, focal, id
        fun logicalSelector(id: String, physical: String?): CameraSelector {
            val b = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .addCameraFilter { infos ->
                    infos.filter { Camera2CameraInfo.from(it).cameraId == id }.ifEmpty { infos }
                }
            physical?.let { b.setPhysicalCameraId(it) }
            return b.build()
        }

        for (logical in backInfos) {
            val id = Camera2CameraInfo.from(logical).cameraId
            val logicalFocal = LensDiagnostics.equivalentFocal(mgr.getCameraCharacteristics(id)) ?: 0f
            raw.add(Triple(logicalSelector(id, null), logicalFocal, id))
            val physicalIds = LinkedHashSet<String>()
            logical.physicalCameraInfos.forEach { physicalIds.add(Camera2CameraInfo.from(it).cameraId) }
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                physicalIds.addAll(mgr.getCameraCharacteristics(id).physicalCameraIds)
            }
            for (pid in physicalIds) {
                val f = try { LensDiagnostics.equivalentFocal(mgr.getCameraCharacteristics(pid)) } catch (e: Exception) { null } ?: continue
                raw.add(Triple(logicalSelector(id, pid), f, "$id/$pid"))
            }
        }

        // Keep one option per distinct focal length (physical lenses repeat the logical camera's main lens)
        val distinct = raw.filter { it.second > 0f }
            .sortedBy { it.second }
            .fold(ArrayList<Triple<CameraSelector, Float, String>>()) { acc, t ->
                if (acc.none { kotlin.math.abs(it.second - t.second) < 0.12f * t.second }) acc.add(t)
                acc
            }
        android.util.Log.i("Artify", "Lens options: " + raw.joinToString { "${it.third}:${"%.0f".format(it.second)}mm" })
        if (distinct.size < 2) return default

        val mainFocal = raw.first().second.takeIf { it > 0f } ?: distinct.first().second
        distinct.map {
            LensOption(it.first, "${"%.1f".format(it.second / mainFocal)}x (${"%.0f".format(it.second)}mm)", it.second)
        }
    } catch (e: Exception) {
        android.util.Log.w("Artify", "Lens lookup failed", e)
        default
    }
}

private fun imageProxyToBitmap(image: ImageProxy, rotationDegrees: Int): Bitmap? {
    val plane = image.planes[0]
    val buffer: ByteBuffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    var original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

    // With a ViewPort the JPEG is full-size; cropRect marks the area the preview showed
    val crop = image.cropRect
    if (crop.width() in 1 until original.width || crop.height() in 1 until original.height) {
        val x = crop.left.coerceIn(0, original.width - 1)
        val y = crop.top.coerceIn(0, original.height - 1)
        val w = crop.width().coerceAtMost(original.width - x)
        val h = crop.height().coerceAtMost(original.height - y)
        val cropped = Bitmap.createBitmap(original, x, y, w, h)
        if (cropped != original) original.recycle()
        original = cropped
    }

    return if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        if (rotated != original) original.recycle()
        rotated
    } else {
        original
    }
}

@Composable
fun DocumentScanOverlay(isAutoCapture: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserYRatio by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 3.5f * density
        val cornerLength = 36f * density
        val marginX = size.width * 0.08f
        val marginY = size.height * 0.12f
        val docWidth = size.width - (marginX * 2)
        val docHeight = size.height - (marginY * 2)

        val frameColor = if (isAutoCapture) Color(0xFF00E5FF) else Color(0xFF38BDF8)

        // Outer darkened mask
        drawRect(
            color = Color(0x66000000),
            size = size
        )

        // Document transparent window
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(marginX, marginY),
            size = Size(docWidth, docHeight),
            cornerRadius = CornerRadius(16f * density, 16f * density),
            blendMode = androidx.compose.ui.graphics.BlendMode.Clear
        )

        // Boundary border (subtle dashed)
        drawRoundRect(
            color = frameColor.copy(alpha = 0.5f),
            topLeft = Offset(marginX, marginY),
            size = Size(docWidth, docHeight),
            cornerRadius = CornerRadius(16f * density, 16f * density),
            style = Stroke(
                width = 1.5f * density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)
            )
        )

        // 4 Viewfinder Corner Brackets
        // Top-Left
        drawLine(frameColor, Offset(marginX, marginY), Offset(marginX + cornerLength, marginY), strokeWidth)
        drawLine(frameColor, Offset(marginX, marginY), Offset(marginX, marginY + cornerLength), strokeWidth)

        // Top-Right
        drawLine(frameColor, Offset(marginX + docWidth, marginY), Offset(marginX + docWidth - cornerLength, marginY), strokeWidth)
        drawLine(frameColor, Offset(marginX + docWidth, marginY), Offset(marginX + docWidth, marginY + cornerLength), strokeWidth)

        // Bottom-Left
        drawLine(frameColor, Offset(marginX, marginY + docHeight), Offset(marginX + cornerLength, marginY + docHeight), strokeWidth)
        drawLine(frameColor, Offset(marginX, marginY + docHeight), Offset(marginX, marginY + docHeight - cornerLength), strokeWidth)

        // Bottom-Right
        drawLine(frameColor, Offset(marginX + docWidth, marginY + docHeight), Offset(marginX + docWidth - cornerLength, marginY + docHeight), strokeWidth)
        drawLine(frameColor, Offset(marginX + docWidth, marginY + docHeight), Offset(marginX + docWidth, marginY + docHeight - cornerLength), strokeWidth)

        // Laser beam sweep
        val laserY = marginY + (docHeight * laserYRatio)
        drawLine(
            color = Color(0xFF00E5FF),
            start = Offset(marginX + 8f, laserY),
            end = Offset(marginX + docWidth - 8f, laserY),
            strokeWidth = 2.5f * density
        )
    }
}

/**
 * Fires once the viewfinder has been steady on a textured subject (a page, not a blank wall)
 * for [stableMillis], then stays quiet for [cooldownMillis] so the user can turn the page.
 */
class StabilityDetector(
    private val stableMillis: Long = 1200L,
    private val cooldownMillis: Long = 3000L
) {
    private var previous: IntArray? = null
    private var stableSince = 0L
    private var lastTrigger = 0L

    fun shouldCapture(image: ImageProxy): Boolean {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val grid = 24
        val sample = IntArray(grid * grid)
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val x = (gx + 0.5f) / grid * image.width
                val y = (gy + 0.5f) / grid * image.height
                val pos = y.toInt() * rowStride + x.toInt() * pixelStride
                sample[gy * grid + gx] = if (pos < buffer.limit()) buffer.get(pos).toInt() and 0xFF else 0
            }
        }
        val now = System.currentTimeMillis()
        val prev = previous
        previous = sample
        if (prev == null) {
            stableSince = now
            return false
        }
        var diff = 0L
        for (i in sample.indices) diff += kotlin.math.abs(sample[i] - prev[i])
        val meanDiff = diff.toFloat() / sample.size
        val mean = sample.average()
        var variance = 0.0
        for (v in sample) variance += (v - mean) * (v - mean)
        val contrast = kotlin.math.sqrt(variance / sample.size)

        if (meanDiff > 6f) {
            stableSince = now
            return false
        }
        // A page sits in the middle and is brighter than its surroundings; this rejects laps, walls and floors
        var centerSum = 0.0
        var centerN = 0
        var ringSum = 0.0
        var ringN = 0
        for (gy in 0 until grid) {
            for (gx in 0 until grid) {
                val inCenter = gx in 6 until 18 && gy in 6 until 18
                val onRing = gx < 3 || gx >= grid - 3 || gy < 3 || gy >= grid - 3
                if (inCenter) { centerSum += sample[gy * grid + gx]; centerN++ }
                if (onRing) { ringSum += sample[gy * grid + gx]; ringN++ }
            }
        }
        val pageLike = centerSum / centerN - ringSum / ringN > 20.0 && centerSum / centerN > 110.0
        if (!pageLike || contrast < 12.0 || now - lastTrigger < cooldownMillis) return false
        if (now - stableSince >= stableMillis) {
            lastTrigger = now
            stableSince = now
            return true
        }
        return false
    }
}
