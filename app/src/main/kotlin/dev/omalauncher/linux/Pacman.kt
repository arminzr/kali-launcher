package dev.omalauncher.linux

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import dev.omalauncher.R
import dev.omalauncher.upgrade.Managed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File

data class Package(
    val name: String,
    val version: String,
    val description: String,
    /** The Kali repo for search hits; null for the installed list. */
    val repo: String?,
    val installed: Boolean,
)

/** Installs and removes guest packages from the settings screen, one apt at a time. */
class Pacman(
    private val context: Context,
    private val proot: Proot,
) {
    /** apt holds a lock on its database; everything that runs it takes this first. */
    val lock = Mutex()

    /** Per-package install/remove progress. */
    val states = mutableStateMapOf<String, JobState>()

    /** Bumped after installs and removals so lists reload. */
    val revision = mutableIntStateOf(0)

    /**
     * Not offered for removal: what the launcher relies on (foot, fonts, the apps), plus
     * `apt` and `bash` (purging apt would take the package manager itself with it).
     * Everything else is either safe to remove or required by something, which apt refuses.
     */
    val core = CORE_PACKAGES + setOf("apt", "bash")

    /** The guest's dpkg database, readable straight from the rootfs: no proot needed. */
    private val statusDb = File(proot.rootfs, "var/lib/dpkg/status")

    /** Search hits from the Kali repos only, best name matches first. */
    suspend fun search(query: String): List<Package> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val local = localNames()
        val lower = q.lowercase()
        return searchRepos(q)
            .map { it.copy(installed = it.name in local) }
            .sortedWith(
                compareBy<Package>(
                    { it.name != lower },
                    { !it.name.startsWith(lower) },
                    { lower !in it.name },
                    { it.name },
                ),
            ).take(MAX_RESULTS)
    }

    /** `apt-cache search --names-only`: one `name - description` line per hit. */
    private suspend fun searchRepos(q: String): List<Package> {
        val out = output("apt-cache search --names-only -- ${shellQuote(q)}")
        return out.lines().mapNotNull { line ->
            val split = line.split(" - ", limit = 2)
            val name = split.first().trim()
            if (name.isEmpty() || name.contains(' ')) return@mapNotNull null
            Package(
                name = name,
                // Versions would need one `apt-cache policy` per batch; the list reads fine without.
                version = "",
                description = split.getOrElse(1) { "" }.trim(),
                repo = "kali",
                installed = false,
            )
        }
    }

    /** One entry of the dpkg status file: `Key: value` lines, continuation lines indented. */
    private fun parseStatus(lines: List<String>): List<Map<String, String>> {
        val records = mutableListOf<Map<String, String>>()
        var fields = HashMap<String, String>()
        fun flush() {
            if (fields.isNotEmpty()) records += fields
            fields = HashMap()
        }
        for (line in lines) {
            when {
                line.isEmpty() -> flush()
                line[0].isWhitespace() -> {} // A folded Description: only its first line is kept.
                else -> {
                    val key = line.substringBefore(':')
                    if (key.isNotEmpty()) fields[key] = line.substringAfter(':').trim()
                }
            }
        }
        flush()
        return records
    }

    /** Records that are really installed, not just unpacked or half-removed. */
    private fun parseStatusInstalled(): List<Map<String, String>> =
        parseStatus(runCatching { statusDb.readLines() }.getOrElse { return emptyList() })
            .filter { it["Status"] == "install ok installed" }

    /** Every installed package's name, from the dpkg status file. */
    private suspend fun localNames(): Set<String> =
        withContext(Dispatchers.IO) { parseStatusInstalled().mapTo(HashSet()) { it["Package"].orEmpty() } }

    /**
     * Manually installed packages (what `apt-mark showmanual` lists), read straight
     * from the databases: much faster than starting proot. dpkg's status file has
     * every installed package; the ones installed as dependencies are in
     * extended_states as `Auto-Installed: 1`.
     */
    suspend fun installed(): List<Package> =
        withContext(Dispatchers.IO) {
            val auto = HashSet<String>()
            runCatching {
                var name: String? = null
                for (line in File(proot.rootfs, "var/lib/apt/extended_states").readLines()) {
                    if (line.startsWith("Package: ")) name = line.removePrefix("Package: ")
                    if (line == "Auto-Installed: 1") name?.let { auto += it }
                }
            }
            parseStatusInstalled()
                .filter { it["Package"] !in auto }
                .map {
                    Package(
                        it["Package"].orEmpty(),
                        it["Version"].orEmpty(),
                        it["Description"].orEmpty(),
                        null,
                        installed = true,
                    )
                }.sortedBy { it.name }
        }

    /** Refreshes the index and installs the package, like the bootstrap: kali-rolling moves fast. */
    suspend fun install(pkg: Package) = run(pkg.name, R.string.linux_job_installing, aptInstall(listOf(pkg.name)))

    /**
     * Nothing managed here any more: the guest's extra provisioning scripts are gone.
     * Kept because Runtime.kt wires every provisioner's list into the upgrade pass.
     */
    fun managed(): List<Managed> = emptyList()

    /** Removes the package and the dependencies nothing else needs. */
    suspend fun remove(name: String) =
        run(name, R.string.linux_job_removing, "DEBIAN_FRONTEND=noninteractive apt-get purge -y --auto-remove -- ${shellQuote(name)}")

    /** Kept between searches: starting proot costs more than the search. */
    private val shell = GuestShell(proot)

    private suspend fun output(script: String): String =
        runCatching { shell.run(script, timeoutSeconds = 30).out }.getOrElse {
            Log.w(TAG, "apt query failed", it)
            ""
        }

    private suspend fun run(
        name: String,
        @StringRes action: Int,
        script: String,
    ): Boolean {
        val (ok, _) = proot.trackedJob(action, script, lock) { if (it == JobState.Idle) states.remove(name) else states[name] = it }
        Log.i(TAG, "${context.getString(action)} $name: ${if (ok) "ok" else "failed"}")
        revision.intValue++
        return ok
    }

    companion object {
        private const val TAG = "apt"

        /** Short queries match thousands of packages. */
        private const val MAX_RESULTS = 100
    }
}
