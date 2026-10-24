# Artify Scanner

> **Every Document. Perfectly Captured.**  
> *Scan Smarter. Organize Better. Work Anywhere.*

Artify Scanner is an advanced, production-grade Android document scanning and productivity application engineered with native Kotlin, Jetpack Compose (Material 3), CameraX, and on-device computer vision.

---

## 🌟 Core Features & Architecture

### 1. Continuous Multi-Page Scanning Session (Mandatory Core Outcome)
- **Zero-Restart Workflow**: Point the camera and capture an entire stack of paper continuously without leaving the viewfinder.
- **Session Resilience**: Captured pages are safely preserved in app storage throughout the session.
- **Live Viewfinder HUD**: Real-time document boundary detection overlay, animated laser scan beam, corner brackets, and stability feedback.
- **Google scanner by default**: Start Scanning, Scan Receipt, Scan ID Card and Review → Add Page use Google's ML Kit Document Scanner (on-device ML edge detection, crop and clean-up, via Google Play services, no API key). Burst Scan and Wide Camera keep the app's own camera (hands-free capture and ultra-wide lens).
- **Smart Scan**: One tap opens Google's ML Kit Document Scanner (accurate live edge detection, auto-capture, crop, enhancement) and imports the pages into the session.
- **Auto-Capture & Manual Shutter**: Toggle between smart auto-capture and manual trigger.
- **In-Session Thumbnail Strip**: Live carousel showing captured pages with page counter ("Page 3", etc.).
- **Quick Undo / Retake**: Instantly discard the latest capture without interrupting the session.
- **Multi-Source Ingestion**: Add pages via camera or import directly from device photo library into the same session.

### 2. Multi-Page Document Review & Editor
- **Full-Size Inspection**: Interactive inspection of all captured pages.
- **Page Reordering**: Move pages up/down or reorder sequence before PDF generation.
- **Rotation**: 90° clockwise and counter-clockwise orientation adjustments.
- **Perspective Correction**: Automatic page-edge detection (Otsu threshold + largest region) followed by 4-point warping. Manual corner adjustment is available per page in the review screen (Crop button).
- **Enhancement Filters**:
  - `Magic Color`: Enhances clarity, lifts shadows, and sharpens text contrast.
  - `B&W Clean`: High-contrast threshold binarization ideal for contracts, receipts, and signatures.
  - `Whitening`: Cleans grayish paper backgrounds to pristine white while keeping ink dark.
  - `Grayscale`: Standard 8-bit luminance conversion.
  - `Original`: Untouched capture.
- **Page Operations**: Duplicate page, delete page, append additional pages from camera or gallery.

### 3. Professional PDF Creation Engine
- **Single Multi-Page PDF Output**: Combines all selected session pages into **ONE** valid, readable PDF document (never separate individual page files).
- **Memory-Safe Streaming**: Decodes and paints pages sequentially to `android.graphics.pdf.PdfDocument`, immediately recycling bitmaps to eliminate Out-Of-Memory hazards.
- **Customizable Export Settings**:
  - Page sizes: **A4** (595x842 pt), **US Letter** (612x792 pt), and **Original Proportions**.
  - Compression levels: High Quality (95%), Standard (80%), and Compact (60%).
  - Configurable page numbering footer (e.g. "Page 1 of 5").
  - Custom watermarks (e.g. "CONFIDENTIAL", "DRAFT").
  - Document metadata: Title, Author, Creator.
- **Searchable PDF Text Layer**: A custom streaming PDF writer (`PdfWriter`) embeds each page as a JPEG plus an invisible text layer (render mode 3) positioned from ML Kit line boxes, so text can be searched and selected in standard readers. Limitation: only Latin/WinAnsi characters are embedded; other scripts appear as `?` in the text layer (the image is unaffected).
- **Sharing & Printing**: Integrated Android Sharesheet and native Android `PrintManager` support.

### 4. On-Device OCR & Structured Expense Extraction
- **Google ML Kit Text Recognition**: High-speed, private, offline text recognition.
- **Search Across Library**: Real-time full-text search across titles, categories, tags, and OCR body text.
- **Receipt & Invoice Parsing**: Automated extraction of:
  - Merchant / Supplier name
  - Date and Invoice/Receipt number
  - Currency symbol
  - Subtotal, Tax/VAT, and Grand Total
  - Individual line items
- **CSV Export**: One-tap export of structured expense records to CSV format.

### 5. Document Management Library
- **Categories**: Invoices, Receipts, ID Cards & Passports, Contracts, Notes, and Personal.
- **Favorites**: Quick-access starred documents.
- **Sorting**: By Date (Newest/Oldest), Title (A-Z), and File Size.
- **Document Management**: Rename, delete, share, print, and view details.

### 6. PDF Productivity Toolkit
- **Merge PDFs**: Combine multiple saved documents into one single PDF.
- **Images to PDF**: Batch convert gallery photos into a single PDF.
- **Digital Signature Pad**: Draw, clear, and save vector signatures.
- **Compress PDF**: Optimize file size for email sharing.
- **Add Watermark**: Stamp documents with watermarks.

### 7. Artify AI Document Assistant
- **Executive Summaries**: High-level summaries of complex reports and agreements.
- **Dates & Action Items**: Key milestones and action checklists.
- **Clause Explainer**: Breaks down legal and contractual clauses into plain language.
- **Interactive Q&A**: Ask custom questions based on recognized document text.

---

## 📋 Feature Status Checklist

| Feature | Status | Notes |
| :--- | :--- | :--- |
| **Continuous Multi-Page Scanning** | ✅ Complete | Retains pages, auto/manual capture, live thumbnails |
| **Single-File Multi-Page PDF Export** | ✅ Complete | Native `PdfDocument` engine with A4/Letter/Original sizes |
| **Document Boundary Detection** | ⚠️ Heuristic | Edge detection on capture; works best on a light page on a darker background. Live overlay is decorative |
| **Auto-Capture** | ✅ Complete | Fires after the viewfinder is steady on a textured subject for ~1.2 s |
| **Perspective Warping** | ✅ Complete | 4-point quadrilateral mapping from detected or user-dragged corners |
| **Image Enhancement Filters** | ✅ Complete | Magic Color, B&W Clean, Whitening, Grayscale |
| **On-Device OCR** | ✅ Complete | ML Kit Text Recognition with text selection and copy |
| **Receipt & Invoice Parser** | ✅ Complete | Extracts merchant, date, amounts, and exports to CSV |
| **ID Card / Passport Mode** | ✅ Complete | Front & Back guided capture workflow |
| **Room Local Persistence** | ✅ Complete | `AppDatabase` v2 (page→document cascade FK, OCR line boxes); no destructive migration |
| **Session Recovery** | ✅ Complete | In-progress scans are mirrored to disk and restored after an Android process kill |
| **Document Library & Search** | ✅ Complete | Full-text OCR search, categories, sorting |
| **PDF Toolkit (Merge, Compress, Sign)** | ✅ Complete | Functional utilities built into Tools screen |
| **AI Document Assistant** | ⚠️ Needs setup | Real Gemini calls via Firebase AI Logic; requires `google-services.json`. Sends document text to Google |
| **App Lock** | ✅ Complete | Biometric / device credential; re-locks after 30 s in background; screenshots and recents thumbnails blocked while enabled |
| **Android Sharesheet & Printing** | ✅ Complete | `FileProvider` URI sharing & `PrintManager` adapter |

---

## 🛠️ Build and Setup Instructions

### Environment Prerequisites
- Android Studio Ladybug (or newer) / Gradle 8.x+
- JDK 17 (or JDK 11+)
- Minimum SDK: API 24 (Android 7.0)
- Target / Compile SDK: API 36

### Building from Command Line
```bash
# Build Debug APK
gradle :app:assembleDebug

# Run Unit and Robolectric Tests
gradle :app:testDebugUnitTest
```

### External Credentials & Configuration
- **Core Scanning & PDF Engine**: 100% offline, local-first. No external API keys required.
- **Gemini AI Assistant**: Uses Firebase AI Logic. Add `app/google-services.json` from a Firebase project with the Gemini Developer API enabled (and App Check for production). Without it the assistant shows a configuration message.
