package com.example

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentCategory
import com.example.data.model.ScannedDocumentItem
import com.example.data.repository.DocumentRepository
import com.example.ui.ai.AiAssistantScreen
import com.example.ui.components.DocumentCardItem
import com.example.ui.auth.AuthScreen
import com.example.ui.components.AppHeader
import com.example.ui.profile.ProfileScreen
import com.example.ui.scan.ScanIntroScreen
import com.example.ui.home.HomeScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.ArtifyScannerTheme
import com.example.ui.tools.PdfToolsScreen
import com.example.ui.viewer.DocumentInfoTab
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the main screens on the JVM and writes PNGs to build/screenshots so layout can be reviewed
 * without a device. It never fails the build: problems are written to <name>.error.txt instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File("build/screenshots").apply { mkdirs() }

    private fun shot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        try {
            rule.setContent {
                ArtifyScannerTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
            rule.waitForIdle()
            val bitmap = try {
                rule.onRoot().captureToImage().asAndroidBitmap()
            } catch (first: Throwable) {
                // Fall back to drawing the decor view into a software canvas
                val view = rule.activity.window.decorView
                val w = if (view.width > 0) view.width else 1080
                val h = if (view.height > 0) view.height else 2340
                view.measure(
                    android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY)
                )
                view.layout(0, 0, w, h)
                android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888).also {
                    view.draw(android.graphics.Canvas(it))
                }
            }
            FileOutputStream(File(outDir, "$name.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        } catch (t: Throwable) {
            File(outDir, "$name.error.txt").writeText(t.stackTraceToString())
        }
    }

    private val repo get() = DocumentRepository(ApplicationProvider.getApplicationContext<Context>())

    private val sampleDoc = ScannedDocumentItem(
        id = "1", title = "Invoice - Acme Cloud Solutions", createdAt = 0, updatedAt = 1_760_000_000_000,
        pageCount = 2, category = DocumentCategory.INVOICES, isFavorite = true, pdfFilePath = "", thumbnailPath = "",
        fileSizeBytes = 1_250_000, tags = listOf("Invoice", "Work", "ACME")
    )

    @Test fun splash() = shot("splash", true) { SplashScreen(showActions = true) }

    @Test fun authSignUp() = shot("auth_signup", false) {
        AuthScreen(true, false, { _, _, _ -> null }, { _, _ -> null }, { null }, { null }, {}, {})
    }

    @Test fun authLoginDark() = shot("auth_login_dark", true) {
        AuthScreen(false, true, { _, _, _ -> null }, { _, _ -> null }, { null }, { null }, {}, {})
    }

    @Test fun scanIntro() = shot("scan_intro", true) { ScanIntroScreen({}, {}) }

    @Test fun headerLight() = shot("header_light", false) { AppHeader({}) }

    @Test fun headerDark() = shot("header_dark", true) { AppHeader({}) }

    @Test fun profileLight() = shot("profile_light", false) { ProfileScreen(repo, {}, {}) }

    @Test fun homeLight() = shot("home_light", false) {
        HomeScreen(repo, {}, {}, {}, {}, {})
    }

    @Test fun homeDark() = shot("home_dark", true) {
        HomeScreen(repo, {}, {}, {}, {}, {})
    }

    @Test fun toolsLight() = shot("tools_light", false) { PdfToolsScreen(repo) {} }

    @Test fun toolsDark() = shot("tools_dark", true) { PdfToolsScreen(repo) {} }

    @Test fun settingsLight() = shot("settings_light", false) { SettingsScreen() }

    @Test fun settingsDark() = shot("settings_dark", true) { SettingsScreen() }

    @Test fun aiLight() = shot("ai_light", false) { AiAssistantScreen(repo) }

    @Test fun detailsLight() = shot("details_light", false) {
        DocumentInfoTab(sampleDoc, {}, {}, {}, {})
    }

    @Test fun detailsDark() = shot("details_dark", true) {
        DocumentInfoTab(sampleDoc, {}, {}, {}, {})
    }

    @Test fun docCardsLight() = shot("doc_cards_light", false) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
            DocumentCardItem(sampleDoc, {}, {}, {}, {}, {}, {})
        }
    }

}
