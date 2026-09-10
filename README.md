# Finger — mini nmap WiFi (Kotlin, minimalista)

App liviana: escanea la /24 Wi-Fi y lista IP, hostname y MAC.

## Permisos (solo 3, sin runtime)
- `INTERNET` — ping / TCP connect + resolución DNS inversa
- `ACCESS_NETWORK_STATE` — detectar si hay Wi-Fi (`TransportInfo`)
- `ACCESS_WIFI_STATE` — reservado/futuro, no pide ubicación

A propósito **NO** pide `ACCESS_FINE_LOCATION`: la IP propia se obtiene por
`NetworkInterface` (wlan0) y la MAC por `/proc/net/arp`, no por `WifiManager`.

## Pantallas
1. Sin Wi-Fi → texto "Conectar a WiFi" + botón "Abrir ajustes Wi-Fi"
   (`Settings.ACTION_WIFI_SETTINGS`).
2. Con Wi-Fi → IP propia, botón Escanear/Detener, lista IP / hostname / MAC.

## Lógica (app/src/main/java/com/finger/NetworkScanner.kt)
- `getLocalNetwork(ctx)`: IP/prefijo/gateway vía `LinkProperties` del Wi-Fi activo
  (sin LOCATION), fallback a `NetworkInterface`.
- Fase 1 ping binario (`ping -c 1 -W 1`, funciona sin root, a diferencia de
  `isReachable`), fase 2 TCP en 12 puertos, fase 3 resto de ARP en subred
  (`/proc/net/arp` + `ip neigh show`) aunque el host no responda a nada.
- Ping manual + re-ping por dispositivo con latencia, stats
  (`ping:n tcp:n arp:n`) y aviso de AP isolation/VPN si solo te ves a vos.

## Compilar
```
# requiere Android SDK 34 + JDK 17
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Estructura
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/finger/MainActivity.kt      # UI Compose (2 pantallas)
app/src/main/java/com/finger/NetworkScanner.kt   # ping-sweep + ARP + DNS
```

## Limitaciones Android moderno
- Sin root no se puede hacer SYN-scan tipo nmap real; se usa ping+TCP connect.
- Algunos dispositivos no responden a ping ni tienen rDNS → salen como
  "desconocido" con MAC de ARP si el kernel los vio.
- En Android 10+ la MAC propia sale `02:00:00:00:00:00`, por eso se muestra "—".
