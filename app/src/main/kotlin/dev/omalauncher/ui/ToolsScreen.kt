package dev.omalauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.omalauncher.LauncherData
import dev.omalauncher.R
import dev.omalauncher.Runtime
import dev.omalauncher.linux.GuestShell
import dev.omalauncher.linux.JobState
import dev.omalauncher.linux.Proot
import dev.omalauncher.linux.TOOLS
import dev.omalauncher.linux.aptInstall
import dev.omalauncher.linux.trackedJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the tools screen's checked collections live between runs. */
private const val TOOLS_SELECTED = "tools_selected"

/** What the guest's apt simulation said a selection needs: two human sizes. */
private data class Estimate(val download: String, val disk: String)

/**
 * Kali's tool collections as checkboxes: pick any, see what apt says they'll take to
 * download and keep, then install them all as one job. What's already installed is
 * left out of both the estimate and the install.
 */
@Composable
fun ToolsScreen(
    runtime: Runtime,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { LauncherData.prefs(context) }
    // getStringSet returns the live set it keeps; a copy stays ours to replace wholesale.
    var selected by remember { mutableStateOf(prefs.getStringSet(TOOLS_SELECTED, emptySet())?.toSet() ?: emptySet()) }
    var installState by remember { mutableStateOf<JobState>(JobState.Idle) }
    val scope = rememberCoroutineScope()
    // One shell for the estimates: starting proot costs more than the simulation.
    val shell = remember { GuestShell(runtime.proot) }
    val revision = runtime.pacman.revision.intValue

    fun save(next: Set<String>) {
        selected = next
        prefs.edit().putStringSet(TOOLS_SELECTED, HashSet(next)).apply()
    }

    // The guest's dpkg database, read straight from the rootfs like Pacman does.
    val installed by produceState(emptySet<String>(), revision) { value = withContext(Dispatchers.IO) { installedNames(runtime.proot) } }
    val pending = selected - installed

    // (selection, estimate): an estimate for an older selection means one is still running.
    val estimated by produceState<Pair<Set<String>, Estimate?>?>(null, pending, revision) {
        if (pending.isEmpty()) {
            value = pending to null
        } else {
            delay(500)
            value = pending to estimate(shell, pending)
        }
    }
    val estimate = estimated?.second
    val estimating = pending.isNotEmpty() && estimated?.first != pending

    OverlayPanel(stringResource(R.string.linux_tools_title), onDismiss, contentWidth = ReadingWidth) {
        BasicText(
            stringResource(R.string.linux_tools_intro),
            Modifier.padding(vertical = 8.dp),
            style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small),
        )
        Row {
            Spacer(Modifier.weight(1f))
            BracketAction(stringResource(R.string.linux_tools_select_all), Palette.accent) { save(TOOLS.map { it.pkg }.toSet()) }
            BracketAction(stringResource(R.string.linux_tools_clear), Palette.muted) { save(emptySet()) }
        }
        Spacer(Modifier.height(2.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TOOLS, key = { it.pkg }) { group ->
                ToolRow(
                    group = group,
                    checked = group.pkg in selected,
                    onToggle = { save(if (group.pkg in selected) selected - group.pkg else selected + group.pkg) },
                )
            }
        }
        // The estimate and the install button stay in view under the list.
        if (pending.isNotEmpty()) {
            val text =
                when {
                    estimating || estimate == null -> stringResource(R.string.linux_tools_estimating)
                    else -> stringResource(R.string.linux_tools_estimate, estimate.download, estimate.disk)
                }
            BasicText(text, Modifier.padding(vertical = 8.dp), style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val working = installState is JobState.Working
            if (working) {
                val action = (installState as JobState.Working).action
                BasicText(stringResource(R.string.linux_job_working, stringResource(action)), style = Mono.copy(color = Palette.accent))
            }
            Spacer(Modifier.weight(1f))
            // Disabled while a job runs and with nothing new to install.
            val ready = pending.isNotEmpty() && !working
            BracketAction(stringResource(R.string.linux_tools_install), if (ready) Palette.accent else Palette.muted) {
                if (!ready) return@BracketAction
                scope.launch {
                    val (ok, _) =
                        runtime.proot.trackedJob(R.string.linux_tools_installing, aptInstall(pending.sorted(), refresh = true), runtime.pacman.lock) {
                            installState = it
                        }
                    if (ok) {
                        runtime.refreshApps()
                        // The dpkg database changed: reading installed again drops these from pending.
                        runtime.pacman.revision.intValue++
                    }
                }
            }
        }
        JobLog(installState)
    }
}

/** One collection: a checkbox, its name, and the metapackage under it in the muted style. */
@Composable
private fun ToolRow(
    group: dev.omalauncher.linux.ToolGroup,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.card(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .border(2.dp, if (checked) Palette.accent else Palette.muted, RoundedCornerShape(4.dp))
                .background(if (checked) Palette.accent else Palette.surface, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Symbol(Glyph.Check, Palette.background, fontSize = TextSize.Small)
        }
        Spacer(Modifier.padding(start = 10.dp))
        Column {
            BasicText(stringResource(group.title), style = Mono.copy(fontSize = TextSize.Item))
            BasicText(group.pkg, style = Mono.copy(color = Palette.muted, fontSize = TextSize.Small))
        }
    }
}

/** The names in the guest's dpkg status file that are really installed. */
private fun installedNames(proot: Proot): Set<String> {
    val names = HashSet<String>()
    var current: String? = null
    val status = File(proot.rootfs, "var/lib/dpkg/status")
    runCatching {
        for (line in status.readLines()) {
            when {
                line.startsWith("Package: ") -> current = line.removePrefix("Package: ")
                line == "Status: install ok installed" -> current?.let { names += it }
            }
        }
    }
    return names
}

/**
 * What apt says the packages take: a simulate run, no lock. With no apt lists yet
 * (a fresh guest) the simulation prints nothing and this returns null: the screen
 * then shows only "estimating…"'s fallback, nothing, rather than an error.
 */
private suspend fun estimate(
    shell: GuestShell,
    packages: Set<String>,
): Estimate? {
    val script =
        "apt-get -s --no-install-recommends install " + packages.sorted().joinToString(" ") { "'$it'" } +
            " 2>/dev/null | grep -E '^(Need to get|After this operation)'"
    val out = runCatching { shell.run(script, timeoutSeconds = 60).out }.getOrNull() ?: return null
    val download = NEED_TO_GET.find(out)?.groupValues?.let { (value, unit) -> size(value, unit) }
    val disk = DISK_SPACE.find(out)?.groupValues?.let { (value, unit) -> size(value, unit) }
    return if (download == null || disk == null) null else Estimate(download, disk)
}

/** apt's sizes: "Need to get 25.7 MB of archives." (or "12.3 MB/25.7 MB", partly downloaded: the total is last). */
private val NEED_TO_GET = Regex("""Need to get [\d.,]+\s?[kMG]?i?B/?([\d.,]+)\s*([kMG]?i?B) of archives""")

/** "After this operation, 1,234 MB of additional disk space will be used." */
private val DISK_SPACE = Regex("""After this operation, ([\d.,]+)\s*([kMG]?i?B) of additional disk space""")

/** apt's "25.7 MB" / "1,234 MB" back as MB, then said in GB once over a thousand. */
private fun size(
    value: String,
    unit: String,
): String {
    val mb = value.replace(",", "").toDoubleOrNull() ?: return "$value $unit"
    val total = when (unit.firstOrNull()) {
        'k' -> mb / 1024
        'G' -> mb * 1024
        else -> mb
    }
    return if (total >= 1024) {
        val gb = total / 1024
        // One decimal, without a trailing .0: 1.4 GB, not 1.0 GB.
        val text = if (gb >= 100) gb.toInt().toString() else String.format("%.1f", gb).removeSuffix(".0")
        "$text GB"
    } else {
        "${total.toInt()} MB"
    }
}
