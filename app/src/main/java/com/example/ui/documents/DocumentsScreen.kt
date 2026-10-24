package com.example.ui.documents

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DocumentCategory
import com.example.data.model.ScannedDocumentItem
import com.example.data.repository.DocumentRepository
import com.example.pdf.PdfShareUtil
import com.example.ui.components.DocumentCardItem
import com.example.ui.components.ScreenTitle
import com.example.ui.components.EmptyDocumentsView
import kotlinx.coroutines.launch
import java.io.File

enum class DocumentSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    TITLE_ASC("Title (A-Z)"),
    SIZE_DESC("File Size")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentsScreen(
    repository: DocumentRepository,
    onOpenDocument: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DocumentCategory.ALL) }
    var onlyFavorites by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(DocumentSortOrder.DATE_DESC) }

    val rawDocs by if (searchQuery.isNotBlank()) {
        repository.searchDocuments(searchQuery).collectAsState(initial = emptyList())
    } else if (onlyFavorites) {
        repository.getFavoriteDocuments().collectAsState(initial = emptyList())
    } else if (selectedCategory != DocumentCategory.ALL) {
        repository.getDocumentsByCategory(selectedCategory).collectAsState(initial = emptyList())
    } else {
        repository.getAllDocuments().collectAsState(initial = emptyList())
    }

    val sortedDocs = remember(rawDocs, sortOrder) {
        when (sortOrder) {
            DocumentSortOrder.DATE_DESC -> rawDocs.sortedByDescending { it.updatedAt }
            DocumentSortOrder.DATE_ASC -> rawDocs.sortedBy { it.updatedAt }
            DocumentSortOrder.TITLE_ASC -> rawDocs.sortedBy { it.title.lowercase() }
            DocumentSortOrder.SIZE_DESC -> rawDocs.sortedByDescending { it.fileSizeBytes }
        }
    }

    var documentToRename by remember { mutableStateOf<ScannedDocumentItem?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var documentToDelete by remember { mutableStateOf<ScannedDocumentItem?>(null) }

    if (documentToRename != null) {
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = { Text("Rename Document") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Title") },
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

    if (documentToDelete != null) {
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Delete '${documentToDelete?.title}' permanently?") },
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
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { ScreenTitle("Documents", "All your scans in one place") }
                    IconButton(
                        onClick = { onlyFavorites = !onlyFavorites },
                        modifier = Modifier.padding(end = 12.dp).testTag("filter_favorites_button")
                    ) {
                        Icon(
                            imageVector = if (onlyFavorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorites",
                            tint = if (onlyFavorites) androidx.compose.ui.graphics.Color(0xFFF43F5E) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Search field
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by title or OCR content...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("documents_search_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DocumentCategory.values().forEach { category ->
                            FilterChip(
                                selected = selectedCategory == category && !onlyFavorites,
                                onClick = {
                                    onlyFavorites = false
                                    selectedCategory = category
                                },
                                shape = RoundedCornerShape(20.dp),
                                label = { Text(category.displayName, fontSize = 12.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sort orders
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        DocumentSortOrder.values().forEach { order ->
                            FilterChip(
                                selected = sortOrder == order,
                                onClick = { sortOrder = order },
                                label = { Text(order.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Documents
            if (sortedDocs.isEmpty()) {
                item {
                    EmptyDocumentsView(
                        message = if (onlyFavorites) "No favorite documents yet" else "No documents found"
                    )
                }
            } else {
                items(sortedDocs, key = { it.id }) { doc ->
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 3.dp)) {
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
