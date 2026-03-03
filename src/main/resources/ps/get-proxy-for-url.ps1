# Script to resolve proxy for a given URL using Windows system proxy settings (WPAD/PAC)
# Usage: get-proxy-for-url.ps1 <url>
# Output: DIRECT or PROXY host:port or PROXY host1:port1; PROXY host2:port2; DIRECT
# Exit code: 0 on success, 1 on error

param(
    [Parameter(Mandatory=$true)]
    [string]$Url
)

try {
    # Use .NET WebRequest to get system proxy
    $uri = New-Object System.Uri($Url)
    $proxyObject = [System.Net.WebRequest]::GetSystemWebProxy()
    
    if ($proxyObject -eq $null) {
        Write-Output "DIRECT"
        exit 0
    }
    
    # Check if proxy is bypassed for this URL
    $isBypassed = $proxyObject.IsBypassed($uri)
    if ($isBypassed) {
        Write-Output "DIRECT"
        exit 0
    }
    
    # Get proxy URI for this URL
    $proxyUri = $proxyObject.GetProxy($uri)
    
    if ($proxyUri -eq $null -or $proxyUri.AbsoluteUri -eq $uri.AbsoluteUri) {
        Write-Output "DIRECT"
        exit 0
    }
    
    # Extract host and port from proxy URI
    $proxyHost = $proxyUri.Host
    $proxyPort = $proxyUri.Port
    
    if ([string]::IsNullOrEmpty($proxyHost)) {
        Write-Output "DIRECT"
        exit 0
    }
    
    Write-Output "PROXY $proxyHost`:$proxyPort"
    exit 0
    
} catch {
    Write-Error "Failed to resolve proxy: $_"
    exit 1
}

