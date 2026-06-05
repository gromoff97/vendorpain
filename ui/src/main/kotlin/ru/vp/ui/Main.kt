package ru.vp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.vp.bitbucket.BitbucketUser
import ru.vp.config.Auth
import java.nio.file.Path
import javax.swing.JFileChooser

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "VP",
        state = rememberWindowState(width = 1280.dp, height = 900.dp),
    ) {
        MaterialTheme(
            colors = MaterialTheme.colors.copy(
                primary = BitbucketTheme.primary,
                secondary = BitbucketTheme.primary,
                background = BitbucketTheme.background,
                surface = BitbucketTheme.surface,
                error = BitbucketTheme.error,
                onPrimary = Color.White,
                onSurface = BitbucketTheme.text,
                onBackground = BitbucketTheme.text,
            ),
        ) {
            Surface(Modifier.fillMaxSize(), color = BitbucketTheme.background) {
                VpApp()
            }
        }
    }
}

@Composable
private fun VpApp(state: AppState = remember { AppState() }) {
    val scope = rememberCoroutineScope()

    LaunchedEffect(
        state.query,
        state.form.bitbucketBaseUrl,
        state.form.authMethod,
        state.form.username,
        state.form.password,
        state.form.token,
        state.form.insecure,
        state.form.sshEnabled,
    ) {
        delay(300)
        runCatching { withContext(Dispatchers.IO) { state.search() } }
            .onSuccess(state::show)
            .onFailure(state::fail)
    }

    Column(Modifier.fillMaxSize()) {
        AppHeader(state)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionSection(state)
            PeopleGroupsSection(state)
            ExportOptionsSection(state)
            OutputSection(state)
            AdvancedSection(state)
            RunSection(state) {
                scope.launch {
                    val config = runCatching(state::start).getOrElse {
                        state.fail(it)
                        return@launch
                    }
                    runCatching { withContext(Dispatchers.IO) { state.export(config) } }
                        .onSuccess(state::done)
                        .onFailure(state::fail)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(state: AppState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BitbucketTheme.header)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Bitbucket", color = Color.White, style = MaterialTheme.typography.h6, fontWeight = FontWeight.SemiBold)
            Text("VP CSV export", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.body2)
        }
        Text(
            state.currentConfigPath?.fileName?.toString() ?: "No config loaded",
            color = Color.White.copy(alpha = 0.82f),
            style = MaterialTheme.typography.body2,
        )
    }
}

@Composable
private fun ConnectionSection(state: AppState) = Section("Connection") {
    Field("Bitbucket URL", state.form.bitbucketBaseUrl) { state.form = state.form.copy(bitbucketBaseUrl = it) }
    Field("Awesome Graphs API URL", state.form.graphsBaseUrl) { state.form = state.form.copy(graphsBaseUrl = it) }
    Check("Insecure TLS", state.form.insecure) { state.form = state.form.copy(insecure = it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Select("Auth", state.form.authMethod, listOf(Auth.BASIC, Auth.TOKEN), Modifier.width(150.dp)) {
            state.form = state.form.copy(authMethod = it)
        }
        if (state.form.authMethod == Auth.BASIC) {
            Field("Username", state.form.username, Modifier.weight(1f)) { state.form = state.form.copy(username = it) }
            SecretField("Password", state.form.password, Modifier.weight(1f)) { state.form = state.form.copy(password = it) }
        } else {
            SecretField("Token", state.form.token, Modifier.weight(1f)) { state.form = state.form.copy(token = it) }
        }
    }
}

@Composable
private fun PeopleGroupsSection(state: AppState) = Section("People and Groups") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Field("Search people", state.query, Modifier.weight(1f)) { state.query = it }
        SecondaryButton("Add group") {
            state.form = state.form.copy(groups = state.form.groups + UiExportGroup(name = "Group ${state.form.groups.size + 1}"))
            state.activeGroupIndex = state.form.groups.lastIndex
        }
    }

    if (state.suggestions.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.suggestions.forEach { user ->
                SecondaryButton("${user.displayName.ifBlank { user.slug }}  ${user.slug}") { state.select(user) }
            }
        }
    }

    state.form.groups.forEachIndexed { index, group ->
        GroupRow(state, index, group)
    }
}

@Composable
private fun GroupRow(state: AppState, index: Int, group: UiExportGroup) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, if (index == state.activeGroupIndex) BitbucketTheme.primary else BitbucketTheme.border), RoundedCornerShape(4.dp))
            .background(BitbucketTheme.subtleSurface, RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { state.activeGroupIndex = index }) {
                Text(if (index == state.activeGroupIndex) "Active" else "Select")
            }
            Field("Group", group.name, Modifier.width(180.dp)) { value -> state.updateGroup(index, group.copy(name = value)) }
            Field("Path segments, separated by /", group.pathText, Modifier.weight(1f)) { value ->
                state.updateGroup(index, group.copy(pathText = value))
            }
            if (state.form.groups.size > 1) {
                TextButton(onClick = { state.removeGroup(index) }) { Text("Remove") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            group.users.forEach { user -> UserChip(user) { state.removeFromGroup(index, user) } }
            if (group.users.isEmpty()) {
                Text("No users selected", color = BitbucketTheme.muted, style = MaterialTheme.typography.body2)
            }
        }
    }
}

@Composable
private fun ExportOptionsSection(state: AppState) = Section("Export Options") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Field("Since", state.form.sinceDate, Modifier.weight(1f)) { state.form = state.form.copy(sinceDate = it) }
        Field("Until", state.form.untilDate, Modifier.weight(1f)) { state.form = state.form.copy(untilDate = it) }
        Select("Merges", state.form.merges, listOf("exclude", "include", "only"), Modifier.width(150.dp)) { state.form = state.form.copy(merges = it) }
        Select("Order", state.form.order, listOf("newest", "oldest"), Modifier.width(140.dp)) { state.form = state.form.copy(order = it) }
    }
}

@Composable
private fun OutputSection(state: AppState) = Section("Output") {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Field("Directory", state.form.outputDir, Modifier.weight(1f)) { state.form = state.form.copy(outputDir = it) }
        SecondaryButton("Browse") { chooseDir(state.form.outputDir)?.let { state.form = state.form.copy(outputDir = it) } }
        Check("Archive", state.form.archive) { state.form = state.form.copy(archive = it) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton("Load config") { chooseYaml(state.currentConfigPath, save = false)?.let(state::load) }
        SecondaryButton("Save config") { runCatching { state.save() }.onFailure(state::fail) }
        SecondaryButton("Save config as") { chooseYaml(state.currentConfigPath, save = true)?.let(state::saveAs) }
    }
}

@Composable
private fun AdvancedSection(state: AppState) = Section("Advanced") {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Check("Route HTTP through SSH", state.form.sshEnabled) { state.form = state.form.copy(sshEnabled = it) }
        Check("Debug", state.form.debug) { state.form = state.form.copy(debug = it) }
    }
    if (state.form.sshEnabled) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Field("SSH host", state.form.sshHost, Modifier.weight(1f)) { state.form = state.form.copy(sshHost = it) }
            Field("Port", state.form.sshPort, Modifier.width(110.dp)) { state.form = state.form.copy(sshPort = it) }
            Field("User", state.form.sshUser, Modifier.weight(1f)) { state.form = state.form.copy(sshUser = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecretField("SSH password", state.form.sshPassword, Modifier.weight(1f)) { state.form = state.form.copy(sshPassword = it) }
            Field("Private key", state.form.sshPrivateKeyPath, Modifier.weight(1f)) { state.form = state.form.copy(sshPrivateKeyPath = it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            SecretField("Key passphrase", state.form.sshPassphrase, Modifier.weight(1f)) { state.form = state.form.copy(sshPassphrase = it) }
            Field("Known hosts", state.form.sshKnownHostsPath, Modifier.weight(1f)) { state.form = state.form.copy(sshKnownHostsPath = it) }
            Check("Strict hosts", state.form.sshStrictHostKeyChecking) { state.form = state.form.copy(sshStrictHostKeyChecking = it) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Field("Timeout", state.form.timeoutSeconds, Modifier.width(120.dp)) { state.form = state.form.copy(timeoutSeconds = it) }
        Field("Retries", state.form.retries, Modifier.width(120.dp)) { state.form = state.form.copy(retries = it) }
    }
}

@Composable
private fun RunSection(state: AppState, export: () -> Unit) = Section("Run") {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(if (state.running) "Exporting" else "Export", enabled = !state.running, onClick = export)
        if (state.progress.total > 0) {
            Text("${state.progress.done}/${state.progress.total}", color = BitbucketTheme.muted)
        }
    }
    if (state.running || state.progress.total > 0) {
        val value = if (state.progress.total == 0) 0f else state.progress.done.toFloat() / state.progress.total
        LinearProgressIndicator(progress = value, modifier = Modifier.fillMaxWidth(), color = BitbucketTheme.primary)
        Text(state.progress.slug?.let { "Exporting $it" } ?: "Idle", color = BitbucketTheme.muted)
    }
    if (state.message.isNotBlank()) {
        Text(state.message, color = if (state.running) BitbucketTheme.muted else BitbucketTheme.text)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BitbucketTheme.surface, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, BitbucketTheme.border), RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold, color = BitbucketTheme.text)
        content()
    }
}

@Composable
private fun UserChip(user: BitbucketUser, remove: () -> Unit) {
    OutlinedButton(onClick = remove, border = BorderStroke(1.dp, BitbucketTheme.border)) {
        Text("${user.slug} x", color = BitbucketTheme.primary)
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun SecretField(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), change: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label) },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun Select(label: String, value: String, values: List<String>, modifier: Modifier = Modifier, change: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        SecondaryButton("$label: $value") { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { option ->
                DropdownMenuItem(onClick = {
                    change(option)
                    expanded = false
                }) {
                    Text(option)
                }
            }
        }
    }
}

@Composable
private fun Check(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = checked,
            onCheckedChange = change,
            colors = CheckboxDefaults.colors(checkedColor = BitbucketTheme.primary),
        )
        Text(label, color = BitbucketTheme.text)
    }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(backgroundColor = BitbucketTheme.primary, contentColor = Color.White),
    ) {
        Text(text)
    }
}

@Composable
private fun SecondaryButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, border = BorderStroke(1.dp, BitbucketTheme.border)) {
        Text(text, color = BitbucketTheme.primary)
    }
}

private fun chooseDir(current: String): String? {
    val chooser = JFileChooser(current.ifBlank { "." })
    chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.absolutePath
    } else {
        null
    }
}

private fun chooseYaml(current: Path?, save: Boolean): Path? {
    val chooser = JFileChooser(current?.parent?.toFile())
    chooser.selectedFile = current?.toFile()
    return when {
        save && chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION -> chooser.selectedFile.toPath()
        !save && chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION -> chooser.selectedFile.toPath()
        else -> null
    }
}
