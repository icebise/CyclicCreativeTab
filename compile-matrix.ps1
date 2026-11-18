param(
    [string[]]$Groups = @("1.21.1-1.21.8", "1.21.9-1.21.11", "26.1-26.3")
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25.0.4"
$log = Join-Path $root "compile-matrix.log"
if (Test-Path $log) { Remove-Item $log }

$matrix = @{
    "1.21.1-1.21.8"  = @("1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7", "1.21.8")
    "1.21.9-1.21.11" = @("1.21.9", "1.21.10", "1.21.11")
    "26.1-26.3"      = @("26.1", "26.2", "26.3")
}

$failed = @()

foreach ($g in $Groups) {
    foreach ($v in $matrix[$g]) {
        $dir = Join-Path $root $g
        Write-Output "[$v] building in $g ..."
        Add-Content $log "=== $g / $v"
        Push-Location $dir
        $out = & ".\gradlew.bat" build --console=plain "-Pmc=$v" 2>&1
        $code = $LASTEXITCODE
        Pop-Location
        Add-Content $log $out
        if ($code -ne 0) {
            Write-Output "[$v] FAILED ($code)"
            $out | Select-String -Pattern "error:|warning:|FAILURE|What went wrong|Could not" -SimpleMatch:$false | Select-Object -First 40 | ForEach-Object { Write-Output "   $_" }
            $failed += "$g/$v"
        } else {
            Write-Output "[$v] OK"
        }
    }
}

Write-Output ""
if ($failed.Count -eq 0) {
    Write-Output "ALL OK"
} else {
    Write-Output "FAILED: $($failed -join ', ')"
}
