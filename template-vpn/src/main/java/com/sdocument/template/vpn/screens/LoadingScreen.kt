package com.sdocument.template.vpn.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun LoadingScreen(onLoadingFinished: () -> Unit) {
    val steps = listOf("Подготовка конфигурации...", "Поиск оптимальных серверов...", "Финальные штрихи...")
    var currentStep by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val totalTime = 12000L // 12 seconds
        val stepDuration = totalTime / steps.size
        
        for (i in steps.indices) {
            currentStep = i
            delay(stepDuration)
        }
        onLoadingFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = steps[currentStep],
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
