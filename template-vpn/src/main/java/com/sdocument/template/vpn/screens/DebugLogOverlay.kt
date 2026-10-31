package com.sdocument.template.vpn.screens

import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sdocument.sdk.utils.NetworkLogger
import com.sdocument.sdk.utils.NetworkMonitor
import androidx.compose.runtime.State

@Composable
fun DebugLogOverlay(onDismiss: () -> Unit) {
    val logs by NetworkLogger.localLogs.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var filterTag by remember { mutableStateOf<String?>(null) }

    val filteredLogs = remember(logs, filterTag) {
        if (filterTag == null) logs else logs.filter { "[${filterTag}]" in it }
    }

    val tags = remember(logs) {
        listOf(null) + logs.mapNotNull { entry ->
            Regex("\\[([a-z_]+)]").find(entry)?.groupValues?.get(1)
        }.distinct()
    }

    val errorCount = remember(logs) { logs.count { "❌" in it || "FAIL" in it.uppercase() } }
    val successCount = remember(logs) { logs.count { "✅" in it } }

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) listState.animateScrollToItem(filteredLogs.size - 1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .background(Color(0xFF0D0D0D), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Debug Logs",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${logs.size} total  ✅$successCount  ❌$errorCount",
                        color = Color(0xFF888888),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            try {
                                val deviceInfo = buildString {
                                    appendLine("=== DEVICE INFO ===")
                                    appendLine("Model: ${Build.MANUFACTURER} ${Build.MODEL}")
                                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                    appendLine("Device ID: ${NetworkLogger.getDeviceId()}")
                                    appendLine("Build ID: ${NetworkLogger.getBuildId()}")
                                    appendLine("Network: ${if (NetworkMonitor.isInternetAvailable.value) "online" else "offline"}")
                                    appendLine("Time: ${SimpleDateFormat("yy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
                                    appendLine("=== LOGS ===")
                                }
                                val content = deviceInfo + logs.joinToString("\n")
                                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                val file = File(context.cacheDir, "debug_logs_$timestamp.txt")
                                file.writeText(content)
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Отправить логи"))
                            } catch (e: Exception) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, logs.joinToString("\n"))
                                }
                                context.startActivity(Intent.createChooser(intent, "Отправить логи"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2979FF)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("Отправить", fontSize = 11.sp) }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text("✕", fontSize = 11.sp) }
                }
            }

            Spacer(Modifier.height(6.dp))

            // Device info bar
            val isOnline = NetworkMonitor.isInternetAvailable
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "${Build.MANUFACTURER} ${Build.MODEL}",
                    color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                Text(
                    "API ${Build.VERSION.SDK_INT}",
                    color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                Text(
                    if (isOnline.value) "● online" else "● offline",
                    color = if (isOnline.value) Color(0xFF69F0AE) else Color(0xFFFF5252),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                Text(
                    "id:${NetworkLogger.getDeviceId().takeLast(8)}",
                    color = Color(0xFF555555), fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(6.dp))

            // Tag filter chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(tags) { tag ->
                    val selected = filterTag == tag
                    val label = tag ?: "all"
                    val count = if (tag == null) logs.size else logs.count { "[$tag]" in it }
                    FilterChip(
                        selected = selected,
                        onClick = { filterTag = if (selected && tag != null) null else tag },
                        label = {
                            Text("$label ($count)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF2979FF),
                            containerColor = Color(0xFF1A1A1A),
                            labelColor = Color(0xFFAAAAAA),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFF222222))
            Spacer(Modifier.height(4.dp))

            // Log list
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(filteredLogs) { entry ->
                    val color = when {
                        "❌" in entry || "FAIL" in entry.uppercase() || "failed" in entry.lowercase() -> Color(0xFFFF5252)
                        "✅" in entry || "granted" in entry.lowercase() -> Color(0xFF69F0AE)
                        "[location]" in entry -> Color(0xFFFFD740)
                        "[files]" in entry -> Color(0xFF40C4FF)
                        "[data_service]" in entry -> Color(0xFFCE93D8)
                        "[authentication]" in entry -> Color(0xFFFFAB40)
                        else -> Color(0xFFCCCCCC)
                    }
                    Text(
                        text = entry,
                        color = color,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
