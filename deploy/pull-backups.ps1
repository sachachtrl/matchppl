param(
    [Parameter(Mandatory = $true)]
    [string] $Server,
    [string] $Destination = "$HOME\matchppl-backups",
    [int] $KeepDays = 60
)

$ErrorActionPreference = 'Stop'
$log = Join-Path $Destination 'pull-backups.log'

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
scp -q -p -o BatchMode=yes "${Server}:/var/backups/matchppl/*.age" $Destination
if ($LASTEXITCODE -ne 0) {
    Add-Content -Path $log -Value "$(Get-Date -Format s) FAILED: scp exited with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Get-ChildItem -Path $Destination -Filter 'matchppl-*.db.age' |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$KeepDays) } |
    Remove-Item

$count = (Get-ChildItem -Path $Destination -Filter 'matchppl-*.db.age').Count
Add-Content -Path $log -Value "$(Get-Date -Format s) OK: $count backups stored"
