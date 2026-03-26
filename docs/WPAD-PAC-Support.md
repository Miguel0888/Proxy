# WPAD/PAC Proxy Support für Client Mode

## Übersicht

Der ReverseProxy unterstützt jetzt **optional** die Verwendung von Windows-Systemproxies (WPAD/PAC) im **Client Mode**. Dies ermöglicht es dem Client, Outbound-Verbindungen über einen konfigurierten Unternehmensproxy aufzubauen, wenn direkter Internetzugang nicht möglich ist.

## Architektur

### Komponenten

1. **SystemProxyResolver** (Interface)
   - Abstrahiert die Proxy-Auflösung für eine Ziel-URL
   - Rückgabe: `ProxyInfo` mit einer Liste von Proxy-Kandidaten

2. **WinProxyJavaResolver** (Neue Implementierung)
   - Nutzt die `win-proxy-java` Bibliothek (`com.aresstack:win-proxy-java:0.1.0-beta.1`)
   - Reine Java-Implementierung - **keine PowerShell-Scripts mehr nötig**
   - Unterstützt WPAD/PAC Auto-Konfiguration
   - Cached Ergebnisse für 5 Minuten (konfigurierbar)
   - Thread-safe

3. **WindowsProxyResolver** (Legacy - @Deprecated)
   - Alte PowerShell-basierte Implementierung
   - Wird nicht mehr verwendet, bleibt für Rückwärtskompatibilität

4. **OutboundSocketDialer** (Interface)
   - Abstrahiert das Öffnen von Outbound-Socket-Verbindungen
   - Implementierungen: `DirectSocketDialer`, `ProxySocketDialer`

5. **ProxySocketDialer** (Implementierung)
   - Öffnet Verbindungen via HTTP CONNECT-Tunnel
   - Unterstützt Fallback-Kette (z.B. PROXY1 → PROXY2 → DIRECT)
   - Behandelt fehlerhafte Proxies transparent

6. **ProxyInfo / ProxyCandidate**
   - Datenmodell für Proxy-Entscheidungen
   - Unterstützt: DIRECT, HTTP PROXY, SOCKS (Parsing, aber noch nicht implementiert)

### Integration

- **GatewayClient**: Verwendet `OutboundSocketDialer` statt direktem `new Socket()`
- **ProxyController**: Erstellt den passenden Dialer basierend auf der Konfiguration
- **ProxyConfig**: Felder für WPAD/PAC-Einstellungen
- **ProxyConfigService**: Lädt/Speichert die Einstellungen persistent
- **ProxyPreferencesDialog**: UI-Checkbox "Client: Use Windows system proxy (WPAD/PAC)"

## Änderungen mit win-proxy-java

### Migration von PowerShell zu Java

Die bisherige PowerShell-basierte Proxy-Ermittlung wurde durch eine reine Java-Implementierung ersetzt:

**Vorher (PowerShell):**
- Abhängigkeit von externem PowerShell-Script
- Script-Extraktion und -Ausführung zur Laufzeit
- Potenziell anfällig für PowerShell-Richtlinien
- Zusätzliche Prozess-Erstellung

**Jetzt (win-proxy-java):**
- Reine Java-Bibliothek
- Keine externen Scripts oder Prozesse
- Direkter Zugriff auf Windows-Proxy-Einstellungen
- Robuster und wartungsfreundlicher

### Neue Dependency

```gradle
dependencies {
    implementation 'com.aresstack:win-proxy-java:0.1.0-beta.1'
}
```

### Entfernte Konfiguration

Das Feld `proxy.client.outboundProxy.scriptPath` ist nicht mehr erforderlich und wird ignoriert.
Die Anwendung benötigt keine manuelle Script-Pflege mehr.

## Konfiguration

### UI (Preferences Dialog)

**Checkbox:** `Client: Use Windows system proxy (WPAD/PAC) for outbound connections`

- **Default:** Deaktiviert (FALSE)
- **Bedeutung:** 
  - `FALSE` → Client baut Outbound-Verbindungen direkt auf (wie bisher)
  - `TRUE` → Client ermittelt Proxy via win-proxy-java Bibliothek und baut CONNECT-Tunnel auf

### Properties-Datei

Speicherort: `~/.proxy/proxy.properties`

```properties
# WPAD/PAC Proxy Support (Client Mode)
proxy.client.outboundProxy.enabled=false
proxy.client.outboundProxy.cacheTtlSeconds=300
proxy.client.outboundProxy.connectTimeoutMillis=10000
proxy.client.outboundProxy.handshakeTimeoutMillis=10000
```

| Property | Default | Beschreibung |
|----------|---------|--------------|
| `enabled` | `false` | Aktiviert WPAD/PAC-Proxy-Unterstützung |
| `cacheTtlSeconds` | `300` | Cache-Dauer für Proxy-Auflösungen (5 Min.) |
| `connectTimeoutMillis` | `10000` | TCP-Connect-Timeout zum Proxy |
| `handshakeTimeoutMillis` | `10000` | CONNECT-Handshake-Timeout |

## Funktionsweise

### Proxy-Auflösung mit win-proxy-java

1. **Client erhält vom Server:** `CONNECT targetHost:targetPort`
2. **Proxy-Resolver aufrufen:**
   - Synthetische URL bilden: `https://targetHost/` (wenn Port 443) oder `http://targetHost/`
   - `WindowsProxyResolver.resolve(url)` aus win-proxy-java aufrufen
   - Cache prüfen (TTL 5 Min.)
3. **Ergebnis verarbeiten:**
   - `ProxyResult.isDirect()` → keine Proxy-Verwendung
   - Sonst: HTTP-Proxy mit Host und Port

### win-proxy-java Funktionen

Die Bibliothek unterstützt:
- **Statische Proxy-Einstellungen** aus Windows Registry
- **PAC-Auswertung** via GraalJS
- **WPAD Auto-Detection** (wenn aktiviert)
- **Bypass-Listen** für lokale Adressen

### CONNECT-Tunnel

Wenn Proxy ermittelt wurde:

1. **TCP-Verbindung zum Proxy:** `socket.connect(proxyHost, proxyPort, connectTimeout)`
2. **CONNECT senden:**
   ```
   CONNECT targetHost:targetPort HTTP/1.1
   Host: targetHost:targetPort
   Proxy-Connection: Keep-Alive
   
   ```
3. **Response lesen:**
   - Status `200` → Tunnel steht, Raw-Daten durchreichen
   - Status `407`, `403`, `502`, etc. → Fehler, nächsten Proxy versuchen (falls vorhanden)
4. **Fallback:** DIRECT wird als Fallback hinzugefügt

### Fehlerbehandlung

- **Proxy-Auflösung fehlgeschlagen:** Fallback auf DIRECT (mit Logging)
- **Proxy TCP-Connect fehlgeschlagen:** IOException → nächster Proxy
- **CONNECT abgelehnt (z.B. 407):** IOException → nächster Proxy
- **Alle Proxies fehlgeschlagen:** IOException mit Details im Log

## Logging

### Log-Ausgaben

Im Traffic-Pane werden folgende Nachrichten angezeigt:

```
[proxy-resolver] win-proxy-java returned proxy proxy.example.com:8080 for https://api.openai.com/ (static)
[proxy-resolver] Resolved proxy for https://api.openai.com/: ProxyInfo[HTTP proxy.example.com:8080; DIRECT]
[proxy-dialer] Dialing api.openai.com:443 via proxy chain: ProxyInfo[HTTP proxy.example.com:8080; DIRECT]
[proxy-dialer] HTTP CONNECT tunnel established via proxy.example.com:8080 to api.openai.com:443
```

Bei Fehlern:

```
[proxy-resolver] win-proxy-java error for https://api.openai.com/: ... -> falling back to DIRECT
[proxy-dialer] Proxy candidate HTTP proxy1:8080 failed: Connection refused
[proxy-dialer] Attempting DIRECT connection to api.openai.com:443
```

## Limitierungen

### Aktuell NICHT unterstützt

1. **SOCKS-Proxies:** win-proxy-java unterstützt HTTP-Proxies, SOCKS wird nicht tunneled
2. **Proxy-Authentifizierung:** Keine Unterstützung für Basic/NTLM/Kerberos-Auth
3. **HTTPS-Proxies:** Nur HTTP CONNECT wird unterstützt
4. **Nicht-Windows-Systeme:** win-proxy-java ist Windows-spezifisch

### Beta-Version Hinweis

Die verwendete win-proxy-java Bibliothek ist als Beta gekennzeichnet (`0.1.0-beta.1`).
Bei Problemen kann das Fallback-Verhalten auf DIRECT eine Verbindung trotzdem ermöglichen.

## Testszenarien

### Unit-Tests

Die Klasse `WinProxyJavaResolverTest` enthält Tests für:
- Resolver-Erstellung
- Proxy-Auflösung für HTTPS-URLs
- Proxy-Auflösung für HTTP-URLs
- Cache-Funktionalität
- Localhost-Behandlung

Ausführen mit:
```bash
.\gradlew.bat test --tests "de.bund.zrb.WinProxyJavaResolverTest"
```

### Manuelle Tests

Im Preferences Dialog:
1. Checkbox "Use Windows system proxy" aktivieren
2. Test-URL eingeben (z.B. `https://www.google.com/`)
3. "Test"-Button klicken
4. Dialog zeigt Proxy-Ergebnis oder DIRECT

## Performance

### Overhead

- **Ohne Proxy (`enabled=false`):** Kein Overhead (wie bisher)
- **Mit Proxy (`enabled=true`):**
  - Erste Verbindung pro Host: ~10-100ms (Java API-Aufruf)
  - Weitere Verbindungen (Cache-Hit): <1ms
  - CONNECT-Handshake: ~50-200ms (abhängig von Proxy-Latenz)

### Verbesserung gegenüber PowerShell

Die win-proxy-java Bibliothek ist deutlich schneller als die PowerShell-basierte Lösung:
- Keine Prozess-Erstellung
- Keine Script-Ausführung
- Direkter Windows-API-Zugriff

### Cache-Strategie

- **Key:** `(scheme, host, port)` → z.B. `https://api.openai.com:443`
- **TTL:** 5 Minuten (konfigurierbar)
- **Thread-Safety:** `ConcurrentHashMap` (lock-free reads)

## Migration / Kompatibilität

### Bestehende Installationen

- **Default:** `enabled=false` → keine Änderung am bestehenden Verhalten
- Upgrade transparent: alte Configs funktionieren weiter
- Script-Pfad-Konfiguration wird ignoriert (kann bleiben oder entfernt werden)

### Abwärtskompatibilität

- Neue Properties haben Defaults → kein Breakage
- UI-Checkbox ist optional → muss nicht aktiviert werden
- Keine Änderung am Netzwerkverhalten wenn deaktiviert

## Troubleshooting

### Problem: "All proxy candidates failed"

**Ursache:**
- Proxy erreichbar, aber CONNECT wird abgelehnt (z.B. 407, 403, 502)
- Zielhost in Proxy-Blacklist
- Proxy unterstützt CONNECT nicht

**Lösung:**
- Log prüfen: Welcher Status-Code kam zurück?
- Proxy-Admin kontaktieren: Ist CONNECT für Zielhost erlaubt?

### Problem: "win-proxy-java error"

**Ursache:**
- Windows-Proxy-Einstellungen nicht lesbar
- PAC-Script Fehler
- WPAD nicht erreichbar

**Lösung:**
- Windows Proxy-Einstellungen prüfen (Systemsteuerung → Internet-Optionen)
- Mit "Test"-Button im Preferences Dialog prüfen
- Fallback auf DIRECT erfolgt automatisch

### Problem: Cache veraltet

**Symptom:**
- Proxy-Konfiguration geändert, aber Client nutzt alten Proxy

**Lösung:**
- Proxy neu starten (Cache wird geleert)
- Oder: Cache-TTL in Config reduzieren

## Zusammenfassung

| Feature | Status | Bemerkung |
|---------|--------|-----------|
| WPAD/PAC-Auflösung (Windows) | ✅ Implementiert | win-proxy-java Bibliothek |
| HTTP CONNECT-Tunnel | ✅ Implementiert | RFC 2817 |
| Proxy-Fallback | ✅ Implementiert | Proxy + DIRECT |
| Cache (5 Min. TTL) | ✅ Implementiert | Thread-safe |
| UI-Checkbox (Preferences) | ✅ Implementiert | Ein/Aus-Schalter |
| Test-Button | ✅ Implementiert | Proxy-Test im Dialog |
| Config-Persistenz | ✅ Implementiert | `proxy.properties` |
| Logging | ✅ Implementiert | Info/Warn-Level |
| Keine Scripts nötig | ✅ Implementiert | Reine Java-Lösung |
| SOCKS-Proxies | ❌ Nicht unterstützt | Nur HTTP-Proxies |
| Proxy-Auth | ❌ Nicht unterstützt | Basic/NTLM/Kerberos |
| Linux/macOS | ❌ Nicht unterstützt | Nur Windows |

**Status:** Feature vollständig implementiert mit win-proxy-java Bibliothek. PowerShell-Abhängigkeit entfernt.

