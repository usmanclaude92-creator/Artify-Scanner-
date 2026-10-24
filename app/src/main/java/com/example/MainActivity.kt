package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthManager
import com.example.auth.NotificationCenter
import com.example.camera.MlKitScanner
import com.example.camera.ScanningSessionManager
import com.example.data.model.ScanFilter
import com.example.data.model.ScanMode
import com.example.data.repository.DocumentRepository
import com.example.security.AppLock
import com.example.ui.ai.AiAssistantScreen
import com.example.ui.auth.AuthScreen
import com.example.ui.components.AppHeader
import com.example.ui.documents.DocumentsScreen
import com.example.ui.home.HomeScreen
import com.example.ui.navigation.Screen
import com.example.ui.profile.ProfileScreen
import com.example.ui.review.DocumentReviewScreen
import com.example.ui.scan.BurstCameraScreen
import com.example.ui.scan.maxPagesFor
import com.example.ui.security.LockScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.splash.SplashScreen
import com.example.ui.theme.ArtifyScannerTheme
import com.example.ui.theme.ThemeController
import com.example.ui.tools.PdfToolsScreen
import com.example.ui.viewer.DocumentDetailScreen
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private enum class Stage { SPLASH, AUTH, APP }

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
        AuthManager.init(this)
        NotificationCenter.init(this)
        val startAtSplash = savedInstanceState == null
        setContent {
            ArtifyScannerTheme {
                Box(Modifier.fillMaxSize()) {
                    if (AppLock.locked.value) {
                        LockScreen(activity = this@MainActivity)
                    } else {
                        AppRoot(activity = this@MainActivity, startAtSplash = startAtSplash)
                    }
                }
            }
        }
    }
}

/** Splash -> (Get Started) -> sign up / log in -> app. A signed-in user passes the splash on its own. */
@Composable
private fun AppRoot(activity: FragmentActivity, startAtSplash: Boolean) {
    val context = LocalContext.current
    val profile = AuthManager.profile.value
    var stage by remember { mutableStateOf(if (startAtSplash || profile == null) Stage.SPLASH else Stage.APP) }
    var startInSignUp by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(1800)
        if (AuthManager.profile.value != null && stage == Stage.SPLASH) stage = Stage.APP
    }
    LaunchedEffect(profile) {
        if (profile != null && stage == Stage.AUTH) stage = Stage.APP
        if (profile == null && stage == Stage.APP) stage = Stage.AUTH
    }

    Box(Modifier.fillMaxSize()) {
        when (stage) {
            Stage.APP -> ArtifyScannerApp()
            Stage.AUTH -> AuthScreen(
                startInSignUp = startInSignUp,
                configured = AuthManager.isConfigured(context),
                onEmailSignUp = { name, email, pass -> AuthManager.signUpWithEmail(context, name, email, pass).exceptionOrNull()?.message },
                onEmailLogin = { email, pass -> AuthManager.signInWithEmail(context, email, pass).exceptionOrNull()?.message },
                onGoogle = { AuthManager.signInWithGoogle(activity).exceptionOrNull()?.message },
                onForgotPassword = { email -> AuthManager.sendPasswordReset(context, email).exceptionOrNull()?.message },
                onGuest = { AuthManager.continueAsGuest(context) },
                onBack = { stage = Stage.SPLASH }
            )
            Stage.SPLASH -> Unit
        }
        AnimatedVisibility(visible = stage == Stage.SPLASH, exit = fadeOut(tween(300))) {
            SplashScreen(
                showActions = profile == null,
                onGetStarted = { startInSignUp = true; stage = Stage.AUTH },
                onHaveAccount = { startInSignUp = false; stage = Stage.AUTH }
            )
        }
    }
}

@Composable
fun ArtifyScannerApp() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { DocumentRepository(context) }
    val scope = rememberCoroutineScope()

    // Seed realistic guide & sample documents on first startup
    LaunchedEffect(Unit) {
        repository.migrateCachedPageImages()
        repository.seedSampleDocumentsIfEmpty()
    }

    // Google's document scanner is the camera for every scan mode; its cropped pages join the session
    val googleScannerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        if (result.resultCode == android.app.Activity.RESULT_OK && scan != null) {
            val route = navController.currentBackStackEntry?.destination?.route
            if (route != Screen.DocumentReview.route) navController.navigate(Screen.DocumentReview.route)
            scope.launch {
                for (page in scan.pages.orEmpty()) {
                    ScanningSessionManager.addPageFromUri(context, page.imageUri, ScanFilter.ORIGINAL)
                }
            }
        }
    }

    fun launchGoogleScanner(maxPages: Int) {
        val activity = context as? android.app.Activity ?: return
        MlKitScanner.createIntentSender(
            activity = activity,
            maxPages = maxPages,
            onReady = { googleScannerLauncher.launch(IntentSenderRequest.Builder(it).build()) },
            onError = { android.widget.Toast.makeText(context, "Google scanner unavailable: $it", android.widget.Toast.LENGTH_LONG).show() }
        )
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        BottomNavItem(Screen.Home.route, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
        BottomNavItem(Screen.Documents.route, "Documents", Icons.Filled.Description, Icons.Outlined.Description, "nav_documents"),
        BottomNavItem(Screen.Tools.route, "Tools", Icons.Filled.GridView, Icons.Outlined.GridView, "nav_tools"),
        BottomNavItem(Screen.AiAssistant.route, "AI Assistant", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_ai_assistant"),
        BottomNavItem(Screen.Settings.route, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
    )

    // Header and footer only on the five main tabs
    val isMainTab = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedVisibility(visible = isMainTab) {
                AppHeader(onOpenProfile = { navController.navigate(Screen.Profile.route) })
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isMainTab,
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
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
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
            composable(Screen.Home.route) {
                HomeScreen(
                    repository = repository,
                    onStartScan = { mode ->
                        ScanningSessionManager.startNewSession(mode)
                        // Burst needs continuous auto-capture, which Google's scanner does not do
                        if (mode == ScanMode.BURST_SCAN) navController.navigate(Screen.BurstCamera.route)
                        else launchGoogleScanner(maxPagesFor(mode))
                    },
                    onOpenDocument = { docId -> navController.navigate(Screen.DocumentDetail.createRoute(docId)) },
                    onOpenReview = { navController.navigate(Screen.DocumentReview.route) },
                    onNavigateToDocuments = { navController.navigate(Screen.Documents.route) },
                    onNavigateToTools = { navController.navigate(Screen.Tools.route) }
                )
            }

            composable(Screen.Documents.route) {
                DocumentsScreen(
                    repository = repository,
                    onOpenDocument = { docId -> navController.navigate(Screen.DocumentDetail.createRoute(docId)) }
                )
            }

            composable(Screen.Tools.route) {
                PdfToolsScreen(
                    repository = repository,
                    onOpenDocument = { docId -> navController.navigate(Screen.DocumentDetail.createRoute(docId)) }
                )
            }

            composable(Screen.AiAssistant.route) { AiAssistantScreen(repository = repository) }

            composable(Screen.Settings.route) {
                SettingsScreen(onOpenProfile = { navController.navigate(Screen.Profile.route) })
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onSignOut = { AuthManager.signOut(context) }
                )
            }

            composable(Screen.BurstCamera.route) {
                BurstCameraScreen(
                    onDone = {
                        navController.navigate(Screen.DocumentReview.route) {
                            popUpTo(Screen.BurstCamera.route) { inclusive = true }
                        }
                    },
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Screen.DocumentReview.route) {
                DocumentReviewScreen(
                    repository = repository,
                    onNavigateBackToCamera = { navController.popBackStack() },
                    onAddPages = { launchGoogleScanner(ScanIntroPages.ADD_MORE) },
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

            composable(
                route = Screen.DocumentDetail.route,
                arguments = listOf(navArgument("documentId") { type = NavType.StringType })
            ) { backStackEntry ->
                val docId = backStackEntry.arguments?.getString("documentId") ?: ""
                DocumentDetailScreen(
                    documentId = docId,
                    repository = repository,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

private object ScanIntroPages {
    const val ADD_MORE = 30
}
