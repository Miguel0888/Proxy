param(
    [string]$CertFilePath = "$PSScriptRoot\myproxy-ca.crt"
)

Write-Host "=== Local MITM CA installation script ==="
Write-Host ""
Write-Host "This script will import a local CA certificate into the"
Write-Host '"Trusted Root Certification Authorities" store on Windows.'
Write-Host ""
Write-Host "ONLY use this for local debugging on your own machine."
Write-Host "Remove this CA from the trust store when no longer needed."
Write-Host ""

# Check OS
if ($env:OS -notlike "*Windows*") {
    Write-Host "ERROR: This script is only supported on Windows." -ForegroundColor Red
    exit 1
}

# Resolve certificate path
$CertFilePath = [System.IO.Path]::GetFullPath($CertFilePath)
Write-Host "Using CA certificate file: $CertFilePath"
if (-not (Test-Path -LiteralPath $CertFilePath)) {
    Write-Host "ERROR: Certificate file not found." -ForegroundColor Red
    exit 1
}

# Check for administrative privileges
$windowsIdentity = [Security.Principal.WindowsIdentity]::GetCurrent()
$windowsPrincipal = New-Object Security.Principal.WindowsPrincipal($windowsIdentity)
$hasAdminRights = $windowsPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $hasAdminRights) {
    Write-Host ""
    Write-Host "WARNING: This operation usually requires administrative privileges." -ForegroundColor Yellow
    Write-Host "If installation fails, run this script from an elevated PowerShell." -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "About to run:"
Write-Host "  certutil -addstore -f Root `"$CertFilePath`""
Write-Host ""

$confirmation = Read-Host "Type 'YES' to continue, or anything else to abort"
if ($confirmation -ne "YES") {
    Write-Host "Aborted by user."
    exit 0
}

Write-Host ""
Write-Host "Import CA certificate into Windows Root store..."
Write-Host ""

$certutilArgs = @(
    "-addstore",
    "-f",
    "Root",
    $CertFilePath
)

$processInfo = New-Object System.Diagnostics.ProcessStartInfo
$processInfo.FileName = "certutil.exe"
$processInfo.Arguments = $certutilArgs -join " "
$processInfo.UseShellExecute = $false
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $processInfo

$null = $process.Start()
$stdout = $process.StandardOutput.ReadToEnd()
$stderr = $process.StandardError.ReadToEnd()
$process.WaitForExit()

Write-Host $stdout
if ($stderr) {
    Write-Host $stderr -ForegroundColor Yellow
}

if ($process.ExitCode -eq 0) {
    Write-Host ""
    Write-Host "SUCCESS: CA certificate installed into 'Trusted Root Certification Authorities'." -ForegroundColor Green
    Write-Host "Verify in certmgr.msc under 'Trusted Root Certification Authorities' -> 'Certificates'."
    exit 0
} else {
    Write-Host ""
    Write-Host "ERROR: certutil failed with exit code $($process.ExitCode)." -ForegroundColor Red
    Write-Host "If this is an access error, run this script in an elevated PowerShell." -ForegroundColor Red
    exit $process.ExitCode
}
