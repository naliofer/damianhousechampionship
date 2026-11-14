$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$keytool = "$env:JAVA_HOME\bin\keytool.exe"
$signer = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat"
$env:CAMPEONATO_KEYSTORE_PATH = Join-Path $env:USERPROFILE '.android\campeonato-damiao\release.jks'
function Read-Password($message) {
    $secret = Read-Host $message -AsSecureString
    $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
}
try {
    Write-Host "Keystore: $env:CAMPEONATO_KEYSTORE_PATH"
    $env:CAMPEONATO_KEYSTORE_PASSWORD = Read-Password 'Defina/digite a senha da Keystore (minimo 6 caracteres)'
    $env:CAMPEONATO_KEY_PASSWORD = Read-Password 'Defina/digite a senha da chave (minimo 6 caracteres)'
    if ($env:CAMPEONATO_KEYSTORE_PASSWORD.Length -lt 6 -or $env:CAMPEONATO_KEY_PASSWORD.Length -lt 6) { throw 'As senhas precisam ter pelo menos 6 caracteres.' }
    if (!(Test-Path -LiteralPath $env:CAMPEONATO_KEYSTORE_PATH)) {
        if ((Read-Password 'Confirme a senha da Keystore') -cne $env:CAMPEONATO_KEYSTORE_PASSWORD) { throw 'Confirmacao da Keystore diferente.' }
        if ((Read-Password 'Confirme a senha da chave') -cne $env:CAMPEONATO_KEY_PASSWORD) { throw 'Confirmacao da chave diferente.' }
        New-Item -ItemType Directory -Force -Path (Split-Path $env:CAMPEONATO_KEYSTORE_PATH) | Out-Null
        & $keytool -genkeypair -keystore $env:CAMPEONATO_KEYSTORE_PATH -storetype JKS -alias campeonato-damiao -keyalg RSA -keysize 3072 -validity 9132 -dname 'CN=Campeonato Damiao' -storepass:env CAMPEONATO_KEYSTORE_PASSWORD -keypass:env CAMPEONATO_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar Keystore.' }
    }
    & .\gradlew.bat --no-daemon --no-configuration-cache assembleRelease
    if ($LASTEXITCODE -ne 0) { throw 'Falha no build release.' }
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
    $report = & $signer verify --verbose --print-certs $apk 2>&1
    $verifyCode = $LASTEXITCODE
    $report | Set-Content -Encoding UTF8 'app\build\outputs\apk\release\signature-verification.txt'
    $report | ForEach-Object { Write-Host $_ }
    if ($verifyCode -ne 0) { throw 'A verificacao da assinatura falhou.' }
    Write-Host "APK assinado e verificado: $apk"
} catch {
    Write-Host "ERRO: $_" -ForegroundColor Red
} finally {
    Remove-Item Env:CAMPEONATO_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:CAMPEONATO_KEY_PASSWORD -ErrorAction SilentlyContinue
    Read-Host 'Pressione Enter para fechar'
}
