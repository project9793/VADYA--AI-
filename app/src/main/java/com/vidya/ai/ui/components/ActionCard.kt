package com.vidya.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidya.ai.ui.assistant.PendingAction

/** Confirmation before a call / WhatsApp message: shows exactly who and what. */
@Composable
fun ActionCard(p: PendingAction, onConfirm: (String) -> Unit, onCancel: () -> Unit) {
    var msg by remember(p) { mutableStateOf(p.message) }
    val isCall = p.kind == "call"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isCall) {
                Text("📞 Call", style = MaterialTheme.typography.titleMedium)
                Text("किसे: ${p.name.ifBlank { "—" }}   ${p.number}")
            } else {
                Text("💬 WhatsApp संदेश", style = MaterialTheme.typography.titleMedium)
                Text("किसे: ${p.name.ifBlank { "(WhatsApp में contact आप चुनेंगे)" }}   ${p.number}")
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("क्या लिखा जाएगा") }
                )
                Text("WhatsApp खुलने के बाद Send आप दबाएँगे।", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onConfirm(msg) },
                    enabled = isCall || msg.isNotBlank()
                ) { Text(if (isCall) "Call करो" else "WhatsApp खोलें") }
                OutlinedButton(onClick = onCancel) { Text("रद्द करें") }
            }
        }
    }
}
