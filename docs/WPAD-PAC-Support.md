# WPAD/PAC Proxy Support für Client Mode

## Übersicht

Der ReverseProxy unterstützt jetzt **optional** die Verwendung von Windows-Systemproxies (WPAD/PAC) im **Client Mode**. Dies ermöglicht es dem Client, Outbound-Verbindungen über einen konfigurierten Unternehmensproxy aufzubauen, wenn direkter Internetzugang nicht möglich ist.

## Architektur

### Komponenten

1. **SystemProxyResolver** (Interface)
   - Abstrahiert die Proxy-Auflösung für eine Ziel-URL
   - Rückgabe: `ProxyInfo` mit einer Liste von Proxy-Kandidaten

2. **WindowsProxyResolver** (Implementierung)
   - Nutzt PowerShell und .NET `WebRequest.GetSystemWebProxy()` zur WPAD/PAC-Auflösung
   - Cached Ergebnisse für 5 Minuten (konfigurierbar)
   - Thread-safe

3. **OutboundSocketDialer** (Interface)
   - Abstrahiert das Öffnen von Outbound-Socket-Verbindungen
   - Implementierungen: `DirectSocketDialer`, `ProxySocketDialer`

4. **ProxySocketDialer** (Implementierung)
   - Öffnet Verbindungen via HTTP CONNECT-Tunnel
   - Unterstützt Fallback-Kette (z.B. PROXY1 → PROXY2 → DIRECT)
   - Behandelt fehlerhafte Proxies transparent

5. **ProxyInfo / ProxyCandidate**
   - Datenmodell für Proxy-Entscheidungen
   - Unterstützt: DIRECT, HTTP PROXY, SOCKS (Parsing, aber noch nicht implementiert)

### Integration

- **GatewayClient**: Verwendet `OutboundSocketDialer` statt direktem `new Socket()`
- **ProxyController**: Erstellt den passenden Dialer basierend auf der Konfiguration
- **ProxyConfig**: Neue Felder für WPAD/PAC-Einstellungen
- **ProxyConfigService**: Lädt/Speichert die neuen Einstellungen persistent
- **ProxyPreferencesDialog**: UI-Checkbox "Client: Use Windows system proxy (WPAD/PAC)"

## Konfiguration

### UI (Preferences Dialog)

**Checkbox:** `Client: Use Windows system proxy (WPAD/PAC) for outbound connections`

- **Default:** Deaktiviert (FALSE)
- **Bedeutung:** 
  - `FALSE` → Client baut Outbound-Verbindungen direkt auf (wie bisher)
  - `TRUE` → Client ermittelt Proxy via WPAD/PAC und baut CONNECT-Tunnel auf

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

### Proxy-Auflösung

1. **Client erhält vom Server:** `CONNECT targetHost:targetPort`
2. **Proxy-Resolver aufrufen:**
   - Synthetische URL bilden: `https://targetHost/` (wenn Port 443) oder `http://targetHost/`
   - PowerShell-Script `get-proxy-for-url.ps1` ausführen
   - Cache prüfen (TTL 5 Min.)
3. **Ergebnis parsen:**
   - `DIRECT` → keine Proxy-Verwendung
   - `PROXY host:port` → HTTP-Proxy
   - `PROXY a:8080; PROXY b:8080; DIRECT` → Fallback-Kette

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
   - Status `407`, `403`, `502`, etc. → Fehler, nächsten Proxy versuchen
4. **Fallback:** Wenn alle Proxies fehlschlagen und PAC `DIRECT` enthält → direkte Verbindung

### Fehlerbehandlung

- **Proxy-Auflösung fehlgeschlagen:** Exception → Verbindung schlägt fehl
- **Proxy TCP-Connect fehlgeschlagen:** IOException → nächster Proxy
- **CONNECT abgelehnt (z.B. 407):** IOException → nächster Proxy
- **Alle Proxies fehlgeschlagen:** IOException mit Details im Log

## Logging

### Log-Ausgaben

Im Traffic-Pane werden folgende Nachrichten angezeigt:

```
[proxy-resolver] Resolved proxy for https://api.openai.com/: ProxyInfo[PROXY proxy.example.com:8080; DIRECT]
[proxy-dialer] Dialing api.openai.com:443 via proxy chain: ProxyInfo[PROXY proxy.example.com:8080; DIRECT]
[proxy-dialer] Attempting HTTP CONNECT via proxy.example.com:8080 to api.openai.com:443
[proxy-dialer] HTTP CONNECT tunnel established via proxy.example.com:8080 to api.openai.com:443
```

Bei Fehlern:

```
[proxy-dialer] Proxy candidate PROXY proxy1:8080 failed: Connection refused
[proxy-dialer] Attempting DIRECT connection to api.openai.com:443
```

### Log-Level

- **info:** Erfolgreiche Verbindungen, Proxy-Entscheidungen
- **warn:** Fallback auf DIRECT (wenn nicht Windows), nicht unterstützte Proxy-Typen
- **error:** Alle Proxies fehlgeschlagen, Script-Fehler

## PowerShell-Script

**Datei:** `src/main/resources/ps/get-proxy-for-url.ps1`

**Funktion:**
- Nimmt URL als Parameter: `.\get-proxy-for-url.ps1 "https://api.openai.com/"`
- Nutzt .NET `[System.Net.WebRequest]::GetSystemWebProxy()` zur Auflösung
- Berücksichtigt WPAD, PAC, manuelle Proxy-Einstellungen und Bypass-Liste

**Rückgabe:**
- `DIRECT` → keine Proxy-Verwendung
- `PROXY host:port` → HTTP-Proxy verwenden
- Exit Code 0 bei Erfolg, 1 bei Fehler

**Extraktion:**
- Script wird beim ersten Aufruf nach `~/.proxy/get-proxy-for-url.ps1` extrahiert
- Einmal extrahiert, wird es wiederverwendet (kein erneutes Schreiben)

## Limitierungen

### Aktuell NICHT unterstützt

1. **SOCKS-Proxies:** PAC kann `SOCKS host:port` liefern → wird geparst, aber Verbindung schlägt fehl mit "SOCKS proxy not supported"
2. **Proxy-Authentifizierung:** Keine Unterstützung für Basic/NTLM/Kerberos-Auth
3. **HTTPS-Proxies:** Nur HTTP CONNECT wird unterstützt
4. **Nicht-Windows-Systeme:** WPAD-Resolver ist Windows-spezifisch (nutzt PowerShell + .NET)

### Erweiterungen (Optional, falls benötigt)

1. **Proxy-Auth:**
   - Basic: Username/Passwort in Config, `Proxy-Authorization`-Header senden
   - NTLM/Kerberos: Komplexer, benötigt native APIs oder Libraries wie JNA

2. **SOCKS-Support:**
   - Java-Library wie `sockslib` oder manuelle SOCKS4/5-Handshake-Implementierung

3. **Linux/macOS WPAD:**
   - Plattformspezifische Resolver via `gsettings` (Linux) oder `scutil` (macOS)

## Testszenarien

### 1. DIRECT (kein Proxy)

**Config:** `enabled=false` oder PAC liefert `DIRECT`

**Erwartung:** Verbindung wird direkt aufgebaut (wie bisher)

```
[proxy-dialer] Attempting DIRECT connection to api.openai.com:443
```

### 2. Single HTTP Proxy

**PAC-Antwort:** `PROXY proxy.example.com:8080`

**Erwartung:**
1. CONNECT-Tunnel zu `proxy.example.com:8080`
2. Bei `200 OK` → Raw-Daten über Tunnel
3. Bei Fehler → Verbindung schlägt fehl (kein Fallback, da PAC nur einen Proxy liefert)

### 3. Proxy-Fallback-Kette

**PAC-Antwort:** `PROXY proxy1:8080; PROXY proxy2:8080; DIRECT`

**Erwartung:**
1. Versuch: `proxy1:8080` → Connect-Fehler
2. Versuch: `proxy2:8080` → `200 OK` → Tunnel steht
3. Falls beide fehlschlagen: `DIRECT` wird versucht

```
[proxy-dialer] Proxy candidate PROXY proxy1:8080 failed: Connection timed out
[proxy-dialer] Attempting HTTP CONNECT via proxy2:8080
[proxy-dialer] HTTP CONNECT tunnel established via proxy2:8080 to api.openai.com:443
```

### 4. Proxy lehnt CONNECT ab (407 Auth Required)

**Erwartung:**
- Status `407` wird als Fehler behandelt
- Nächster Proxy oder DIRECT wird versucht (falls vorhanden)
- Sonst: Verbindung schlägt fehl mit "All proxy candidates failed"

```
[proxy-dialer] Proxy proxy.example.com:8080 rejected CONNECT with status 407 (HTTP/1.1 407 Proxy Authentication Required)
[proxy-dialer] All proxy candidates failed for api.openai.com:443 (tried 1 candidate(s))
```

### 5. Cache-Test

**Erwartung:**
- Erste Verbindung zu `api.openai.com:443` → PowerShell-Aufruf
- Zweite Verbindung (innerhalb 5 Min.) → Cache-Hit, kein Script-Aufruf

```
[proxy-resolver] Resolved proxy for https://api.openai.com/: ProxyInfo[PROXY proxy:8080]
... (einige Sekunden später) ...
[proxy-resolver] Proxy cache hit for https://api.openai.com/: ProxyInfo[PROXY proxy:8080]
```

## Sicherheit

### Credentials

**Aktuell:**
- Keine Proxy-Credentials werden unterstützt
- Keine Credentials werden geloggt

**Falls später erweitert:**
- Speicherung nur verschlüsselt (z.B. Windows DPAPI)
- Niemals in Klartext-Logs

### CA-Trust

- WPAD/PAC-Auflösung nutzt Systemvertrauen (Windows Root Store)
- Keine zusätzlichen Zertifikatsprüfungen nötig

## Migration / Kompatibilität

### Bestehende Installationen

- **Default:** `enabled=false` → keine Änderung am bestehenden Verhalten
- Upgrade transparent: alte Configs funktionieren weiter

### Abwärtskompatibilität

- Neue Properties haben Defaults → kein Breakage
- UI-Checkbox ist optional → muss nicht aktiviert werden

## Troubleshooting

### Problem: "All proxy candidates failed"

**Ursache:**
- Proxy erreichbar, aber CONNECT wird abgelehnt (z.B. 407, 403, 502)
- Zielhost in Proxy-Blacklist
- Proxy unterstützt CONNECT nicht

**Lösung:**
- Log prüfen: Welcher Status-Code kam zurück?
- PAC-Konfiguration prüfen: Liefert sie `DIRECT` als Fallback?
- Proxy-Admin kontaktieren: Ist CONNECT für Zielhost erlaubt?

### Problem: "Proxy resolution script failed"

**Ursache:**
- PowerShell nicht installiert
- Script-Datei fehlt oder ist beschädigt
- WPAD/PAC nicht erreichbar

**Lösung:**
- PowerShell-Version prüfen: `Get-Host | Select-Object Version`
- Script manuell testen: `powershell.exe -ExecutionPolicy Bypass -File ~/.proxy/get-proxy-for-url.ps1 "https://example.com/"`
- WPAD/PAC-URL im Browser prüfen: `about:config` (Firefox) oder `chrome://net-internals/#proxy` (Chrome)

### Problem: "SOCKS proxy not supported"

**Ursache:**
- PAC liefert `SOCKS host:port`
- SOCKS-Implementierung fehlt aktuell

**Lösung:**
- PAC anpassen: SOCKS durch HTTP-Proxy ersetzen (falls möglich)
- Oder: Feature-Request für SOCKS-Support

### Problem: Cache veraltet

**Symptom:**
- Proxy-Konfiguration geändert, aber Client nutzt alten Proxy

**Lösung:**
- Proxy neu starten (stoppt/startet Client-Thread → Cache wird geleert)
- Oder: Cache-TTL in Config reduzieren (z.B. `cacheTtlSeconds=60`)

## Performance

### Overhead

- **Ohne Proxy (`enabled=false`):** Kein Overhead (wie bisher)
- **Mit Proxy (`enabled=true`):**
  - Erste Verbindung pro Host: ~100-500ms (PowerShell-Aufruf)
  - Weitere Verbindungen (Cache-Hit): <1ms
  - CONNECT-Handshake: ~50-200ms (abhängig von Proxy-Latenz)

### Cache-Strategie

- **Key:** `(scheme, host, port)` → z.B. `https://api.openai.com:443`
- **TTL:** 5 Minuten (konfigurierbar)
- **Thread-Safety:** `ConcurrentHashMap` (lock-free reads)

### Optimierung

- Cache-TTL erhöhen (z.B. 15 Min.) für stabilere Netzwerke
- Cache-TTL reduzieren (z.B. 1 Min.) für dynamische PAC-Konfigurationen

## Zusammenfassung

| Feature | Status | Bemerkung |
|---------|--------|-----------|
| WPAD/PAC-Auflösung (Windows) | ✅ Implementiert | PowerShell + .NET |
| HTTP CONNECT-Tunnel | ✅ Implementiert | RFC 2817 |
| Proxy-Fallback-Kette | ✅ Implementiert | Mehrere Proxies + DIRECT |
| Cache (5 Min. TTL) | ✅ Implementiert | Thread-safe |
| UI-Checkbox (Preferences) | ✅ Implementiert | Ein/Aus-Schalter |
| Config-Persistenz | ✅ Implementiert | `proxy.properties` |
| Logging | ✅ Implementiert | Info/Warn-Level |
| SOCKS-Proxies | ❌ Nicht unterstützt | Parsing OK, Verbindung fehlt |
| Proxy-Auth | ❌ Nicht unterstützt | Basic/NTLM/Kerberos |
| Linux/macOS WPAD | ❌ Nicht unterstützt | Nur Windows |

**Status:** Feature vollständig implementiert gemäß Anforderungen F1-F8, N1-N5, T1 (Unit-Tests empfohlen, aber optional).

