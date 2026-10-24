package com.example.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import com.example.ui.theme.BrandViolet
import com.example.ui.theme.BrandPink
import com.example.ui.theme.BrandOrange
import com.example.ui.theme.BrandGreen
import com.example.ui.theme.BrandBlue
import com.example.ui.components.SectionHeader
import com.example.ui.components.GradientButton
import com.example.ui.components.FeatureChip
import com.example.ui.components.BrandHeader
import com.example.ui.components.ActionCard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.ScanningSessionManager
import com.example.data.model.DocumentCategory
import com.example.data.model.ScanMode
import com.example.data.model.ScannedDocumentItem
import com.example.data.repository.DocumentRepository
import com.example.pdf.PdfShareUtil
import com.example.ui.components.DocumentCardItem
import com.example.ui.components.EmptyDocumentsView
import com.example.ui.components.QuickActionTile
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: DocumentRepository,
    onStartScan: (ScanMode) -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenReview: () -> Unit,
    onNavigateToDocuments: () -> Unit,
    onNavigateToTools: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allDocuments by repository.getAllDocuments().collectAsState(initial = emptyList())
    val documents = allDocuments.take(6)

    var documentToRename by remember { mutableStateOf<ScannedDocumentItem?>(null) }
    var renameInput by remember { mutableStateOf("") }

    var documentToDelete by remember { mutableStateOf<ScannedDocumentItem?>(null) }

    // Multi-image file import directly into a new continuous scanning session
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
            scope.launch {
                for (uri in uris) {
                    ScanningSessionManager.addPageFromUri(context, uri)
                }
                onOpenReview()
            }
        }
    }

    // Rename Dialog
    if (documentToRename != null) {
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Document Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val doc = documentToRename
                        if (doc != null && renameInput.isNotBlank()) {
                            scope.launch {
                                repository.updateDocumentTitle(doc.id, renameInput.trim())
                                documentToRename = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (documentToDelete != null) {
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to delete '${documentToDelete?.title}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val doc = documentToDelete
                        if (doc != null) {
                            scope.launch {
                                repository.deleteDocument(doc.id)
                                documentToDelete = null
                                Toast.makeText(context, "Document deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Hero: headline, primary scan button and the three promises
        item {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.surface)
                        )
                    )
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(26.dp))
                    .padding(20.dp)
            ) {
                Text(
                    "Capture anything.\nTurn it digital.",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan, organize and share your documents in seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(18.dp))
                GradientButton(
                    text = "Scan Document (Multi-Page)",
                    icon = Icons.Default.CameraAlt,
                    onClick = {
                        ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
                        onStartScan(ScanMode.BATCH_DOCUMENTS)
                    },
                    modifier = Modifier.testTag("hero_start_scanning_button")
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FeatureChip(Icons.Default.CenterFocusStrong, "Auto Detect")
                    FeatureChip(Icons.Default.HighQuality, "High Quality")
                    FeatureChip(Icons.Default.PictureAsPdf, "PDF Output")
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)); SectionHeader("Quick Actions") }

        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(
                        title = "Receipt", subtitle = "OCR",
                        icon = Icons.Default.ReceiptLong, tint = BrandGreen,
                        onClick = {
                            ScanningSessionManager.startNewSession(ScanMode.RECEIPT_INVOICE)
                            onStartScan(ScanMode.RECEIPT_INVOICE)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        title = "ID Card", subtitle = "Front & Back",
                        icon = Icons.Default.Badge, tint = BrandBlue,
                        onClick = {
                            ScanningSessionManager.startNewSession(ScanMode.ID_CARD)
                            onStartScan(ScanMode.ID_CARD)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(
                        title = "Burst Scan", subtitle = "Hands-free",
                        icon = Icons.Default.BurstMode, tint = BrandViolet,
                        onClick = {
                            ScanningSessionManager.startNewSession(ScanMode.BURST_SCAN)
                            onStartScan(ScanMode.BURST_SCAN)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        title = "Import Files", subtitle = "Photos & PDFs",
                        icon = Icons.Default.UploadFile, tint = Color(0xFF6366F1),
                        onClick = { importLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionCard(
                        title = "Single Page", subtitle = "One document",
                        icon = Icons.Default.CameraAlt, tint = BrandOrange,
                        onClick = {
                            ScanningSessionManager.startNewSession(ScanMode.SINGLE_PAGE)
                            onStartScan(ScanMode.SINGLE_PAGE)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ActionCard(
                        title = "PDF Tools", subtitle = "Merge & Sign",
                        icon = Icons.Default.DocumentScanner, tint = BrandPink,
                        onClick = onNavigateToTools,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(10.dp)); SectionHeader("Recent Documents", "View All", onNavigateToDocuments) }

        if (documents.isEmpty()) {
            item { EmptyDocumentsView(message = "No documents yet. Tap Scan Document!") }
        } else {
            items(documents, key = { it.id }) { doc ->
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
                    DocumentCardItem(
                        document = doc,
                        onClick = { onOpenDocument(doc.id) },
                        onToggleFavorite = { scope.launch { repository.toggleFavorite(doc.id, doc.isFavorite) } },
                        onShare = { PdfShareUtil.sharePdf(context, File(doc.pdfFilePath), "Share ${doc.title}") },
                        onPrint = { PdfShareUtil.printPdf(context, File(doc.pdfFilePath)) },
                        onRename = {
                            documentToRename = doc
                            renameInput = doc.title
                        },
                        onDelete = { documentToDelete = doc }
                    )
                }
            }
        }
    }
}
