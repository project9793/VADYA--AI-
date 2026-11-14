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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidya.ai.ui.assistant.GuideAction

/** "Go there" card shown by the guide. During the tour it only offers Stop. */
@Composable
fun GuideCard(g: GuideAction, onGo: (String) -> Unit, onDone: () -> Unit, onStopTour: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🧭 ${g.title}", style = MaterialTheme.typography.titleMedium)
            if (g.text.isNotBlank()) Text(g.text, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (g.tour) {
                    Button(onClick = onStopTour) { Text("रोकें") }
                } else {
                    val r = g.route
                    if (r != null) Button(onClick = { onGo(r) }) { Text("वहाँ ले चलो") }
                    OutlinedButton(onClick = onDone) { Text("ठीक है") }
                }
            }
        }
    }
}
