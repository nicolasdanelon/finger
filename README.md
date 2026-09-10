# Finger — minimalist Wi-Fi scanner (Kotlin)

Lightweight app that lists the devices on your Wi-Fi: IP, hostname and latency.
No root, no new libraries, 3 install-time permissions.

> **Not nmap.** There is no SYN scan here: Android apps can't open raw sockets
> without root. Discovery is a parallel **ping sweep** (system `ping` binary,
> which works unprivileged) + TCP-connect fallback on 12 common ports +
> harvest of the kernel ARP/neighbor table. Hosts that answer nothing but were
> seen by the kernel still show up via ARP.

*Leer en [español](README.es.md) · Leggi in [italiano](README.it.md)*

## Permissions (3, install-time only, no runtime dialogs)
- `INTERNET` — ping / TCP connect + reverse DNS
- `ACCESS_NETWORK_STATE` — detect Wi-Fi transport
- `ACCESS_WIFI_STATE` — declared, no location involved

Deliberately **no** `ACCESS_FINE_LOCATION`: the local IP/prefix/gateway come
from `LinkProperties` of the active Wi-Fi network (fallback: `NetworkInterface`
enumeration), never from `WifiManager`.

## Screens
1. No Wi-Fi → "Connect to WiFi" + button opening `Settings.ACTION_WIFI_SETTINGS`.
2. Wi-Fi on → own IP/prefix/gateway/interface, scanned range, live counter
   (`Ping 37/254…`), device count with proper plurals, stats line
   (`3.2s · ping:4 tcp:1 arp:7`), pull-to-refresh list with fade-in rows,
   centered Scan/Stop button at the bottom. Empty-state hint when only
   yourself is visible (AP isolation / VPN / guest network).

## How it finds devices (`NetworkScanner.kt`)
1. Local network via `LinkProperties` (no LOCATION permission).
2. **Phase 1:** one ping per IP over the whole range, ~100 in parallel.
   This populates the kernel ARP table.
3. **Phase 2:** TCP connect (12 ports) for hosts blocking ICMP.
4. **Phase 3:** remaining ARP entries in the subnet (`/proc/net/arp` +
   `ip neigh show`) — silent hosts the kernel already saw.
5. Hostnames via reverse DNS, fallback `unknown`. Own device pinned first.

## Style
Hacker terminal theme, colors + monospace + shapes only: phosphor green on
near-black in dark mode, ink-on-paper in light mode (follows the system flag,
fully offline). Custom adaptive launcher icon (vector, incl. monochrome layer).

## i18n
English (default) · Español (`values-es`) · Italiano (`values-it`), including
`<plurals>` for the device count.

## Build
```
# Android SDK 34 + JDK 17+, or just:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Privacy / backups
Stateless app: no database, no prefs, no files. Backups and device transfers
are fully excluded (`data_extraction_rules.xml` + `backup_rules.xml`).

## Layout
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/finger/MainActivity.kt      # Compose UI
app/src/main/java/com/finger/NetworkScanner.kt    # ping-sweep + TCP + ARP
app/src/main/java/com/finger/Theme.kt             # hacker theme (styles only)
app/src/main/res/values{,-es,-it}/strings.xml
```

## Known limits (modern Android)
- No true nmap-style SYN scan without root.
- Hosts answering nothing with no rDNS show as `unknown`.
- Your own MAC reads `02:00:00:00:00:00` on Android 10+, hence hidden.
- `lintDebug` is green except 2 `GradleDependency` upkeep warnings (bumping
  `activity-compose` would force `compileSdk 35`, intentionally deferred).
