package com.example.pdf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintManager
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfShareUtil {

    fun getUriForFile(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** Copies the PDF into the phone's public Downloads folder and tells the user. */
    fun downloadPdf(context: Context, pdfFile: File, displayName: String) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }
        val name = displayName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "Scan" }.removeSuffix(".pdf") + ".pdf"
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Downloads is not available")
                resolver.openOutputStream(uri)?.use { out -> FileInputStream(pdfFile).use { it.copyTo(out) } }
                    ?: error("Could not write the file")
                values.clear()
                values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                dir.mkdirs()
                FileInputStream(pdfFile).use { input -> FileOutputStream(File(dir, name)).use { input.copyTo(it) } }
            }
            Toast.makeText(context, "Saved to Downloads: $name", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Could not save to Downloads: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdf(context: Context, pdfFile: File, title: String = "Share Scanned PDF") {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = getUriForFile(context, pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, pdfFile.nameWithoutExtension)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, title))
        } catch (e: Exception) {
            Toast.makeText(context, "Could not share PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val contentUri = getUriForFile(context, pdfFile)
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "No PDF viewer app installed to open file", Toast.LENGTH_SHORT).show()
        }
    }

    fun printPdf(context: Context, pdfFile: File) {
        if (!pdfFile.exists()) {
            Toast.makeText(context, "PDF file not found", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Print service unavailable", Toast.LENGTH_SHORT).show()
                return
            }

            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: android.os.Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = android.print.PrintDocumentInfo.Builder(pdfFile.name)
                        .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out android.print.PageRange>?,
                    destination: android.os.ParcelFileDescriptor?,
                    cancellationSignal: android.os.CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (destination == null) return
                    try {
                        FileInputStream(pdfFile).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        callback?.onWriteFailed(e.message)
                    }
                }
            }

            val printJobName = "Artify_${pdfFile.nameWithoutExtension}"
            printManager.print(printJobName, printAdapter, PrintAttributes.Builder().build())
        } catch (e: Exception) {
            Toast.makeText(context, "Printing error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
