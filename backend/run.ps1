$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found!"
    exit 1
}

$line = Get-Content $envFile | Where-Object {
    $_ -match '^\s*MONGODB_URI\s*='
} | Select-Object -First 1

if (-not $line) {
    Write-Error "MONGODB_URI is missing from .env"
    exit 1
}

$env:MONGODB_URI = ($line -split '=', 2)[1].Trim()

& "$PSScriptRoot\gradlew.bat" bootRun
