param(
    [Parameter(Mandatory = $true)]
    [string] $Server,
    [switch] $AllowDirty
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $AllowDirty -and (git status --porcelain)) {
    throw 'There are uncommitted changes: commit them first, or use -AllowDirty.'
}
$version = (git rev-parse --short HEAD).Trim()
if ($AllowDirty -and (git status --porcelain)) {
    $version = "$version-dirty"
}

Write-Host "Building $version..."
mvn -q clean package
if ($LASTEXITCODE -ne 0) {
    throw 'The build or the tests failed: nothing was deployed.'
}

Write-Host 'Uploading the jar...'
scp -q target/matchppl.jar "${Server}:matchppl.jar.new"
if ($LASTEXITCODE -ne 0) {
    throw 'The upload failed: nothing was deployed.'
}

Write-Host 'Installing on the server (sudo password of the server user)...'
ssh -t $Server "sudo /usr/local/bin/matchppl-update ~/matchppl.jar.new $version"
if ($LASTEXITCODE -ne 0) {
    throw 'The update failed on the server: see the messages above.'
}
