# Create minimal OpenSSL config for CA (if not exists)
$opensslConfigPath = Join-Path (Get-Location) "openssl.cnf"
if (-not (Test-Path $opensslConfigPath)) {
    @"
[ req ]
distinguished_name = req_distinguished_name
x509_extensions = v3_ca
prompt = no

[ req_distinguished_name ]
CN = MyProxy-Dev-CA

[ v3_ca ]
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always,issuer
basicConstraints = critical, CA:true
keyUsage = critical, keyCertSign, cRLSign
"@ | Out-File -Encoding ascii $opensslConfigPath
    Write-Host "Created openssl.cnf"
} else {
    Write-Host "openssl.cnf already exists"
}

# Create CA private key (if not exists)
$caKeyPath = Join-Path (Get-Location) "myproxy-ca.key"
if (-not (Test-Path $caKeyPath)) {
    Write-Host "Generating myproxy-ca.key ..."
    openssl genrsa -out "myproxy-ca.key" 4096
} else {
    Write-Host "myproxy-ca.key already exists"
}

# Create CA certificate (if not exists)
$caCrtPath = Join-Path (Get-Location) "myproxy-ca.crt"
if (-not (Test-Path $caCrtPath)) {
    Write-Host "Generating myproxy-ca.crt ..."
    openssl req -x509 -new -key "myproxy-ca.key" `
      -sha256 -days 3650 `
      -config "openssl.cnf" `
      -out "myproxy-ca.crt"
} else {
    Write-Host "myproxy-ca.crt already exists"
}
