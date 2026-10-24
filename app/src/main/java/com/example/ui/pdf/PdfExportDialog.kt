package com.example.ui.pdf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.PdfExportConfig
import com.example.data.model.PdfPageSize
import com.example.data.model.PdfQuality

@Composable
fun PdfExportDialog(
    initialFilename: String,
    pageCount: Int,
    onDismiss: () -> Unit,
    onExport: (PdfExportConfig) -> Unit
) {
    var filename by remember { mutableStateOf(initialFilename) }
    var selectedPageSize by remember { mutableStateOf(PdfPageSize.A4) }
    var selectedQuality by remember { mutableStateOf(PdfQuality.HIGH) }
    var includePageNumbers by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "PDF Export Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Text(
                    text = "Creating 1 PDF containing $pageCount pages",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // Filename Input
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("PDF Filename") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_filename_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Page Size Option
                Text(
                    text = "Page Size",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                PdfPageSize.values().forEach { size ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPageSize = size }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedPageSize == size,
                            onClick = { selectedPageSize = size }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = size.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quality Option
                Text(
                    text = "Compression & Quality",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                PdfQuality.values().forEach { quality ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedQuality = quality }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedQuality == quality,
                            onClick = { selectedQuality = quality }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = quality.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Page Numbers toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includePageNumbers = !includePageNumbers }
                ) {
                    Checkbox(
                        checked = includePageNumbers,
                        onCheckedChange = { includePageNumbers = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Add page numbering (e.g. 'Page 1 of $pageCount')",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Watermark optional
                OutlinedTextField(
                    value = watermarkText,
                    onValueChange = { watermarkText = it },
                    label = { Text("Watermark Text (Optional)") },
                    placeholder = { Text("e.g. CONFIDENTIAL, DRAFT") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
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
                            val cleanName = filename.ifBlank { "Scanned_Document" }
                            onExport(
                                PdfExportConfig(
                                    filename = cleanName,
                                    pageSize = selectedPageSize,
                                    quality = selectedQuality,
                                    includePageNumbers = includePageNumbers,
                                    watermarkText = watermarkText,
                                    documentTitle = cleanName
                                )
                            )
                        },
                        modifier = Modifier.testTag("confirm_export_pdf_button")
                    ) {
                        Text("Export PDF")
                    }
                }
            }
        }
    }
}
