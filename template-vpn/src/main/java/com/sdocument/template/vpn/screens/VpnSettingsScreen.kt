package com.sdocument.template.vpn.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sdocument.template.vpn.ui.vpn.AppThemeStyle
import com.sdocument.template.vpn.ui.vpn.LocalAppThemeStyle

@Composable
fun VpnSettingsScreen(
    skipAuth: Boolean,
    onBack: () -> Unit,
    onOpenServers: () -> Unit,
    onLogout: () -> Unit
) {
    val themeStyle = LocalAppThemeStyle.current

    Scaffold(
        bottomBar = {
            VPNBottomNav(
                themeStyle = themeStyle,
                currentScreen = "SETTINGS",
                onNavigate = { route ->
                    when (route) {
                        "MAIN" -> onBack()
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text("Настройки", style = MaterialTheme.typography.titleLarge)
            }

            Column(modifier = Modifier.padding(16.dp)) {
                VPNSwitchItem("Авто-подключение", "Подключаться при запуске приложения", true, themeStyle)
                VPNSwitchItem("Kill Switch", "Блокировать интернет при разрыве VPN", false, themeStyle)
                Spacer(Modifier.height(16.dp))
                Text("Протокол", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
                VPNSettingItem("WireGuard", "Текущий протокол", themeStyle)

                if (!skipAuth) {
                    Spacer(Modifier.height(32.dp))
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Выйти")
                    }
                }
            }
        }
    }
}

@Composable
fun VPNSwitchItem(title: String, subtitle: String, initial: Boolean, themeStyle: AppThemeStyle) {
    var checked by remember { mutableStateOf(initial) }
    val isRegulatory = themeStyle == AppThemeStyle.REGULATORY

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).border(if(isRegulatory) 1.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(0.1f)).padding(if(isRegulatory) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(
            checked = checked,
            onCheckedChange = { checked = it },
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary, checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha=0.3f))
        )
    }
}

@Composable
fun VPNSettingItem(title: String, subtitle: String, themeStyle: AppThemeStyle) {
    val isRegulatory = themeStyle == AppThemeStyle.REGULATORY
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).border(if(isRegulatory) 1.dp else 0.dp, MaterialTheme.colorScheme.onSurface.copy(0.1f)).padding(if(isRegulatory) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f))
    }
}
