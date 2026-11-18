param(
    [switch]$SkipBuild
)

# Builds the three release jars and copies them into dist/ together with a
# checksum file. Run verify/run-smoke.ps1 before releasing.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$dist = Join-Path $root "dist"

$jdk21 = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
$jdk25 = "C:\Program Files\Java\jdk-25.0.4"

$projects = @(
    @{ directory = "1.21.1-1.21.8";   java = $jdk21; jar = "CyclicCreativeTabs-fabric-1.0.0+1.21.1-1.21.8.jar" },
    @{ directory = "1.21.9-1.21.11";  java = $jdk21; jar = "CyclicCreativeTabs-fabric-1.0.0+1.21.9-1.21.11.jar" },
    @{ directory = "26.1-26.3";       java = $jdk25; jar = "CyclicCreativeTabs-fabric-1.0.0+26.1-26.3.jar" }
)

New-Item -ItemType Directory -Force -Path $dist | Out-Null
Get-ChildItem $dist -Filter "*.jar" | Remove-Item -Force -ErrorAction SilentlyContinue

foreach ($project in $projects) {
    $directory = Join-Path $root $project.directory
    if (-not $SkipBuild) {
        Write-Output "[$($project.directory)] building ..."
        $env:JAVA_HOME = $project.java
        Push-Location $directory
        try {
            & (Join-Path $directory "gradlew.bat") build --console=plain --no-daemon
            if ($LASTEXITCODE -ne 0) {
                throw "the build of $($project.directory) failed with exit code $LASTEXITCODE"
            }
        } finally {
            Pop-Location
        }
    }
    $jar = Join-Path $directory ("build/libs/" + $project.jar)
    if (-not (Test-Path $jar)) {
        throw "the release jar was not produced: $jar"
    }
    Copy-Item $jar $dist -Force
    Write-Output "[$($project.directory)] $($project.jar)"
}

$checksums = Get-ChildItem $dist -Filter "*.jar" | Sort-Object Name | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $($_.Name)"
}
$checksums | Set-Content -Encoding ASCII (Join-Path $dist "SHA256SUMS.txt")
Write-Output ""
Write-Output "written to $dist"
$checksums | ForEach-Object { Write-Output $_ }
