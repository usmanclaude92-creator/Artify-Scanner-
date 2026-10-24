package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Documents : Screen("documents")
    object Tools : Screen("tools")
    object AiAssistant : Screen("ai_assistant")
    object Settings : Screen("settings")
    object BurstCamera : Screen("burst_camera")
    object Profile : Screen("profile")
    object DocumentReview : Screen("document_review")
    object DocumentDetail : Screen("document_detail/{documentId}") {
        fun createRoute(documentId: String) = "document_detail/$documentId"
    }
}
