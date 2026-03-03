# Test script für Gateway-Verbindung
# Verwendung: .\test-gateway-connection.ps1 -Server "192.168.1.100" -Port 8888 -Passkey "meinpasskey"

param(
    [Parameter(Mandatory=$true)]
    [string]$Server,
    
    [Parameter(Mandatory=$false)]
    [int]$Port = 8888,
    
    [Parameter(Mandatory=$false)]
    [string]$Passkey = ""
)

Write-Host "=== Gateway Connection Test ===" -ForegroundColor Cyan
Write-Host "Server: $Server"
Write-Host "Port: $Port"
Write-Host "Passkey: $(if ($Passkey) { '****' } else { '(empty)' })"
Write-Host ""

try {
    Write-Host "1. Creating TCP connection..." -ForegroundColor Yellow
    $client = New-Object System.Net.Sockets.TcpClient
    $client.ReceiveTimeout = 10000  # 10 seconds
    $client.SendTimeout = 10000
    
    $client.Connect($Server, $Port)
    Write-Host "   TCP connected!" -ForegroundColor Green
    
    $stream = $client.GetStream()
    $writer = New-Object System.IO.StreamWriter($stream, [System.Text.Encoding]::UTF8)
    $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8)
    
    # HELLO senden
    $hello = if ($Passkey) { "HELLO $Passkey" } else { "HELLO" }
    Write-Host "2. Sending: '$hello'" -ForegroundColor Yellow
    $writer.WriteLine($hello)
    $writer.Flush()
    Write-Host "   Sent!" -ForegroundColor Green
    
    # Antwort lesen
    Write-Host "3. Waiting for response..." -ForegroundColor Yellow
    $response = $reader.ReadLine()
    
    if ($response -eq $null) {
        Write-Host "   ERROR: Server closed connection without response!" -ForegroundColor Red
        Write-Host "   This usually means:" -ForegroundColor Red
        Write-Host "   - Server is NOT in Gateway mode (enable 'Route via gateway')" -ForegroundColor Red
        Write-Host "   - Server is running but doesn't recognize HELLO command" -ForegroundColor Red
    } else {
        Write-Host "   Response: '$response'" -ForegroundColor $(if ($response -eq "OK") { "Green" } else { "Red" })
        
        switch ($response.ToUpper()) {
            "OK" { 
                Write-Host "   SUCCESS! Gateway connection established." -ForegroundColor Green 
            }
            "DENIED" { 
                Write-Host "   ERROR: Invalid passkey!" -ForegroundColor Red 
            }
            "BUSY" { 
                Write-Host "   ERROR: Another client already connected!" -ForegroundColor Red 
            }
            default { 
                Write-Host "   WARNING: Unexpected response" -ForegroundColor Yellow 
            }
        }
    }
    
    $client.Close()
    
} catch [System.Net.Sockets.SocketException] {
    Write-Host "ERROR: Socket exception - $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  Check if server is running and port is correct" -ForegroundColor Red
} catch [System.IO.IOException] {
    Write-Host "ERROR: IO exception - $($_.Exception.Message)" -ForegroundColor Red
} catch {
    Write-Host "ERROR: $($_.Exception.GetType().Name) - $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan

