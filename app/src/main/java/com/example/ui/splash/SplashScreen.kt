package com.example.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.BrandCyan
import com.example.ui.theme.InterFamily

/** Dark-blue brand splash shown for a moment when the app starts. */
@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0A2A66), Color(0xFF051634), Color(0xFF030B1C)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_icon),
                contentDescription = "Artify Scanner",
                modifier = Modifier.size(150.dp).clip(RoundedCornerShape(34.dp))
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Artify ") }
                    withStyle(SpanStyle(color = BrandCyan)) { append("Scanner") }
                },
                fontFamily = InterFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Scan. Organize. Do More.",
                color = Color(0xFFB9CBEF),
                fontFamily = InterFamily,
                fontSize = 15.sp
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Your documents,\nperfectly captured.",
                color = Color(0xFF9FB4DD),
                fontFamily = InterFamily,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.width(22.dp).height(5.dp).clip(CircleShape).background(BrandCyan))
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF2B4A85)))
                Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF2B4A85)))
            }
        }
    }
}
