package dev.omalauncher.linux

import androidx.annotation.StringRes
import dev.omalauncher.R

/**
 * A Kali tool collection the tools screen offers: one metapackage that apt expands
 * into the category's tools. Every one of these exists in kali-rolling on arm64
 * (checked against the repo's Packages index).
 */
data class ToolGroup(
    val pkg: String,
    @StringRes val title: Int,
)

/**
 * The catalog, in offensive-security order with the top 10 first, then attack
 * categories by the usual testing flow, then wireless and hardware, then analysis,
 * then defense and reporting, and the whole-distribution bundles last.
 */
val TOOLS: List<ToolGroup> =
    listOf(
        ToolGroup("kali-tools-top10", R.string.linux_tools_top10),
        ToolGroup("kali-tools-information-gathering", R.string.linux_tools_information_gathering),
        ToolGroup("kali-tools-vulnerability", R.string.linux_tools_vulnerability),
        ToolGroup("kali-tools-web", R.string.linux_tools_web),
        ToolGroup("kali-tools-database", R.string.linux_tools_database),
        ToolGroup("kali-tools-passwords", R.string.linux_tools_passwords),
        ToolGroup("kali-tools-exploitation", R.string.linux_tools_exploitation),
        ToolGroup("kali-tools-post-exploitation", R.string.linux_tools_post_exploitation),
        ToolGroup("kali-tools-sniffing-spoofing", R.string.linux_tools_sniffing_spoofing),
        ToolGroup("kali-tools-social-engineering", R.string.linux_tools_social_engineering),
        ToolGroup("kali-tools-wireless", R.string.linux_tools_wireless),
        ToolGroup("kali-tools-802-11", R.string.linux_tools_802_11),
        ToolGroup("kali-tools-bluetooth", R.string.linux_tools_bluetooth),
        ToolGroup("kali-tools-hardware", R.string.linux_tools_hardware),
        ToolGroup("kali-tools-crypto-stego", R.string.linux_tools_crypto_stego),
        ToolGroup("kali-tools-forensics", R.string.linux_tools_forensics),
        ToolGroup("kali-tools-reverse-engineering", R.string.linux_tools_reverse_engineering),
        ToolGroup("kali-tools-fuzzing", R.string.linux_tools_fuzzing),
        ToolGroup("kali-tools-gpu", R.string.linux_tools_gpu),
        ToolGroup("kali-tools-identify", R.string.linux_tools_identify),
        ToolGroup("kali-tools-detect", R.string.linux_tools_detect),
        ToolGroup("kali-tools-protect", R.string.linux_tools_protect),
        ToolGroup("kali-tools-reporting", R.string.linux_tools_reporting),
        ToolGroup("kali-tools-voip", R.string.linux_tools_voip),
        ToolGroup("kali-tools-rfid", R.string.linux_tools_rfid),
        ToolGroup("kali-tools-sdr", R.string.linux_tools_sdr),
        ToolGroup("kali-tools-recover", R.string.linux_tools_recover),
        ToolGroup("kali-tools-respond", R.string.linux_tools_respond),
        ToolGroup("kali-tools-windows-resources", R.string.linux_tools_windows_resources),
        ToolGroup("kali-linux-default", R.string.linux_tools_default),
        ToolGroup("kali-linux-large", R.string.linux_tools_large),
    )
