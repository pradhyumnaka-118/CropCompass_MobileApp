package com.cropcompass.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag

@Composable fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
}
@Composable fun Heading(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun Notice(text: String, error: Boolean = false) {
    Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Choice(label: String, value: String, options: List<String>, tag: String = label, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { if (options.isNotEmpty()) open = !open }) {
        OutlinedTextField(value, {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(open) }, modifier = Modifier.menuAnchor().fillMaxWidth().testTag("select-$tag"))
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); open = false }) }
        }
    }
}
