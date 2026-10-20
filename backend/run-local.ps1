param()

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Development tokens expire across restarts unless you supply a stable JWT_SECRET.
# Generate the secret in process memory; never print or commit it.
if ([string]::IsNullOrWhiteSpace($env:JWT_SECRET)) {
    $secretBytes = New-Object byte[] 48
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($secretBytes) } finally { $random.Dispose() }
    $env:JWT_SECRET = [Convert]::ToBase64String($secretBytes)
}

$env:SERVER_ADDRESS = '127.0.0.1'
if ([string]::IsNullOrWhiteSpace($env:APP_MAIL_LOGIN_ALERT_ENABLED)) {
    $env:APP_MAIL_LOGIN_ALERT_ENABLED = 'false'
}

& mvn.cmd -B -ntp '-Dmaven.repo.local=../.cache/maven-repository' spring-boot:run
exit $LASTEXITCODE
