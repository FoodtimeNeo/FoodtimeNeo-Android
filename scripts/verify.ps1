param([switch]$Device,[string]$ApplicationSuffix='', [string]$AdbPath='adb', [string]$Serial='',
    [string]$UserAccount=$env:FOODTIME_TEST_USER_ACCOUNT,
    [string]$AdminAccount=$env:FOODTIME_TEST_ADMIN_ACCOUNT,
    [string]$SuperadminAccount=$env:FOODTIME_TEST_SUPERADMIN_ACCOUNT,
    [string]$TestPassword=$env:FOODTIME_TEST_PASSWORD)
$ErrorActionPreference='Stop'
if($ApplicationSuffix -notmatch '^(\.[a-zA-Z][a-zA-Z0-9_]*)*$') {throw 'Invalid application suffix'}
if($Device) {
    if($ApplicationSuffix -ne '.integration') {throw 'Device acceptance requires -ApplicationSuffix .integration'}
    if(@($UserAccount,$AdminAccount,$SuperadminAccount,$TestPassword) | Where-Object { [string]::IsNullOrWhiteSpace($_) }) {
        throw 'Provide UserAccount, AdminAccount, SuperadminAccount and TestPassword (or FOODTIME_TEST_* environment variables)'
    }
}
function Quote-AndroidShell([string]$Value) { return "'"+$Value.Replace("'", "'\''")+"'" }
$projectPath=Split-Path -Parent $PSScriptRoot
$deviceArgs=@();if($Serial) {$deviceArgs=@('-s',$Serial)}
Push-Location -LiteralPath $projectPath
try {
    $buildArgs=@('verifyArchitecture','assembleDebug','testDebugUnitTest','lintDebug','--no-configuration-cache','--console=plain',"-PFOODTIME_DEBUG_SUFFIX=$ApplicationSuffix")
    if($Device) {
        # Device tests create and mutate fixture records; always target the dedicated local Backend.
        $buildArgs+='-PFOODTIME_API_BASE_URL=http://127.0.0.1:8080/api/v1/'
        $buildArgs+='-PFOODTIME_COMMUNITY_PROTOTYPE=false'
        $buildArgs+='-PFOODTIME_TEST_RUNNER=cn.edu.bjtu.foodtime.presentation.RemoteUiRunner'
        $buildArgs+='assembleDebugAndroidTest'
    }
    & .\gradlew.bat @buildArgs
    if($LASTEXITCODE -ne 0) {throw 'Build, architecture, tests or lint failed'}
    if($Device) {
        $health=Invoke-RestMethod 'http://127.0.0.1:8080/actuator/health/readiness'
        if($health.status -ne 'UP') {throw 'Start the dedicated integration Backend first'}
        & $AdbPath @deviceArgs reverse tcp:8080 tcp:8080
        if($LASTEXITCODE -ne 0) {throw 'ADB reverse failed'}
        & $AdbPath @deviceArgs install -r app/build/outputs/apk/debug/app-debug.apk
        if($LASTEXITCODE -ne 0) {throw 'Application install failed; use -ApplicationSuffix .integration for a different-signature existing install'}
        & $AdbPath @deviceArgs install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if($LASTEXITCODE -ne 0) {throw 'Instrumentation install failed'}
        $package='cn.edu.bjtu.foodtime'+$ApplicationSuffix
        $runnerArgs=@('-e','userAccount',(Quote-AndroidShell $UserAccount),
            '-e','adminAccount',(Quote-AndroidShell $AdminAccount),
            '-e','superadminAccount',(Quote-AndroidShell $SuperadminAccount),
            '-e','testPassword',(Quote-AndroidShell $TestPassword))
        $result=& $AdbPath @deviceArgs shell am instrument -w @runnerArgs ($package+'.test/cn.edu.bjtu.foodtime.presentation.RemoteUiRunner')
        $result|Write-Output
        if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Remote UI PASS: \d+ checks') {throw 'Real Backend/UI acceptance failed'}
    }
} finally {Pop-Location}
