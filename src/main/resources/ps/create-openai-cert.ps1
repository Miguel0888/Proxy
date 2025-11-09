param(
    [string]$HostName = "api.openai.com",
    [string]$KeyStoreFile = "myproxy.jks",
    [string]$KeyStorePassword = "changeit"
)

# Determine working directory (where this script and openssl.cnf live)
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

# Paths
$configPath = Join-Path $scriptDir "openssl.cnf"
$caKey      = Join-Path $scriptDir "myproxy-ca.key"
$caCrt      = Join-Path $scriptDir "myproxy-ca.crt"

if (-not (Test-Path $configPath)) { throw "openssl.cnf not found in $scriptDir" }
if (-not (Test-Path $caKey))      { throw "myproxy-ca.key not found in $scriptDir" }
if (-not (Test-Path $caCrt))      { throw "myproxy-ca.crt not found in $scriptDir" }

Write-Host "Working directory: $scriptDir"
Write-Host "Using openssl.cnf, myproxy-ca.key, myproxy-ca.crt from this folder."

# 1) SAN config for target host
$sanFile = Join-Path $scriptDir "$HostName-san.cnf"
@"
[ v3_req ]
subjectAltName = DNS:$HostName
"@ | Out-File -Encoding ascii $sanFile
Write-Host "Created $sanFile"

# 2) Host private key
$hostKey = Join-Path $scriptDir "$HostName.key"
if (-not (Test-Path $hostKey)) {
    Write-Host "Generating $HostName key..."
    openssl genrsa -out "$hostKey" 2048
} else {
    Write-Host "$HostName.key already exists"
}

# 3) CSR mit expliziter Config
$csrFile = Join-Path $scriptDir "$HostName.csr"
Write-Host "Generating $HostName CSR..."
openssl req -new -key "$hostKey" -subj "/CN=$HostName" -config "$configPath" -out "$csrFile"

if (-not (Test-Path $csrFile)) {
    throw "Failed to create CSR $csrFile"
}

# 4) Zertifikat mit CA und SAN signieren
$hostCrt = Join-Path $scriptDir "$HostName.crt"
Write-Host "Signing $HostName certificate with CA..."
openssl x509 -req `
  -in "$csrFile" `
  -CA "$caCrt" -CAkey "$caKey" -CAcreateserial `
  -out "$hostCrt" -days 365 -sha256 `
  -extfile "$sanFile" -extensions v3_req

if (-not (Test-Path $hostCrt)) {
    throw "Failed to create certificate $hostCrt"
}

# 5) PKCS12 bauen
$pfxFile = Join-Path $scriptDir "$HostName.p12"
Write-Host "Creating PKCS12 container $pfxFile ..."
openssl pkcs12 -export `
  -inkey "$hostKey" -in "$hostCrt" -name "$HostName" `
  -out "$pfxFile" -password pass:$KeyStorePassword

if (-not (Test-Path $pfxFile)) {
    throw "Failed to create PKCS12 file $pfxFile"
}

# 6) JKS Keystore für Java
$keyStorePath = Join-Path $scriptDir "$KeyStoreFile"
Write-Host "Creating JKS keystore $keyStorePath ..."
keytool -importkeystore `
  -srckeystore "$pfxFile" -srcstoretype PKCS12 -srcstorepass $KeyStorePassword `
  -destkeystore "$keyStorePath" -deststoretype JKS -deststorepass $KeyStorePassword `
  -alias "$HostName" -noprompt

if (-not (Test-Path $keyStorePath)) {
    throw "Failed to create JKS keystore $keyStorePath"
}

Write-Host "Done. Generated:"
Write-Host " - $hostKey"
Write-Host " - $hostCrt"
Write-Host " - $pfxFile"
Write-Host " - $keyStorePath"
