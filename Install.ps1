param([string]$Instance='D:/Games/mc/Cheat/versions/1.20.1-Forge_47.4.28-test')
$ErrorActionPreference='Stop'
$source=Join-Path $PSScriptRoot 'versions/1.20.1-forge/build/libs/mcpmcp-1.20.1-forge-0.1.2.jar'
if(!(Test-Path -LiteralPath $source)){throw 'Build the artifact first.'}
$resolved=[IO.Path]::GetFullPath($Instance)
if([IO.Path]::GetFileName($resolved)-ne '1.20.1-Forge_47.4.28-test'){throw 'This installer is limited to the authorized test instance.'}
$mods=Join-Path $resolved 'mods';New-Item -ItemType Directory -Path $mods -Force|Out-Null
$existing=Get-ChildItem -LiteralPath $mods -Filter 'mcpmcp-*.jar'
if($existing){$backup=Join-Path $PSScriptRoot ('.local/backups/'+(Get-Date -Format 'yyyyMMdd-HHmmss'));New-Item -ItemType Directory $backup -Force|Out-Null;$existing|ForEach-Object{Move-Item -LiteralPath $_.FullName -Destination $backup}}
$target=Join-Path $mods ([IO.Path]::GetFileName($source));Copy-Item -LiteralPath $source -Destination $target
if((Get-FileHash -LiteralPath $source).Hash-ne(Get-FileHash -LiteralPath $target).Hash){throw 'Installed hash mismatch'}
Write-Output "Installed and hash-verified: $target"
