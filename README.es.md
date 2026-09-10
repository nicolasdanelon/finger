# Finger — scanner Wi-Fi minimalista (Kotlin)

App liviana que lista los dispositivos de tu Wi-Fi: IP, hostname y latencia.
Sin root, sin librerías nuevas, 3 permisos install-time.

> **No es nmap.** Acá no hay SYN scan: una app Android no puede abrir raw
> sockets sin root. El descubrimiento es **barrido ping** en paralelo (binario
> `ping` del sistema, funciona sin privilegios) + fallback TCP-connect en 12
> puertos comunes + cosecha de la tabla ARP/vecinos del kernel. Equipos que no
> responden nada pero que el kernel ya vio aparecen igual por ARP.

*Read in [English](README.md) · Leggi in [italiano](README.it.md)*

## Permisos (3, solo install-time, sin diálogos runtime)
- `INTERNET` — ping / TCP connect + DNS reversa
- `ACCESS_NETWORK_STATE` — detectar transporte Wi-Fi
- `ACCESS_WIFI_STATE` — declarado, sin nada de ubicación

A propósito **sin** `ACCESS_FINE_LOCATION`: IP/prefijo/gateway propios salen de
`LinkProperties` de la red Wi-Fi activa (fallback: enumerar `NetworkInterface`),
nunca de `WifiManager`.

## Pantallas
1. Sin Wi-Fi → "Conectar a WiFi" + botón que abre `Settings.ACTION_WIFI_SETTINGS`.
2. Con Wi-Fi → IP/prefijo/gateway/interfaz propias, rango escaneado, contador
   en vivo (`Ping 37/254…`), conteo con plurales correctos, línea de stats
   (`3.2s · ping:4 tcp:1 arp:7`), lista con pull-to-refresh y fade-in por fila,
   botón Escanear/Detener centrado abajo. Aviso si solo te ves a vos
   (AP isolation / VPN / red de invitados).

## Cómo encuentra equipos (`NetworkScanner.kt`)
1. Red local vía `LinkProperties` (sin permiso LOCATION).
2. **Fase 1:** un ping por IP a todo el rango, ~100 en paralelo.
   Esto puebla la tabla ARP del kernel.
3. **Fase 2:** TCP connect (12 puertos) para los que bloquean ICMP.
4. **Fase 3:** resto de ARP en la subred (`/proc/net/arp` + `ip neigh show`):
   equipos mudos que el kernel ya vio.
5. Hostnames por DNS reversa, fallback `desconocido`. Tu equipo primero.

## Estilo
Theme hacker terminal, solo colores + monoespaciado + formas: verde fósforo
sobre negro en modo oscuro, tinta sobre papel en modo claro (sigue el flag del
sistema, 100% offline). Ícono adaptativo propio (vector, con capa monocroma).

## i18n
English (default) · Español (`values-es`) · Italiano (`values-it`), con
`<plurals>` para el conteo de dispositivos.

## Compilar
```
# Android SDK 34 + JDK 17+, o simplemente:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Privacidad / backups
App stateless: sin base, sin prefs, sin archivos. Backups y transferencias
excluidos por completo (`data_extraction_rules.xml` + `backup_rules.xml`).

## Estructura
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/finger/MainActivity.kt      # UI Compose
app/src/main/java/com/finger/NetworkScanner.kt    # ping-sweep + TCP + ARP
app/src/main/java/com/finger/Theme.kt             # theme hacker (solo estilos)
app/src/main/res/values{,-es,-it}/strings.xml
```

## Límites conocidos (Android moderno)
- Sin SYN scan real tipo nmap sin root.
- Equipos que no responden nada y sin rDNS salen como `desconocido`.
- Tu propia MAC sale `02:00:00:00:00:00` en Android 10+, por eso se oculta.
- `lintDebug` en verde salvo 2 warnings `GradleDependency` (subir
  `activity-compose` forzaría `compileSdk 35`, diferido a propósito).
