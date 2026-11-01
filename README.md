# KaliLauncher

An Android home screen that boots a **Kali Linux** (kali-rolling, arm64) system
next to your phone apps — **without root**, under proot. A fork of
[SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher)
(which runs Arch Linux ARM) with the guest distro swapped to Kali, so the
Kali toolset is one search away.

## What's different from omalauncher

- **Kali guest**: the bootstrap downloads the official Kali arm64 rootfs
  (126 MB, `.tar.xz`) and configures apt against `kali-rolling`
  (`main contrib non-free non-free-firmware`).
- **apt everywhere**: package search, install/remove, and system updates all
  run through apt/dpkg. The AUR is gone; Kali's `kali-tools-*` metapackages
  (`kali-tools-top10`, `kali-tools-web`, `kali-tools-passwords`, …) are
  searchable right in the packages screen.
- **Hermes** joined the coding-agent list (installed via mise's pipx backend).
- Own application id (`ir.arminz.kalilauncher`) and signing key, so it
  installs **alongside** omalauncher rather than replacing it.
- Everything else — the compositor, OWL widgets, themes, GPU acceleration,
  coding agents — is unchanged from upstream.

## Root and what works

No root is needed at any point: proot fakes root inside the guest, so apt and
the Kali tools install and run normally. Everything userspace works (nmap,
metasploit, hydra, john, sqlmap, wireshark, …). Things that need the real
kernel do **not** work on a stock device — Wi-Fi monitor mode / packet
injection, iptables/nftables, USB tooling. That is a kernel/firmware limit no
Shizuku or permission trick can lift; run those against a radio outside the
phone (VPS / Raspberry Pi with a monitor-capable dongle over SSH), or use a
NetHunter custom kernel.

## Building

```
./scripts/fetch-proot.sh          # Termux's proot -> app/src/main/jniLibs (needs bsdtar)
./gradlew :app:assembleDebug
```

CI builds both a debug and a signed release APK on every push to `kali`; grab
them from the workflow artifacts. Signing uses repo secrets
(`KALI_KEYSTORE_B64`, `KALI_KEYSTORE_PASS`); a `keystore.properties` at the
repo root overrides that locally, same format as upstream.

Optional, skips the on-device download:

```
adb push kali-nethunter-rootfs-minimal-arm64.tar.xz /sdcard/Android/data/ir.arminz.kalilauncher/files/
```

Tests: `./gradlew :owl:test :app:testDebugUnitTest`.

## Credits

Everything here is built on [omalauncher](https://github.com/SimonSchubert/omalauncher)
by Simon Schubert (MIT), which in turn takes its look from
[Omarchy](https://omarchy.org). Kali Linux is a trademark of OffSec — this
project is not affiliated with or endorsed by Omarchy, Arch Linux, Kali Linux,
or OffSec.
