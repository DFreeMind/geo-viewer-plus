[CmdletBinding()]
param(
    [ValidateSet("patch", "minor", "major")]
    [string]$Bump = "patch",
    [string]$NotesFile,
    [switch]$NoPush
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repo

if (-not $env:ORG_GRADLE_PROJECT_intellijPlatformPublishingToken) {
    throw "Missing ORG_GRADLE_PROJECT_intellijPlatformPublishingToken. Create a JetBrains Marketplace token and set it as a local environment variable first."
}

if ((git status --porcelain)) {
    throw "Working tree is not clean. Commit or stash other changes before publishing."
}

$propertiesPath = Join-Path $repo "gradle.properties"
$lines = Get-Content -LiteralPath $propertiesPath
$versionLine = $lines | Where-Object { $_ -match '^pluginVersion=(\d+)\.(\d+)\.(\d+)$' } | Select-Object -First 1
if (-not $versionLine) {
    throw "gradle.properties must contain pluginVersion=MAJOR.MINOR.PATCH"
}

$match = [regex]::Match($versionLine, '^pluginVersion=(\d+)\.(\d+)\.(\d+)$')
$major = [int]$match.Groups[1].Value
$minor = [int]$match.Groups[2].Value
$patch = [int]$match.Groups[3].Value
switch ($Bump) {
    "major" { $major++; $minor = 0; $patch = 0 }
    "minor" { $minor++; $patch = 0 }
    "patch" { $patch++ }
}
$newVersion = "$major.$minor.$patch"
$notesPath = if ($NotesFile) { (Resolve-Path -LiteralPath $NotesFile).Path } else { Join-Path $repo "release-notes\$newVersion.html" }
if (-not (Test-Path -LiteralPath $notesPath)) {
    throw "Release notes were not found at $notesPath. Create release-notes\$newVersion.html or pass -NotesFile."
}
$newLines = $lines | ForEach-Object {
    if ($_ -match '^pluginVersion=') { "pluginVersion=$newVersion" } else { $_ }
}
Set-Content -LiteralPath $propertiesPath -Value $newLines -Encoding utf8
$env:ORG_GRADLE_PROJECT_releaseNotesFile = $notesPath

$gradle = Join-Path $repo "tools\gradle-8.10.2\bin\gradle.bat"
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "Gradle was not found at $gradle"
}
if (Test-Path -LiteralPath "C:\Program Files\Java\jdk-17") {
    $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
}

Write-Host "Publishing Geo Viewer Plus v$newVersion..." -ForegroundColor Cyan
& $gradle publishPlugin --no-daemon
if ($LASTEXITCODE -ne 0) {
    throw "publishPlugin failed. The version file was left at $newVersion for inspection."
}

git add gradle.properties
git commit -m "Release Geo Viewer Plus v$newVersion"
git tag "v$newVersion"
if (-not $NoPush) {
    git push origin main
    git push origin "v$newVersion"
}

Write-Host "Published v$newVersion successfully." -ForegroundColor Green
