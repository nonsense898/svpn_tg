package com.sdocument.template.vpn.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sdocument.template.vpn.models.WgTunnel
import com.sdocument.template.vpn.ui.vpn.AppThemeStyle
import com.sdocument.template.vpn.ui.vpn.LocalAppThemeStyle
import com.sdocument.template.vpn.viewmodel.VpnViewModel

@Composable
fun VpnServersScreen(
    viewModel: VpnViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onServerSelected: (WgTunnel) -> Unit
) {
    val themeStyle = LocalAppThemeStyle.current
    val tunnels by viewModel.tunnels.collectAsState()

    Scaffold(
        bottomBar = {
            VPNBottomNav(
                themeStyle = themeStyle,
                currentScreen = "SERVERS",
                onNavigate = { route ->
                    when (route) {
                        "MAIN" -> onBack()
                        "SETTINGS" -> onOpenSettings()
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
                Text("Select Server", style = MaterialTheme.typography.titleLarge)
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(tunnels) { tunnel ->
                    VPNServerItem(tunnel.name, tunnel.serverEndpoint, themeStyle) {
                        onServerSelected(tunnel)
                    }
                }
            }
        }
    }
}

@Composable
fun VPNServerItem(country: String, ip: String, themeStyle: AppThemeStyle, onClick: () -> Unit) {
    val shape = when (themeStyle) {
        AppThemeStyle.TELEGRAM -> RoundedCornerShape(12.dp)
        AppThemeStyle.MILITARY -> RoundedCornerShape(2.dp)
        AppThemeStyle.REGULATORY -> RoundedCornerShape(0.dp)
    }

    val isMilitary = themeStyle == AppThemeStyle.MILITARY

    Card(
        shape = shape,
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(if(isMilitary) 1.dp else 0.dp, MaterialTheme.colorScheme.primary.copy(alpha=0.3f), shape)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(country, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text(ip, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f))
        }
    }
}
