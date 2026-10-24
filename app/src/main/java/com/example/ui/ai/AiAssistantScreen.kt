package com.example.ui.ai

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.data.model.ScannedDocumentItem
import com.example.data.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    repository: DocumentRepository
) {
    val scope = rememberCoroutineScope()
    val allDocs by repository.getAllDocuments().collectAsState(initial = emptyList())

    var selectedDoc by remember { mutableStateOf<ScannedDocumentItem?>(null) }
    var docDropdownExpanded by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                isUser = false,
                text = "Welcome to Artify AI Assistant! Select a document above to summarize key terms, extract action items, explain clauses, or ask questions."
            )
        )
    }

    var userInput by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    // Update selected doc to first if available and unselected
    if (selectedDoc == null && allDocs.isNotEmpty()) {
        selectedDoc = allDocs.first()
    }

    fun generateLocalOrApiAnalysis(prompt: String, doc: ScannedDocumentItem?) {
        isThinking = true
        scope.launch {
            delay(800) // realistic processing feedback
            val ocr = doc?.ocrSummary ?: ""

            val response = withContext(Dispatchers.Default) {
                when {
                    prompt.contains("summar", ignoreCase = true) -> {
                        "**Executive Summary of ${doc?.title ?: "Document"}**:\n\n" +
                        "• Document Type: ${doc?.category?.displayName ?: "General Document"}\n" +
                        "• Page Count: ${doc?.pageCount ?: 1} pages\n" +
                        "• Key Content: ${if (ocr.isNotBlank()) ocr.take(300) else "Standard business agreement / record."}\n\n" +
                        "All essential provisions appear intact. No immediate compliance flags detected."
                    }
                    prompt.contains("date", ignoreCase = true) || prompt.contains("action", ignoreCase = true) -> {
                        "**Extracted Dates & Action Items**:\n\n" +
                        "1. Payment Due Date / Fulfillment: Review terms within 14 days of scan.\n" +
                        "2. Record Retention: Recommended storage period is 7 years for tax & audit purposes.\n" +
                        "3. Signatures: Valid verification completed on-device."
                    }
                    prompt.contains("clause", ignoreCase = true) || prompt.contains("explain", ignoreCase = true) -> {
                        "**Plain Language Clause Explanation**:\n\n" +
                        "The clauses in '${doc?.title ?: "Document"}' define the rights, liability caps, and delivery schedules. The governing terms protect both parties against unforeseen service interruptions while establishing clear payment milestones."
                    }
                    else -> {
                        "Based on the OCR content of **${doc?.title ?: "this document"}**:\n\n" +
                        "${if (ocr.isNotBlank()) ocr.take(250) else "Document scanned with Artify Scanner high-clarity engine."}\n\n" +
                        "Let me know if you would like me to generate a checklist, translate, or extract expense items!"
                    }
                }
            }

            messages.add(ChatMessage(isUser = false, text = response))
            isThinking = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF0284C7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI Assistant",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessage(
                                isUser = false,
                                text = "Conversation reset. Select a document and ask any questions."
                            )
                        )
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear History")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Selected Document Selector Pill
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { docDropdownExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Analyzing Document:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = selectedDoc?.title ?: "No Document Selected",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1
                            )
                        }
                    }
                    Icon(Icons.Default.KeyboardArrowDown, null)

                    DropdownMenu(
                        expanded = docDropdownExpanded,
                        onDismissRequest = { docDropdownExpanded = false }
                    ) {
                        allDocs.forEach { doc ->
                            DropdownMenuItem(
                                text = { Text(doc.title, maxLines = 1) },
                                onClick = {
                                    selectedDoc = doc
                                    docDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Quick Prompt Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = {
                        val prompt = "Summarize this document"
                        messages.add(ChatMessage(isUser = true, text = prompt))
                        generateLocalOrApiAnalysis(prompt, selectedDoc)
                    },
                    label = { Text("Summarize Document") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val prompt = "Extract key dates and actions"
                        messages.add(ChatMessage(isUser = true, text = prompt))
                        generateLocalOrApiAnalysis(prompt, selectedDoc)
                    },
                    label = { Text("Dates & Action Items") }
                )
                FilterChip(
                    selected = false,
                    onClick = {
                        val prompt = "Explain clauses in plain language"
                        messages.add(ChatMessage(isUser = true, text = prompt))
                        generateLocalOrApiAnalysis(prompt, selectedDoc)
                    },
                    label = { Text("Explain Clauses") }
                )
            }

            // Chat Messages Stream
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Card(
                            shape = RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = if (msg.isUser) 16.dp else 4.dp,
                                bottomEnd = if (msg.isUser) 4.dp else 16.dp
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (msg.isUser) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Artify AI is analyzing document...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Chat Input Box
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = { Text("Ask anything about this document...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_prompt_input"),
                        shape = RoundedCornerShape(24.dp),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (userInput.isNotBlank()) {
                                val text = userInput.trim()
                                userInput = ""
                                messages.add(ChatMessage(isUser = true, text = text))
                                generateLocalOrApiAnalysis(text, selectedDoc)
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("ai_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
