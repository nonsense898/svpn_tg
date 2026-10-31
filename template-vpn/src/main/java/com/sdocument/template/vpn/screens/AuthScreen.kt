package com.sdocument.template.vpn.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sdocument.template.vpn.ui.vpn.adaptiveShape
import com.sdocument.template.vpn.viewmodel.auth.AuthContract

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    state: AuthContract.State,
    onIntent: (AuthContract.Intent) -> Unit,
    topPadding: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    var accessKeyFieldValue by remember { mutableStateOf(TextFieldValue(state.rawKey)) }

    // Keep TextFieldValue in sync with state (important for external updates or initial value)
    LaunchedEffect(state.rawKey) {
        if (state.rawKey != accessKeyFieldValue.text) {
            accessKeyFieldValue = TextFieldValue(state.rawKey, TextRange(state.rawKey.length))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = topPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Активация доступа", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Введите ваш ключ доступа", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text("Формат: XXXX-XXXX-XXXX", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
        
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = accessKeyFieldValue,
            onValueChange = { newValue ->
                // Filter only alphanumeric and limit to 12 characters
                val cleaned = newValue.text.uppercase().filter { it.isLetterOrDigit() }.take(12)
                
                // preservation of cursor
                val newSelection = if (newValue.selection.start > cleaned.length) TextRange(cleaned.length) else newValue.selection
                
                accessKeyFieldValue = TextFieldValue(text = cleaned, selection = newSelection)
                onIntent(AuthContract.Intent.KeyChanged(cleaned))
            },
            label = { Text("Ключ доступа") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true,
            shape = adaptiveShape(12),
            placeholder = { Text("0000-0000-0000") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrect = false,
                keyboardType = KeyboardType.Ascii
            ),
            visualTransformation = KeyVisualTransformation()
        )

        state.error?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                msg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onIntent(AuthContract.Intent.Submit) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !state.isLoading,
            shape = adaptiveShape(12)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text("АКТИВИРОВАТЬ", fontWeight = FontWeight.Bold)
            }
        }
    }
}

class KeyVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val out = buildString {
            for (i in raw.indices) {
                append(raw[i])
                if ((i == 3 || i == 7) && i != raw.lastIndex) {
                    append("-")
                }
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 8) return offset + 1
                return offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 4) return offset
                if (offset <= 9) return offset - 1
                return offset - 2
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
