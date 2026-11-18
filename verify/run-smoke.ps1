param(
    [string[]]$Groups = @("groupA", "groupB", "groupC"),
    [string[]]$Versions = @(),
    [string[]]$Modes = @("api", "builtin"),
    [switch]$SkipBuild,
    [switch]$WithModMenu,
    [int]$TimeoutSeconds = 1500
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projects = Split-Path -Parent $root
$results = Join-Path $root "results"
$summary = Join-Path $results "summary.txt"
New-Item -ItemType Directory -Force -Path $results | Out-Null

# `powershell -File` passes `-Versions 1.21.1,1.21.8` as one string, so split
# every list on commas before use.
$Groups = @($Groups | ForEach-Object { "$_" -split "," } | Where-Object { $_ })
$Versions = @($Versions | ForEach-Object { "$_" -split "," } | Where-Object { $_ })
$Modes = @($Modes | ForEach-Object { "$_" -split "," } | Where-Object { $_ })

$jdk21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$jdk25 = "C:\Program Files\Java\jdk-25.0.4"

$groupInfo = @{
    groupA = @{
        project   = "1.21.1-1.21.8"
        jar       = "CyclicCreativeTabs-fabric-1.0.0+1.21.1-1.21.8.jar"
        java      = $jdk21
        versions  = @("1.21.1", "1.21.8")
        loom      = "fabric-loom"
    }
    groupB = @{
        project   = "1.21.9-1.21.11"
        jar       = "CyclicCreativeTabs-fabric-1.0.0+1.21.9-1.21.11.jar"
        java      = $jdk21
        versions  = @("1.21.9", "1.21.11")
        loom      = "fabric-loom"
    }
    groupC = @{
        project   = "26.1-26.3"
        jar       = "CyclicCreativeTabs-fabric-1.0.0+26.1-26.3.jar"
        java      = $jdk25
        versions  = @("26.1", "26.3")
        loom      = "net.fabricmc.fabric-loom"
    }
}

$apiVersions = @{
    "1.21.1"  = "0.116.18+1.21.1"
    "1.21.2"  = "0.106.1+1.21.2"
    "1.21.3"  = "0.114.1+1.21.3"
    "1.21.4"  = "0.119.4+1.21.4"
    "1.21.5"  = "0.128.2+1.21.5"
    "1.21.6"  = "0.128.2+1.21.6"
    "1.21.7"  = "0.129.0+1.21.7"
    "1.21.8"  = "0.136.1+1.21.8"
    "1.21.9"  = "0.134.1+1.21.9"
    "1.21.10" = "0.138.4+1.21.10"
    "1.21.11" = "0.141.6+1.21.11"
    "26.1"    = "0.145.1+26.1"
    "26.2"    = "0.161.0+26.2"
    "26.3"    = "0.166.2+26.3"
}

$modmenuVersions = @{
    "1.21.1"  = "11.0.5"
    "1.21.8"  = "15.0.2"
    "1.21.9"  = "16.0.1"
    "1.21.11" = "17.0.1"
    "26.1"    = "18.0.2"
    "26.3"    = "21.0.0"
}

$lines = New-Object System.Collections.Generic.List[string]
$failed = New-Object System.Collections.Generic.List[string]

# cmd.exe's quote stripping mangles `cmd /c "program" args -Px="quoted path"`,
# so every command is written to a small .cmd file and executed from there.
function Invoke-CommandLine($commandLine, $workingDirectory, $javaHome, $logPath, $timeout) {
    $env:JAVA_HOME = $javaHome
    $script = $logPath + ".cmd"
    $lines = @(
        "@echo off",
        "cd /d `"$workingDirectory`"",
        $commandLine
    )
    Set-Content -Path $script -Value $lines -Encoding OEM
    $out = Start-Process -FilePath "cmd.exe" -ArgumentList @("/c", "`"$script`"") -WorkingDirectory $workingDirectory `
            -RedirectStandardOutput $logPath -RedirectStandardError ($logPath + ".err") -PassThru -WindowStyle Hidden
    $finished = $out.WaitForExit($timeout * 1000)
    if (-not $finished) {
        & taskkill /PID $out.Id /T /F | Out-Null
        return -1
    }
    $code = -1
    try {
        $code = [int] $out.ExitCode
    } catch {
        $code = -1
    }
    return $code
}

foreach ($group in $Groups) {
    $info = $groupInfo[$group]
    if ($null -eq $info) {
        Write-Output "unknown group $group"
        continue
    }
    $projectDir = Join-Path $projects $info.project
    $gradlew = Join-Path $projectDir "gradlew.bat"
    $jarPath = Join-Path $projectDir ("build/libs/" + $info.jar)

    if (-not $SkipBuild) {
        Write-Output "[$group] building the release jar ..."
        $buildLog = Join-Path $results ("build-" + $group + ".log")
        $code = Invoke-CommandLine "`"$gradlew`" build --console=plain --no-daemon" `
                $projectDir $info.java $buildLog 1800
        if (-not (Test-Path $jarPath)) {
            Write-Output "[$group] build FAILED (exit $code), see $buildLog"
            $failed.Add("$group build")
            continue
        }
    }
    if (-not (Test-Path $jarPath)) {
        Write-Output "[$group] release jar missing: $jarPath"
        $failed.Add("$group jar")
        continue
    }

    $wanted = if ($Versions.Count -gt 0) { $Versions } else { $info.versions }
    foreach ($version in $wanted) {
        foreach ($mode in $Modes) {
            $suffix = $mode + $(if ($WithModMenu) { "-modmenu" } else { "" })
            $runName = "$version-$suffix"
            $resultDir = Join-Path $results "$group/$runName"
            New-Item -ItemType Directory -Force -Path $resultDir | Out-Null
            $resultFile = Join-Path $resultDir "result.txt"
            Remove-Item $resultFile -ErrorAction SilentlyContinue

            $commandLine = "`"$gradlew`" runClient --console=plain --no-daemon -Pmc=$version -PmodJar=`"$jarPath`""
            if ($mode -eq "api") {
                $commandLine += " -PwithFabricApi -PfabricApiVersion=$($apiVersions[$version])"
            }
            if ($WithModMenu) {
                $commandLine += " -PwithModMenu -PmodmenuVersion=$($modmenuVersions[$version])"
            }

            Write-Output "[$group] $runName ..."
            $logPath = Join-Path $results "$group-$runName.log"
            $code = Invoke-CommandLine $commandLine (Join-Path $root $group) $info.java $logPath $TimeoutSeconds

            $status = "MISSING"
            $detail = ""
            if (Test-Path $resultFile) {
                $text = Get-Content $resultFile -Raw
                if ($text -match "(?m)^PASS\b") {
                    $status = "PASS"
                } elseif ($text -match "(?m)^FAIL\b") {
                    $status = "FAIL"
                }
                $detail = ($text -split "`n" | Where-Object { $_ -match "^FAIL " }) -join " | "
            }
            if ($status -eq "MISSING" -and $code -ne 0) {
                $status = "CRASH"
            }
            $line = "{0,-7} {1,-14} exit={2,-3} {3} {4}" -f $group, $runName, $code, $status, $detail
            Write-Output $line
            $lines.Add($line)
            if ($status -ne "PASS") {
                $failed.Add("$group/$runName ($status)")
            }
        }
    }
}

$lines.Add("")
if ($failed.Count -eq 0) {
    $lines.Add("ALL RUNS PASSED")
} else {
    $lines.Add("FAILED: " + ($failed -join ", "))
}
$lines | Set-Content -Encoding UTF8 $summary
Write-Output ""
$lines | Select-Object -Last 3 | ForEach-Object { Write-Output $_ }
