param([string]$JavaHome='', [string]$GradleHome='', [switch]$Offline)
$ErrorActionPreference='Stop'
if (!$JavaHome) { $JavaHome=$env:JAVA_HOME }
if (!$JavaHome -or !(Test-Path "$JavaHome/bin/java.exe")) { throw 'Set JAVA_HOME or pass -JavaHome with JDK 17.' }
$env:JAVA_HOME=$JavaHome
$env:PYTHONUTF8='1'
$env:JAVA_TOOL_OPTIONS='-Dfile.encoding=UTF-8'

if (!$env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME=Join-Path $PSScriptRoot '.gradle-user-home' }
$gradle=if($GradleHome){Join-Path $GradleHome 'bin/gradle.bat'}else{Join-Path $PSScriptRoot 'gradlew.bat'}
$argsList=@('build','--console=plain');if($Offline){$argsList+='--offline';$argsList+='-Dnet.minecraftforge.gradle.check.certs=false'}
Push-Location $PSScriptRoot
try { & $gradle @argsList; if($LASTEXITCODE-ne 0){throw 'Gradle failed'}; python -m unittest discover -s tests -v; if($LASTEXITCODE-ne 0){throw 'Python tests failed'} } finally {Pop-Location}
