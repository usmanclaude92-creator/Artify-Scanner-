package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.DocumentCategory
import com.example.data.model.ScanFilter

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int,
    val category: String, // DocumentCategory name
    val isFavorite: Boolean,
    val pdfFilePath: String,
    val thumbnailPath: String,
    val fileSizeBytes: Long,
    val ocrFullText: String,
    val tags: String // comma-separated
)

@Entity(tableName = "document_pages")
data class PageEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val pageIndex: Int,
    val imagePath: String,
    val originalImagePath: String,
    val rotationDegrees: Int,
    val filterName: String, // ScanFilter name
    val ocrText: String
)
