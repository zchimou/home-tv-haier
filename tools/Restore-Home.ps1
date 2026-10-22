param(
    [Parameter(Mandatory=$true)]
    [ValidatePattern('^[0-9.]+(?::[0-9]+)?$')][string]$TV,
    [ValidatePattern('^[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+$')]
    [string]$HomeComponent = 'com.youku.taitan.tv/com.youku.tv.home.activity.HomeActivity',
    [string]$AdbPath = 'adb',
    [switch]$CheckOnly
)
$ErrorActionPreference = 'Stop'
if ($TV -notmatch ':') { $TV += ':5555' }
$packageName = 'app.orchard.tv.haier'
if ($HomeComponent.StartsWith($packageName + '/')) { throw 'The recovery target must be another home app.' }
function Invoke-Tv([string]$Command) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $text = & $AdbPath -s $TV shell $Command 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($exitCode -ne 0) { throw ('ADB command failed: ' + $Command + "`n" + ($text -join "`n")) }
    return ($text -join "`n").Trim()
}
& $AdbPath connect $TV
if ($LASTEXITCODE -ne 0) { throw 'Cannot connect to the TV. Check the address and debugging authorization.' }
if ((Invoke-Tv 'getprop ro.product.model') -ne 'LRH670_2G_ALI7H_B') {
    throw 'This recovery script is verified only for Haier 65H5D / LRH670_2G_ALI7H_B.'
}
$targetPackage = $HomeComponent.Split('/')[0]
$installed = Invoke-Tv ('pm list packages --user 0 ' + $targetPackage)
if ($installed -notmatch ('(?m)^package:' + [regex]::Escape($targetPackage) + '$')) { throw 'The target home app is not installed.' }
Write-Output ('Current HOME: ' + (Invoke-Tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME'))
Write-Output ('Recovery target: ' + $HomeComponent)
if ($CheckOnly) { Write-Output 'Read-only check complete; no TV settings changed.'; return }
# Validate/set the target before withdrawing the current home app's takeover capability.
$null = Invoke-Tv ('cmd package set-home-activity --user 0 ' + $HomeComponent)
$haierInstalled = Invoke-Tv ('pm list packages --user 0 ' + $packageName)
if ($haierInstalled -match ('(?m)^package:' + [regex]::Escape($packageName) + '$')) {
    $null = Invoke-Tv 'am broadcast -a app.orchard.tv.haier.CONFIG -n app.orchard.tv.haier/app.orchard.tv.guard.ConfigReceiver --ez take_home false --ez keep_adb false --ez own_screensaver false'
    $null = Invoke-Tv 'cmd notification disallow_listener app.orchard.tv.haier/app.orchard.tv.guard.RevivalListener 0'
    $null = Invoke-Tv 'pm revoke app.orchard.tv.haier android.permission.WRITE_SECURE_SETTINGS'
    $null = Invoke-Tv 'appops set app.orchard.tv.haier SYSTEM_ALERT_WINDOW deny'
    $null = Invoke-Tv 'am force-stop app.orchard.tv.haier'
}
$enabled = Invoke-Tv 'settings get secure enabled_accessibility_services'
$others = @($enabled -split ':' | Where-Object {$_ -and $_ -ne 'null' -and $_ -notmatch '^app\.orchard\.tv\.haier/'})
if ($others.Count -eq 0) {
    $null = Invoke-Tv 'settings delete secure enabled_accessibility_services'
} else {
    foreach ($entry in $others) { if ($entry -notmatch '^[a-zA-Z0-9_.$]+/[a-zA-Z0-9_.$]+$') {throw 'Unexpected accessibility component format.'} }
    $null = Invoke-Tv ("settings put secure enabled_accessibility_services '" + ($others -join ':') + "'")
}
$null = Invoke-Tv ('am start -W -n ' + $HomeComponent)
Write-Output 'Target home opened. No app was uninstalled or disabled; app data was preserved.'
