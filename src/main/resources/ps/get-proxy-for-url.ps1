# Script to resolve proxy for a given URL using Windows system proxy settings (WPAD/PAC)
# Usage: get-proxy-for-url.ps1 -TestUrl "https://example.com/"
# Output: host:port (if proxy) or nothing/exit 0 (if direct)
# Exit code: 0 on success (direct or proxy found)

param(
    [string]$TestUrl = "https://plugins.gradle.org/m2/",
    [switch]$DebugEnabled
)

function Write-DebugLine([string]$msg) {
    if ($DebugEnabled) { Write-Host $msg }
}

$uri = [Uri]$TestUrl

$proxy = [System.Net.WebRequest]::GetSystemWebProxy()
$proxy.Credentials = [System.Net.CredentialCache]::DefaultNetworkCredentials

if ($proxy.IsBypassed($uri)) {
    Write-DebugLine ("[DEBUG] DIRECT for {0}" -f $TestUrl)
    exit 0
}

$proxyUri = $proxy.GetProxy($uri)

if (-not $proxyUri -or $proxyUri.AbsoluteUri -eq $uri.AbsoluteUri) {
    Write-DebugLine ("[DEBUG] DIRECT for {0}" -f $TestUrl)
    exit 0
}

Write-DebugLine ("[DEBUG] Proxy for {0} -> {1}" -f $TestUrl, $proxyUri.AbsoluteUri)
Write-Output ("{0}:{1}" -f $proxyUri.Host, $proxyUri.Port)
exit 0
