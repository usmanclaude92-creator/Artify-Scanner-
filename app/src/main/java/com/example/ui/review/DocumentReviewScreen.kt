package com.example.ui.review

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.camera.ScanningSessionManager
import com.example.data.model.CapturedPage
import com.example.data.model.DocumentCategory
import com.example.data.model.PdfExportConfig
import com.example.data.model.ScanFilter
import com.example.data.repository.DocumentRepository
import com.example.pdf.PdfShareUtil
import com.example.ui.components.ProcessingDialog
import com.example.ui.pdf.PdfExportDialog
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReviewScreen(
    repository: DocumentRepository,
    onNavigateBackToCamera: () -> Unit,
    onFinishAndGoHome: () -> Unit,
    onOpenDocumentDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pages = ScanningSessionManager.capturedPages

    // If session has no pages, handle back
    BackHandler {
        onNavigateBackToCamera()
    }

    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isSavingDocument by remember { mutableStateOf(false) }
    var saveProgressMessage by remember { mutableStateOf("") }

    val activePage: CapturedPage? = pages.getOrNull(selectedPageIndex)

    // Gallery import picker to append pages
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

    if (showExportDialog) {
        val defaultName = remember {
            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
            "Scan_${sdf.format(Date())}"
        }
        PdfExportDialog(
            initialFilename = defaultName,
            pageCount = pages.size,
            onDismiss = { showExportDialog = false },
            onExport = { config ->
                showExportDialog = false
                isSavingDocument = true
                saveProgressMessage = "Generating single PDF for ${pages.size} pages..."

                scope.launch {
                    val category = when (ScanningSessionManager.scanMode.value) {
                        com.example.data.model.ScanMode.RECEIPT_INVOICE -> DocumentCategory.INVOICES
                        com.example.data.model.ScanMode.ID_CARD -> DocumentCategory.ID_CARDS
                        else -> DocumentCategory.ALL
                    }

                    val savedDoc = repository.saveScanSessionOrNull(
                        title = config.filename,
                        category = category,
                        pages = pages.toList(),
                        pdfConfig = config
                    )

                    isSavingDocument = false
                    if (savedDoc != null) {
                        Toast.makeText(context, "PDF saved successfully! (1 file, ${pages.size} pages)", Toast.LENGTH_LONG).show()
                        onOpenDocumentDetail(savedDoc.id)
                    } else {
                        Toast.makeText(context, "Could not create PDF. Please try again.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (isSavingDocument) {
        ProcessingDialog(message = saveProgressMessage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Review Document",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${pages.size} ${if (pages.size == 1) "page" else "pages"} • Continuous Session",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBackToCamera,
                        modifier = Modifier.testTag("review_back_to_camera")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Camera")
                    }
                },
                actions = {
                    // Add more from camera
                    IconButton(
                        onClick = onNavigateBackToCamera,
                        modifier = Modifier.testTag("add_pages_camera_button")
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Add page with camera")
                    }
                    // Add more from gallery
                    IconButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.testTag("add_pages_gallery_button")
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "Add page from gallery")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            // Save & Export action footer
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBackToCamera,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Page")
                    }

                    Button(
                        onClick = { showExportDialog = true },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("save_pdf_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save as PDF (${pages.size}p)")
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No pages captured yet. Return to camera to start.")
                }
            } else {
                // Large Active Page Preview with rotation preview
                activePage?.let { page ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(page.localImagePath),
                            contentDescription = "Active page preview",
                            modifier = Modifier
                                .fillMaxSize()
                                .rotate(page.rotationDegrees.toFloat())
                                .padding(8.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Page indicator chip
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Navigation arrows over preview
                        if (selectedPageIndex > 0) {
                            IconButton(
                                onClick = { selectedPageIndex-- },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Prev", tint = Color.White)
                            }
                        }
                        if (selectedPageIndex < pages.size - 1) {
                            IconButton(
                                onClick = { selectedPageIndex++ },
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.45f))
                            ) {
                                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }

                    // PAGE EDITING TOOLBAR: Rotate Left, Rotate Right, Reorder Left/Right, Duplicate, Delete
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { ScanningSessionManager.rotatePage(page.id, clockwise = false) },
                                modifier = Modifier.testTag("rotate_left_button")
                            ) {
                                Icon(Icons.Default.RotateLeft, contentDescription = "Rotate Left")
                            }

                            IconButton(
                                onClick = { ScanningSessionManager.rotatePage(page.id, clockwise = true) },
                                modifier = Modifier.testTag("rotate_right_button")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate Right")
                            }

                            // Reorder: Move Left
                            IconButton(
                                onClick = {
                                    if (selectedPageIndex > 0) {
                                        ScanningSessionManager.movePageUp(selectedPageIndex)
                                        selectedPageIndex--
                                    }
                                },
                                enabled = selectedPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left")
                            }

                            // Reorder: Move Right
                            IconButton(
                                onClick = {
                                    if (selectedPageIndex < pages.size - 1) {
                                        ScanningSessionManager.movePageDown(selectedPageIndex)
                                        selectedPageIndex++
                                    }
                                },
                                enabled = selectedPageIndex < pages.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right")
                            }

                            // Duplicate
                            IconButton(
                                onClick = {
                                    ScanningSessionManager.duplicatePage(context, page.id)
                                    Toast.makeText(context, "Page duplicated", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
                            }

                            // Delete
                            IconButton(
                                onClick = {
                                    ScanningSessionManager.deletePage(page.id)
                                    if (selectedPageIndex >= pages.size && selectedPageIndex > 0) {
                                        selectedPageIndex--
                                    }
                                },
                                modifier = Modifier.testTag("delete_page_button")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Page",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Enhancement Filter Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Filter:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        ScanFilter.values().forEach { filter ->
                            FilterChip(
                                selected = page.filter == filter,
                                onClick = {
                                    ScanningSessionManager.updatePageFilter(context, scope, page.id, filter)
                                },
                                label = { Text(filter.displayName, fontSize = 12.sp) },
                                leadingIcon = if (page.filter == filter) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Page Thumbnails Bottom Carousel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        pages.forEachIndexed { index, p ->
                            val isSelected = index == selectedPageIndex
                            Card(
                                modifier = Modifier
                                    .size(width = 54.dp, height = 72.dp)
                                    .clickable { selectedPageIndex = index }
                                    .border(
                                        width = if (isSelected) 2.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    ),
                                shape = RoundedCornerShape(8.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    AsyncImage(
                                        model = File(p.localImagePath),
                                        contentDescription = "Thumbnail $index",
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .rotate(p.rotationDegrees.toFloat()),
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
                                            text = "${index + 1}",
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
                }
            }
        }
    }
}
