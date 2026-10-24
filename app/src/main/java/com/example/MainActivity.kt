package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import com.example.security.AppLock
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.ThemeController
import com.example.ui.security.LockScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.camera.ScanningSessionManager
import com.example.data.model.ScanMode
import com.example.data.repository.DocumentRepository
import com.example.ui.ai.AiAssistantScreen
import com.example.ui.documents.DocumentsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.Screen
import com.example.ui.review.DocumentReviewScreen
import com.example.ui.scanner.CameraScannerScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.ArtifyScannerTheme
import com.example.ui.tools.PdfToolsScreen
import com.example.ui.viewer.DocumentDetailScreen

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : FragmentActivity() {
    /** Hides document content from screenshots and the recents screen while App Lock is on. */
    fun applySecureFlag() {
        if (AppLock.isEnabled(this)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStart() {
        super.onStart()
        AppLock.onAppForegrounded(this)
    }

    override fun onStop() {
        super.onStop()
        AppLock.onAppBackgrounded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) AppLock.lockIfEnabledOnLaunch(this)
        ScanningSessionManager.init(applicationContext, restore = savedInstanceState != null)
        applySecureFlag()
        enableEdgeToEdge()
        ThemeController.init(this)
        val showSplashAtStart = savedInstanceState == null
        setContent {
            ArtifyScannerTheme {
                var showSplash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(showSplashAtStart) }
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1400)
                    showSplash = false
                }
                androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
                    if (AppLock.locked.value) {
                        LockScreen(activity = this@MainActivity)
                    } else {
                        ArtifyScannerApp()
                    }
                    AnimatedVisibility(
                        visible = showSplash,
                        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(350))
                    ) { SplashScreen() }
                }
            }
        }
    }
}

@Composable
fun ArtifyScannerApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { DocumentRepository(context) }

    // Seed realistic guide & sample documents on first startup
    LaunchedEffect(Unit) {
        repository.migrateCachedPageImages()
        repository.seedSampleDocumentsIfEmpty()
    }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // Google ML Kit Document Scanner is the default scanner; its cropped pages join the session
    val googleScannerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val scan = com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        if (result.resultCode == android.app.Activity.RESULT_OK && scan != null) {
            if (navController.currentBackStackEntry?.destination?.route != Screen.DocumentReview.route) {
                navController.navigate(Screen.DocumentReview.route)
            }
            scope.launch {
                for (page in scan.pages.orEmpty()) {
                    ScanningSessionManager.addPageFromUri(
                        context, page.imageUri, com.example.data.model.ScanFilter.ORIGINAL, autoCrop = false
                    )
                }
            }
        }
    }

    fun launchGoogleScanner(maxPages: Int) {
        val activity = context as? android.app.Activity ?: return
        com.example.camera.MlKitScanner.createIntentSender(
            activity = activity,
            maxPages = maxPages,
            onReady = { googleScannerLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(it).build()) },
            onError = { android.widget.Toast.makeText(context, "Google scanner unavailable: $it", android.widget.Toast.LENGTH_LONG).show() }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem(
            route = Screen.Home.route,
            label = "Home",
            selectedIcon = Icons.Filled.Home,
            unselectedIcon = Icons.Outlined.Home,
            testTag = "nav_home"
        ),
        BottomNavItem(
            route = Screen.Documents.route,
            label = "Documents",
            selectedIcon = Icons.Filled.Description,
            unselectedIcon = Icons.Outlined.Description,
            testTag = "nav_documents"
        ),
        BottomNavItem(
            route = Screen.Tools.route,
            label = "Tools",
            selectedIcon = Icons.Filled.Build,
            unselectedIcon = Icons.Outlined.Build,
            testTag = "nav_tools"
        ),
        BottomNavItem(
            route = Screen.AiAssistant.route,
            label = "AI Assistant",
            selectedIcon = Icons.Filled.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            testTag = "nav_ai_assistant"
        ),
        BottomNavItem(
            route = Screen.Settings.route,
            label = "Settings",
            selectedIcon = Icons.Filled.Settings,
            unselectedIcon = Icons.Outlined.Settings,
            testTag = "nav_settings"
        )
    )

    // Hide bottom navigation on camera scanner and review screens for immersive full-screen UX
    val isBottomBarVisible = currentRoute in listOf(
        Screen.Home.route,
        Screen.Documents.route,
        Screen.Tools.route,
        Screen.AiAssistant.route,
        Screen.Settings.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            AnimatedVisibility(
                visible = isBottomBarVisible,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val isSelected = currentRoute == item.route
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != item.route) {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = {
                                Text(
                                    item.label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                                )
                            },
                            colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            // Home Dashboard
            composable(Screen.Home.route) {
                HomeScreen(
                    repository = repository,
                    onStartScan = { mode ->
                        ScanningSessionManager.startNewSession(mode)
                        if (mode == com.example.data.model.ScanMode.BURST_SCAN || mode == com.example.data.model.ScanMode.WIDE_CAMERA) {
                            // Our own camera: Burst Scan and ultra-wide manual shooting
                            navController.navigate(Screen.CameraScanner.route)
                        } else {
                            launchGoogleScanner(if (mode == com.example.data.model.ScanMode.ID_CARD) 2 else 30)
                        }
                    },
                    onOpenDocument = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId))
                    },
                    onNavigateToDocuments = {
                        navController.navigate(Screen.Documents.route)
                    },
                    onNavigateToTools = {
                        navController.navigate(Screen.Tools.route)
                    }
                )
            }

            // Documents Library
            composable(Screen.Documents.route) {
                DocumentsScreen(
                    repository = repository,
                    onOpenDocument = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId))
                    }
                )
            }

            // PDF Toolkit
            composable(Screen.Tools.route) {
                PdfToolsScreen(
                    repository = repository,
                    onOpenDocument = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId))
                    }
                )
            }

            // AI Document Assistant
            composable(Screen.AiAssistant.route) {
                AiAssistantScreen(repository = repository)
            }

            // Settings
            composable(Screen.Settings.route) {
                SettingsScreen()
            }

            // Camera Continuous Scanner
            composable(Screen.CameraScanner.route) {
                CameraScannerScreen(
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onFinishScanning = {
                        navController.navigate(Screen.DocumentReview.route)
                    }
                )
            }

            // Document Review Screen (multi-page reorder, rotate, filter, export)
            composable(Screen.DocumentReview.route) {
                DocumentReviewScreen(
                    repository = repository,
                    onNavigateBackToCamera = {
                        navController.popBackStack()
                    },
                    onAddPages = {
                        // Pages came from our own camera: go back to it. Otherwise add more with Google's scanner.
                        if (navController.previousBackStackEntry?.destination?.route == Screen.CameraScanner.route) {
                            navController.popBackStack()
                        } else {
                            launchGoogleScanner(30)
                        }
                    },
                    onFinishAndGoHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                    },
                    onOpenDocumentDetail = { docId ->
                        navController.navigate(Screen.DocumentDetail.createRoute(docId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // Document Detail Viewer
            composable(
                route = Screen.DocumentDetail.route,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getString("documentId") ?: ""
                DocumentDetailScreen(
                    documentId = docId,
                    repository = repository,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
}
