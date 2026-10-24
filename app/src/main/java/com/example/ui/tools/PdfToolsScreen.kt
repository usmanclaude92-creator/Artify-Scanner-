package com.example.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.camera.ScanningSessionManager
import com.example.data.model.DocumentCategory
import com.example.data.model.PdfExportConfig
import com.example.data.model.PdfPageSize
import com.example.data.model.PdfQuality
import com.example.data.model.ScanMode
import com.example.data.repository.DocumentRepository
import com.example.imageprocessing.ImageProcessor
import com.example.pdf.PdfEngine
import com.example.pdf.PdfShareUtil
import com.example.ui.components.ProcessingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfToolsScreen(
    repository: DocumentRepository,
    onOpenDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allDocs by repository.getAllDocuments().collectAsState(initial = emptyList())

    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("") }

    // Dialog states
    var showMergeDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }

    // Images to PDF Picker
    val imagesToPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isProcessing = true
            processingMsg = "Converting ${uris.size} images to PDF..."
            scope.launch {
                ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
                for (uri in uris) {
                    ScanningSessionManager.addPageFromUri(context, uri)
                }
                val pages = ScanningSessionManager.capturedPages.toList()
                if (pages.isNotEmpty()) {
                    val config = PdfExportConfig(
                        filename = "Images_Converted_${System.currentTimeMillis()}",
                        pageSize = PdfPageSize.A4,
                        quality = PdfQuality.HIGH
                    )
                    val doc = repository.saveScanSessionOrNull(
                        title = config.filename,
                        category = DocumentCategory.ALL,
                        pages = pages,
                        pdfConfig = config
                    )
                    isProcessing = false
                    if (doc != null) {
                        Toast.makeText(context, "Images converted to PDF successfully!", Toast.LENGTH_SHORT).show()
                        onOpenDocument(doc.id)
                    } else {
                        Toast.makeText(context, "Could not create PDF. Please try again.", Toast.LENGTH_LONG).show()
                    }
                } else {
                    isProcessing = false
                }
            }
        }
    }

    if (isProcessing) {
        ProcessingDialog(message = processingMsg)
    }

    // MERGE PDFs DIALOG
    if (showMergeDialog) {
        val selectedDocIds = remember { mutableStateListOf<String>() }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text("Merge Documents into One PDF") },
            text = {
                Column {
                    Text(
                        text = "Select 2 or more documents to combine into a single PDF:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    allDocs.forEach { doc ->
                        val isSelected = selectedDocIds.contains(doc.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isSelected) selectedDocIds.remove(doc.id)
                                    else selectedDocIds.add(doc.id)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = isSelected,
                                onCheckedChange = {
                                    if (it) selectedDocIds.add(doc.id)
                                    else selectedDocIds.remove(doc.id)
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(doc.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text("${doc.pageCount} pages", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedDocIds.size >= 2) {
                            showMergeDialog = false
                            isProcessing = true
                            processingMsg = "Merging ${selectedDocIds.size} documents..."
                            scope.launch {
                                val combinedPages = mutableListOf<com.example.data.model.CapturedPage>()
                                for (id in selectedDocIds) {
                                    combinedPages.addAll(repository.getPagesForDocument(id))
                                }
                                val config = PdfExportConfig(
                                    filename = "Merged_Document_${System.currentTimeMillis()}",
                                    pageSize = PdfPageSize.A4,
                                    quality = PdfQuality.HIGH
                                )
                                val mergedDoc = repository.saveScanSessionOrNull(
                                    title = config.filename,
                                    category = DocumentCategory.ALL,
                                    pages = combinedPages,
                                    pdfConfig = config
                                )
                                isProcessing = false
                                if (mergedDoc != null) {
                                    Toast.makeText(context, "Merged into 1 PDF successfully!", Toast.LENGTH_SHORT).show()
                                    onOpenDocument(mergedDoc.id)
                                } else {
                                    Toast.makeText(context, "Could not create PDF. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Please select at least 2 documents", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Merge (${selectedDocIds.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // SIGNATURE DRAWING PAD DIALOG
    if (showSignatureDialog) {
        SignaturePadDialog(
            onDismiss = { showSignatureDialog = false },
            onSave = { signatureBitmap ->
                showSignatureDialog = false
                scope.launch(Dispatchers.IO) {
                    val sigFile = File(repository.documentsDir, "signature_${System.currentTimeMillis()}.png")
                    ImageProcessor.saveBitmapToFile(signatureBitmap, sigFile, format = Bitmap.CompressFormat.PNG)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Signature saved to document assets!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PDF Toolkit",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    text = "Professional PDF Operations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                PdfToolCard(
                    title = "Merge Multiple PDFs",
                    description = "Combine 2 or more scanned documents into one unified, multi-page PDF.",
                    icon = Icons.Default.CallMerge,
                    iconTint = androidx.compose.ui.graphics.Color(0xFF0284C7),
                    onClick = { showMergeDialog = true }
                )
            }

            item {
                PdfToolCard(
                    title = "Images to PDF",
                    description = "Batch select multiple photos from gallery and convert into a clean PDF document.",
                    icon = Icons.Default.Image,
                    iconTint = androidx.compose.ui.graphics.Color(0xFF10B981),
                    onClick = { imagesToPdfLauncher.launch("image/*") }
                )
            }

            item {
                PdfToolCard(
                    title = "Digital Signature Pad",
                    description = "Draw and save your handwritten signature for signing contracts and invoices.",
                    icon = Icons.Default.Draw,
                    iconTint = androidx.compose.ui.graphics.Color(0xFF8B5CF6),
                    onClick = { showSignatureDialog = true }
                )
            }

            item {
                PdfToolCard(
                    title = "Compress PDF",
                    description = "Reduce file size with smart JPEG downsampling while preserving clear, readable text.",
                    icon = Icons.Default.Straighten,
                    iconTint = androidx.compose.ui.graphics.Color(0xFFF59E0B),
                    onClick = {
                        val first = allDocs.firstOrNull()
                        if (first != null) {
                            isProcessing = true
                            processingMsg = "Compressing ${first.title}..."
                            scope.launch {
                                val pages = repository.getPagesForDocument(first.id)
                                val config = PdfExportConfig(
                                    filename = "${first.title}_Compressed",
                                    pageSize = PdfPageSize.A4,
                                    quality = PdfQuality.COMPRESSED
                                )
                                val compDoc = repository.saveScanSessionOrNull(
                                    title = config.filename,
                                    category = first.category,
                                    pages = pages,
                                    pdfConfig = config
                                )
                                isProcessing = false
                                if (compDoc != null) {
                                    Toast.makeText(context, "Compressed PDF created!", Toast.LENGTH_SHORT).show()
                                    onOpenDocument(compDoc.id)
                                } else {
                                    Toast.makeText(context, "Could not create PDF. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "No documents available to compress", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            item {
                PdfToolCard(
                    title = "Add Watermark",
                    description = "Add 'CONFIDENTIAL', 'ORIGINAL', or custom watermarks across all document pages.",
                    icon = Icons.Default.BrandingWatermark,
                    iconTint = androidx.compose.ui.graphics.Color(0xFFEC4899),
                    onClick = {
                        val first = allDocs.firstOrNull()
                        if (first != null) {
                            isProcessing = true
                            processingMsg = "Applying watermark..."
                            scope.launch {
                                val pages = repository.getPagesForDocument(first.id)
                                val config = PdfExportConfig(
                                    filename = "${first.title}_Watermarked",
                                    pageSize = PdfPageSize.A4,
                                    quality = PdfQuality.HIGH,
                                    watermarkText = "CONFIDENTIAL"
                                )
                                val wmDoc = repository.saveScanSessionOrNull(
                                    title = config.filename,
                                    category = first.category,
                                    pages = pages,
                                    pdfConfig = config
                                )
                                isProcessing = false
                                if (wmDoc != null) {
                                    Toast.makeText(context, "Watermarked PDF created!", Toast.LENGTH_SHORT).show()
                                    onOpenDocument(wmDoc.id)
                                } else {
                                    Toast.makeText(context, "Could not create PDF. Please try again.", Toast.LENGTH_LONG).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "No documents available to watermark", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun PdfToolCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SignaturePadDialog(
    onDismiss: () -> Unit,
    onSave: (Bitmap) -> Unit
) {
    val points = remember { mutableStateListOf<Offset>() }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sign Document",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { points.clear() }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }

                Text(
                    text = "Draw your signature inside the box below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Signature Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color(0xFFF8FAFC))
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                points.add(change.position)
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        for (i in 0 until points.size - 1) {
                            drawLine(
                                color = androidx.compose.ui.graphics.Color(0xFF0F172A),
                                start = points[i],
                                end = points[i + 1],
                                strokeWidth = 4.5f * density,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val bmp = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.BLACK
                                strokeWidth = 8f
                                strokeCap = Paint.Cap.ROUND
                            }
                            for (i in 0 until points.size - 1) {
                                val p1 = points[i]
                                val p2 = points[i + 1]
                                canvas.drawLine(p1.x * 2f, p1.y * 2f, p2.x * 2f, p2.y * 2f, paint)
                            }
                            onSave(bmp)
                        }
                    ) {
                        Text("Save Signature")
                    }
                }
            }
        }
    }
}
