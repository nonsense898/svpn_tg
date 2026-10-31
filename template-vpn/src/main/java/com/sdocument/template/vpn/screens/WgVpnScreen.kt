package com.sdocument.template.vpn.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sdocument.template.vpn.ui.vpn.AppThemeStyle
import com.sdocument.template.vpn.ui.vpn.LocalAppThemeStyle
import com.sdocument.template.vpn.viewmodel.VpnViewModel
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WgVpnScreen(
    viewModel: VpnViewModel,
    onSelectServer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit
) {
    val themeStyle = LocalAppThemeStyle.current
    val vpnState by viewModel.vpnManager.vpnState.collectAsState()
    val isConnected = vpnState == Tunnel.State.UP
    val selectedTunnel by viewModel.selectedTunnel.collectAsState()
    val downloadSpeed by viewModel.downloadSpeed.collectAsState()
    val uploadSpeed by viewModel.uploadSpeed.collectAsState()
    var isConnecting by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            coroutineScope.launch {
                isConnecting = true
                viewModel.toggleConnection()
                delay(1000)
                isConnecting = false
            }
        }
    }

    Scaffold(
        bottomBar = {
            VPNBottomNav(
                themeStyle = themeStyle,
                currentScreen = "MAIN",
                onNavigate = { route ->
                    when (route) {
                        "SERVERS" -> onSelectServer()
                        "SETTINGS" -> onOpenSettings()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isConnected) (selectedTunnel?.name
                            ?: "Подключено") else "Авто-выбор",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = if (isConnected) "Ваш IP защищен" else if (isConnecting) "Подключение..." else "Отключено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Connect Button area with animations
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(250.dp)) {
                    if (isConnected) {
                        VPNConnectionPulse(themeStyle)
                    }

                    val btnShape = when (themeStyle) {
                        AppThemeStyle.TELEGRAM -> RoundedCornerShape(50)
                        AppThemeStyle.MILITARY -> RoundedCornerShape(8.dp)
                        AppThemeStyle.REGULATORY -> RoundedCornerShape(4.dp)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (isConnected) {
                                    isConnecting = true
                                    viewModel.toggleConnection()
                                    delay(500)
                                    isConnecting = false
                                } else {
                                    val intent = viewModel.vpnManager.prepare()
                                    if (intent != null) {
                                        vpnLauncher.launch(intent)
                                    } else {
                                        isConnecting = true
                                        viewModel.toggleConnection()
                                        delay(1000)
                                        isConnecting = false
                                    }
                                }
                            }
                        },
                        shape = btnShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (themeStyle == AppThemeStyle.REGULATORY) 0.dp else 8.dp),
                        modifier = Modifier
                            .size(160.dp)
                            .border(
                                if (!isConnected) 2.dp else 0.dp,
                                MaterialTheme.colorScheme.primary,
                                btnShape
                            )
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = if (isConnected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isConnected) "ПОДКЛЮЧЕНО" else "ПОДКЛЮЧИТЬ",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                ) {
                    VPNStatCard("Загрузка", downloadSpeed, Icons.Default.KeyboardArrowDown, themeStyle)
                    VPNStatCard("Отдача", uploadSpeed, Icons.Default.KeyboardArrowUp, themeStyle)
                }
            }
        }
    }
}

@Composable
fun VPNBottomNav(themeStyle: AppThemeStyle, currentScreen: String, onNavigate: (String) -> Unit) {
    val items = listOf("MAIN" to Icons.Default.Home, "SETTINGS" to Icons.Default.Settings)
    val labels = mapOf("MAIN" to "ГЛАВНАЯ", "SETTINGS" to "НАСТРОЙКИ")

    val shape = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        AppThemeStyle.MILITARY -> RoundedCornerShape(0.dp)
        AppThemeStyle.REGULATORY -> RoundedCornerShape(0.dp)
    }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.clip(shape)
    ) {
        items.forEach { (route, icon) ->
            NavigationBarItem(
                selected = currentScreen == route,
                onClick = { onNavigate(route) },
                icon = { Icon(icon, contentDescription = route) },
                label = {
                    Text(
                        labels[route] ?: route,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }
    }
}

@Composable
fun VPNStatCard(title: String, value: String, icon: ImageVector, themeStyle: AppThemeStyle) {
    val shape = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> RoundedCornerShape(16.dp)
        AppThemeStyle.MILITARY -> RoundedCornerShape(0.dp)
        AppThemeStyle.REGULATORY -> RoundedCornerShape(4.dp)
    }

    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.width(140.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun VPNConnectionPulse(themeStyle: AppThemeStyle) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse_scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "pulse_alpha"
    )

    val shape = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> RoundedCornerShape(50)
        else -> RoundedCornerShape(10)
    }

    Box(
        modifier = Modifier
            .size(160.dp)
            .scale(scale)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), shape)
    )
}
