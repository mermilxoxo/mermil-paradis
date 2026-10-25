$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$jdkDir = Join-Path $env:APPDATA 'LabyMod\runtime\java-runtime-delta\bin'
$compiler = Join-Path $jdkDir 'javac.exe'
$java = Join-Path $jdkDir 'java.exe'
$jarTool = Join-Path $jdkDir 'jar.exe'
$meta = Get-Content -LiteralPath (Join-Path $projectDir 'resources\addon.json') -Raw | ConvertFrom-Json
$addonJar = Join-Path $projectDir ('Mermil-Paradis-' + $meta.version + '-release.jar')
$depsDir = Join-Path $projectDir 'build\compile-deps'
$dependencies = @('labymod.jar', 'gson.jar', 'mixin.jar') | ForEach-Object { Join-Path $depsDir $_ }
foreach ($required in @($compiler, $java, $jarTool, $addonJar) + $dependencies) {
    if (!(Test-Path -LiteralPath $required)) { throw 'JDK, Addon-JAR oder Bibliothek fehlt. Zuerst build.ps1 ausfuehren.' }
}
$classPath = $addonJar + ';' + ($dependencies -join ';')
$testRoot = Join-Path $projectDir ('build\tests-' + [Guid]::NewGuid().ToString('N'))
$testClasses = Join-Path $testRoot 'classes'
$stubClasses = Join-Path $testRoot 'stub'
New-Item -ItemType Directory -Force -Path $testClasses, $stubClasses | Out-Null
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $projectDir 'tests\de') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $compiler --release 21 -encoding UTF-8 -proc:none -classpath $classPath -d $testClasses @testSources
if ($LASTEXITCODE -ne 0) { throw 'Test-Kompilierung fehlgeschlagen.' }
$testJar = Join-Path $testRoot 'tests.jar'
& $jarTool --create --file $testJar -C $testClasses .
if ($LASTEXITCODE -ne 0) { throw 'Test-JAR konnte nicht erstellt werden.' }
$stubSources = @(Get-ChildItem -LiteralPath (Join-Path $projectDir 'tests\test-api') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $compiler --release 21 -encoding UTF-8 -proc:none -classpath $classPath -d $stubClasses @stubSources
if ($LASTEXITCODE -ne 0) { throw 'Test-Stub-Kompilierung fehlgeschlagen.' }
$stubJar = Join-Path $testRoot 'test-api.jar'
& $jarTool --create --file $stubJar -C $stubClasses .
if ($LASTEXITCODE -ne 0) { throw 'Test-Stub-JAR konnte nicht erstellt werden.' }
$testClassPath = $testJar + ';' + $classPath
foreach ($testClass in @('de.skincopy.SkinDataTest', 'de.skincopy.ServiceStatusTest',
        'de.skincopy.SkinBackupsTest', 'de.skincopy.ParadisLinksTest',
        'de.skincopy.lookup.ProfileSkinTest', 'de.skincopy.lookup.HistoryDataTest', 'de.skincopy.lookup.HistoryComparisonTest')) {
    & $java -Xverify:all -classpath $testClassPath $testClass $addonJar
    if ($LASTEXITCODE -ne 0) { throw "Test fehlgeschlagen: $testClass" }
}
& $java -Xverify:all -classpath ($stubJar + ';' + $testClassPath) de.skincopy.LiveSkinOverrideTest
if ($LASTEXITCODE -ne 0) { throw 'LiveSkinOverrideTest fehlgeschlagen.' }
Write-Output 'Alle isolierten Tests bestanden. Ein Spieltest ist weiterhin erforderlich.'
