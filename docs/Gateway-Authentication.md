# Gateway-Authentifizierung und Verschlüsselung

## Übersicht

Die Gateway-Verbindung zwischen Client und Server unterstützt verschiedene Authentifizierungs- und Verschlüsselungsmodi.

## Authentifizierungsmodi

### 1. NONE (Keine Authentifizierung)

- Keine Passwort-Prüfung
- Nur für lokale/vertrauenswürdige Netzwerke
- **Nicht empfohlen für Produktionsumgebungen**

### 2. PASSKEY (Legacy-Modus)

- Einfacher gemeinsamer Schlüssel
- Client sendet Passkey im Klartext: `HELLO <passkey>`
- Server vergleicht mit konfiguriertem Passkey
- **Abwärtskompatibel, aber nicht sicher**

### 3. TOKEN (Empfohlen)

- Challenge-Response-Authentifizierung mit HMAC-SHA256
- Ablauf:
  1. Client verbindet sich
  2. Server sendet Challenge (32 Bytes, Base64-kodiert)
  3. Client berechnet: `HMAC-SHA256(challenge, shared_secret)`
  4. Client sendet Response
  5. Server verifiziert Response
- **Sicher gegen Replay-Angriffe**

## Verschlüsselungsmodi

### 1. NONE (Keine Verschlüsselung)

- Daten werden im Klartext übertragen
- Für lokale Netzwerke oder wenn TLS auf Netzwerkebene verwendet wird

### 2. AES (Ende-zu-Ende-Verschlüsselung)

- AES-256-CBC mit PKCS5-Padding
- Schlüsselableitung: PBKDF2 mit 10.000 Iterationen
- Pro-Session generierte IVs
- **Empfohlen für Verbindungen über öffentliche Netzwerke**

## Konfiguration

### In der UI (Preferences Dialog)

- **Gateway Secret/Passkey**: Das gemeinsame Geheimnis
- **Auth Mode**: NONE / PASSKEY / TOKEN
- **Encryption**: Aktiviert/Deaktiviert
- **Username**: Optional für zukünftige OAuth-Unterstützung

### In der Properties-Datei

```properties
proxy.gateway.passkey=mein_geheimer_schluessel
proxy.gateway.authMode=TOKEN
proxy.gateway.encryption.enabled=true
proxy.gateway.username=
```

## Protokoll

### PASSKEY-Modus (Legacy)

```
Client -> Server: HELLO <passkey>
Server -> Client: OK | DENIED | BUSY
```

### TOKEN-Modus

```
Client -> Server: AUTH TOKEN
Server -> Client: CHALLENGE <base64_challenge>
Client -> Server: RESPONSE <base64_hmac>
Server -> Client: OK | DENIED
```

### Mit Verschlüsselung

Nach erfolgreicher Authentifizierung:

```
Server -> Client: ENCRYPT <base64_salt> <base64_iv>
# Ab hier sind alle Daten AES-verschlüsselt
```

## Fehlerbehandlung

### Verbindungsfehler

| Status | Bedeutung |
|--------|-----------|
| `OK` | Authentifizierung erfolgreich |
| `DENIED` | Falsches Passwort/Token |
| `BUSY` | Anderer Client bereits verbunden |
| `(Verbindung geschlossen)` | Server nicht im Gateway-Modus |

### Logging

Bei Verbindungsproblemen werden detaillierte Logs ausgegeben:

```
GatewayClient: TCP connection established to host:8888
GatewayClient: Sending HELLO with passkey='****'
GatewayClient: Waiting for server response...
GatewayClient: Received response: 'DENIED'
GatewayClient: HELLO rejected - invalid passkey
```

## Troubleshooting

### Problem: "Connection refused"

**Ursache**: Server läuft nicht oder falscher Port

**Lösung**:
- Prüfen Sie, ob der Server gestartet ist
- Prüfen Sie den Port in den Einstellungen

### Problem: "Gateway rejected: invalid passkey"

**Ursache**: Passkey auf Client und Server stimmen nicht überein

**Lösung**:
- Gleichen Passkey auf beiden Seiten eintragen
- Auf Leerzeichen am Anfang/Ende achten

### Problem: "Server closed connection"

**Ursache**: Server ist nicht im Gateway-Modus

**Lösung**:
- Im Server die Option "Route via gateway" aktivieren
- Server neu starten

### Problem: "Gateway busy: another client connected"

**Ursache**: Es ist bereits ein anderer Client verbunden

**Lösung**:
- Nur ein Client kann gleichzeitig verbunden sein
- Warten Sie, bis der andere Client die Verbindung beendet

## Sicherheitsempfehlungen

1. **Verwenden Sie TOKEN-Authentifizierung** statt PASSKEY
2. **Aktivieren Sie Verschlüsselung** für Verbindungen über öffentliche Netzwerke
3. **Verwenden Sie starke Passwörter** (min. 16 Zeichen, zufällig generiert)
4. **Rotieren Sie Passwörter regelmäßig**
5. **Verwenden Sie zusätzlich VPN oder TLS** wenn möglich

## Zukünftige Erweiterungen

- **OAuth 2.0**: Geplant für eine zukünftige Version
- **mTLS**: Gegenseitige TLS-Authentifizierung
- **Key Rotation**: Automatische Schlüsselrotation

