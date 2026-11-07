$envFile = Join-Path $PSScriptRoot ".env"

if (-not (Test-Path $envFile)) {
    Write-Error ".env file not found. Copy .env.example to .env and set MONGODB_URI."
    exit 1
}

$line = Get-Content $envFile | Where-Object {
    $_ -match '^\s*MONGODB_URI\s*=' -and $_ -notmatch '^\s*#'
} | Select-Object -First 1

if (-not $line) {
    Write-Error "MONGODB_URI is missing from .env"
    exit 1
}

$uri = ($line -split '=', 2)[1].Trim()
if ($uri.Length -ge 2 -and (
    ($uri.StartsWith('"') -and $uri.EndsWith('"')) -or
    ($uri.StartsWith("'") -and $uri.EndsWith("'"))
)) {
    $uri = $uri.Substring(1, $uri.Length - 2)
}

if ([string]::IsNullOrWhiteSpace($uri) -or $uri -eq "your_mongodb_connection_string") {
    Write-Error "MONGODB_URI in .env must contain a real MongoDB connection string."
    exit 1
}

if ($uri -notmatch '^mongodb(\+srv)?://') {
    Write-Error "MONGODB_URI must start with mongodb:// or mongodb+srv://."
    exit 1
}

$env:MONGODB_URI = $uri

$secretLine = Get-Content $envFile | Where-Object {
    $_ -match '^\s*JWT_SECRET\s*=' -and $_ -notmatch '^\s*#'
} | Select-Object -First 1
$jwtSecret = ""
if ($secretLine) {
    $jwtSecret = ($secretLine -split '=', 2)[1].Trim()
    if ($jwtSecret.Length -ge 2 -and (
        ($jwtSecret.StartsWith('"') -and $jwtSecret.EndsWith('"')) -or
        ($jwtSecret.StartsWith("'") -and $jwtSecret.EndsWith("'"))
    )) {
        $jwtSecret = $jwtSecret.Substring(1, $jwtSecret.Length - 2)
    }
}

if ([string]::IsNullOrWhiteSpace($jwtSecret)) {
    $secretBytes = New-Object byte[] 32
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($secretBytes)
    }
    finally {
        $randomGenerator.Dispose()
    }
    $jwtSecret = [Convert]::ToBase64String($secretBytes)

    if ($secretLine) {
        $envLines = [System.Collections.Generic.List[string]]::new()
        foreach ($envLine in Get-Content $envFile) {
            if ($envLine -match '^\s*JWT_SECRET\s*=' -and $envLine -notmatch '^\s*#') {
                $envLines.Add("JWT_SECRET=$jwtSecret")
            }
            else {
                $envLines.Add($envLine)
            }
        }
        [System.IO.File]::WriteAllLines($envFile, $envLines.ToArray())
    }
    else {
        Add-Content -Path $envFile -Value "JWT_SECRET=$jwtSecret"
    }
    Write-Output "Generated and saved a JWT signing secret in .env."
}

try {
    $decodedSecret = [Convert]::FromBase64String($jwtSecret)
    if ($decodedSecret.Length -lt 32) {
        throw "JWT_SECRET must decode to at least 32 bytes."
    }
}
catch {
    Write-Error "JWT_SECRET must be valid Base64 encoding of at least 32 random bytes."
    exit 1
}

$env:JWT_SECRET = $jwtSecret

& "$PSScriptRoot\gradlew.bat" bootRun
exit $LASTEXITCODE
