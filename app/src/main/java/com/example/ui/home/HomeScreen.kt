package com.example.ui.home

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
    onNavigateToDocuments: () -> Unit,
    onNavigateToTools: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocumentCategory.ALL) }

    val documents by if (searchQuery.isNotBlank()) {
        repository.searchDocuments(searchQuery).collectAsState(initial = emptyList())
    } else if (selectedCategory != DocumentCategory.ALL) {
        repository.getDocumentsByCategory(selectedCategory).collectAsState(initial = emptyList())
    } else {
        repository.getAllDocuments().collectAsState(initial = emptyList())
    }

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
                onStartScan(ScanMode.BATCH_DOCUMENTS)
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

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
                    onStartScan(ScanMode.BATCH_DOCUMENTS)
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                modifier = Modifier.testTag("home_fab_scan")
            ) {
                Icon(
                    imageVector = Icons.Default.DocumentScanner,
                    contentDescription = "Scan Document",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            // Header: Brand & Tagline
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFF0F172A),
                                    Color(0xFF1E293B)
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 24.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0284C7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Artify Scanner",
                                color = Color.White,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 22.sp
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Every Document. Perfectly Captured.",
                            color = Color(0xFF38BDF8),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Large Hero Start Scanning Button
                        Button(
                            onClick = {
                                ScanningSessionManager.startNewSession(ScanMode.BATCH_DOCUMENTS)
                                onStartScan(ScanMode.BATCH_DOCUMENTS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("hero_start_scanning_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF0284C7)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Start Scanning (Multi-Page)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Quick Actions 2x2 Grid
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Actions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionTile(
                            title = "Scan Receipt",
                            subtitle = "Expense OCR",
                            icon = Icons.Default.ReceiptLong,
                            iconBgColor = Color(0xFF059669),
                            onClick = {
                                ScanningSessionManager.startNewSession(ScanMode.RECEIPT_INVOICE)
                                onStartScan(ScanMode.RECEIPT_INVOICE)
                            },
                            modifier = Modifier.weight(1f)
                        )

                        QuickActionTile(
                            title = "Scan ID Card",
                            subtitle = "Front & Back",
                            icon = Icons.Default.Badge,
                            iconBgColor = Color(0xFFD97706),
                            onClick = {
                                ScanningSessionManager.startNewSession(ScanMode.ID_CARD)
                                onStartScan(ScanMode.ID_CARD)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickActionTile(
                            title = "Import Files",
                            subtitle = "Photos & PDFs",
                            icon = Icons.Default.UploadFile,
                            iconBgColor = Color(0xFF6366F1),
                            onClick = { importLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f)
                        )

                        QuickActionTile(
                            title = "PDF Toolkit",
                            subtitle = "Merge & Sign",
                            icon = Icons.Default.DocumentScanner,
                            iconBgColor = Color(0xFF0284C7),
                            onClick = onNavigateToTools,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search title, tags, or OCR text...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_bar")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Filter Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentCategory.values().forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category },
                                label = { Text(category.displayName, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "Search Results" else "Recent Documents",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${documents.size} saved",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Documents List
            if (documents.isEmpty()) {
                item {
                    EmptyDocumentsView(
                        message = if (searchQuery.isNotBlank()) "No matching documents" else "No documents yet. Tap Start Scanning!"
                    )
                }
            } else {
                items(documents, key = { it.id }) { doc ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        DocumentCardItem(
                            document = doc,
                            onClick = { onOpenDocument(doc.id) },
                            onToggleFavorite = {
                                scope.launch {
                                    repository.toggleFavorite(doc.id, doc.isFavorite)
                                }
                            },
                            onShare = {
                                PdfShareUtil.sharePdf(context, File(doc.pdfFilePath), "Share ${doc.title}")
                            },
                            onPrint = {
                                PdfShareUtil.printPdf(context, File(doc.pdfFilePath))
                            },
                            onRename = {
                                documentToRename = doc
                                renameInput = doc.title
                            },
                            onDelete = {
                                documentToDelete = doc
                            }
                        )
                    }
                }
            }
        }
    }
}
