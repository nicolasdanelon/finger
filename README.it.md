# Finger — scanner Wi-Fi minimalista (Kotlin)

App leggera che elenca i dispositivi del tuo Wi-Fi: IP, hostname e latenza.
Senza root, senza nuove librerie, 3 permessi install-time.

> **Non è nmap.** Qui non c'è SYN scan: un'app Android non può aprire raw
> socket senza root. La scoperta è **ping sweep** parallelo (binario `ping`
> di sistema, funziona senza privilegi) + fallback TCP-connect su 12 porte
> comuni + raccolta della tabella ARP/vicini del kernel. Gli host che non
> rispondono a nulla ma sono già stati visti dal kernel compaiono via ARP.

*Read in [English](README.md) · Leer en [español](README.es.md)*

## Permessi (3, solo install-time, nessun dialogo runtime)
- `INTERNET` — ping / TCP connect + DNS inverso
- `ACCESS_NETWORK_STATE` — rileva il trasporto Wi-Fi
- `ACCESS_WIFI_STATE` — dichiarato, niente geolocalizzazione

Deliberatamente **senza** `ACCESS_FINE_LOCATION`: IP/prefisso/gateway locali
vengono da `LinkProperties` della rete Wi-Fi attiva (fallback: enumerazione
`NetworkInterface`), mai da `WifiManager`.

## Schermate
1. Senza Wi-Fi → "Connetti al WiFi" + pulsante che apre
   `Settings.ACTION_WIFI_SETTINGS`.
2. Con Wi-Fi → IP/prefisso/gateway/interfaccia, intervallo scansionato,
   contatore live (`Ping 37/254…`), conteggio con plurali corretti, riga di
   statistiche (`3.2s · ping:4 tcp:1 arp:7`), lista con pull-to-refresh e
   fade-in per riga, pulsante Scansiona/Interrompi centrato in basso. Avviso
   se vedi solo te stesso (isolamento AP / VPN / rete ospiti).

## Come trova i dispositivi (`NetworkScanner.kt`)
1. Rete locale via `LinkProperties` (senza permesso LOCATION).
2. **Fase 1:** un ping per IP su tutto l'intervallo, ~100 in parallelo.
   Questo popola la tabella ARP del kernel.
3. **Fase 2:** TCP connect (12 porte) per chi blocca ICMP.
4. **Fase 3:** resto ARP nella subnet (`/proc/net/arp` + `ip neigh show`):
   host silenziosi già visti dal kernel.
5. Hostname via DNS inverso, fallback `sconosciuto`. Il tuo dispositivo prima.

## Stile
Tema hacker terminal, solo colori + monospace + forme: verde fosforo su nero
in dark mode, inchiostro su carta in light mode (segue il flag di sistema,
100% offline). Icona adattiva propria (vettoriale, con layer monocromatico).

## i18n
English (default) · Español (`values-es`) · Italiano (`values-it`), con
`<plurals>` per il conteggio dei dispositivi.

## Compilare
```
# Android SDK 34 + JDK 17+, oppure:
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Privacy / backup
App stateless: niente database, prefs o file. Backup e trasferimenti
completamente esclusi (`data_extraction_rules.xml` + `backup_rules.xml`).

## Struttura
```
app/src/main/AndroidManifest.xml
app/src/main/java/com/finger/MainActivity.kt      # UI Compose
app/src/main/java/com/finger/NetworkScanner.kt    # ping-sweep + TCP + ARP
app/src/main/java/com/finger/Theme.kt             # tema hacker (solo stili)
app/src/main/res/values{,-es,-it}/strings.xml
```

## Limiti noti (Android moderno)
- Nessun SYN scan stile nmap senza root.
- Host che non rispondono e senza rDNS risultano `sconosciuto`.
- Il tuo MAC risulta `02:00:00:00:00:00` su Android 10+, quindi nascosto.
- `lintDebug` verde tranne 2 warning `GradleDependency` (aggiornare
  `activity-compose` forzerebbe `compileSdk 35`, rimandato di proposito).
