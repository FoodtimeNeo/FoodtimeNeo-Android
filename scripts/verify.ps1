param([switch]$Device)
$ErrorActionPreference = 'Stop'
$projectPath = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectPath
try {
    & .\gradlew.bat assembleDebug testDebugUnitTest lintDebug --no-configuration-cache --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Build, unit tests or lint failed.' }
    if ($Device) {
        & .\gradlew.bat assembleDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Device test package build failed.' }
        & adb install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Application installation failed.' }
        & adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Device test installation failed.' }
        $acceptanceResult = & adb shell am instrument -w cn.edu.bjtu.foodtime.test/cn.edu.bjtu.foodtime.AcceptanceRunner
        $acceptanceResult | Write-Output
        if ($LASTEXITCODE -ne 0 -or ($acceptanceResult -join "`n") -notmatch 'PASS: \d+ acceptance assertions') {
            throw 'Device acceptance did not pass.'
        }
    }
} finally { Pop-Location }
