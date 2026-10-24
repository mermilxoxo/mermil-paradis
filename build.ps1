$ErrorActionPreference = 'Stop'
$projectDir = $PSScriptRoot
$labyRoot = Join-Path $env:APPDATA 'LabyMod'
$jdkDir = Join-Path $labyRoot 'runtime\java-runtime-delta\bin'
$compiler = Join-Path $jdkDir 'javac.exe'
$jarTool = Join-Path $jdkDir 'jar.exe'
$labyJar = Join-Path $labyRoot 'libraries\net\labymod\LabyMod\4-production\LabyMod-4-production.jar'
$gsonRoot = Join-Path $labyRoot 'libraries\com\google\code\gson\gson'
$mixinRoot = Join-Path $labyRoot 'libraries\net\labymod\sponge-mixin'
if (!(Test-Path -LiteralPath $compiler)) { throw 'LabyMod Java 21 JDK nicht gefunden.' }
if (!(Test-Path -LiteralPath $labyJar)) { throw 'LabyMod Client-JAR nicht gefunden.' }
$gsonJar = Get-ChildItem -LiteralPath $gsonRoot -Recurse -Filter 'gson-*.jar' | Sort-Object FullName -Descending | Select-Object -First 1
if (!$gsonJar) { throw 'Gson-Bibliothek von LabyMod nicht gefunden.' }
$mixinJar = Get-ChildItem -LiteralPath $mixinRoot -Recurse -Filter '*.jar' | Sort-Object FullName -Descending | Select-Object -First 1
if (!$mixinJar) { throw 'Mixin-Bibliothek von LabyMod nicht gefunden.' }
$buildDir = Join-Path $projectDir 'build'
$classesDir = Join-Path $buildDir ('classes-' + [Guid]::NewGuid().ToString('N'))
$depsDir = Join-Path $buildDir 'compile-deps'
New-Item -ItemType Directory -Force -Path $classesDir, $depsDir | Out-Null
# Work on copies: compilation never modifies the installed client libraries.
Copy-Item -LiteralPath $labyJar -Destination (Join-Path $depsDir 'labymod.jar') -Force
Copy-Item -LiteralPath $gsonJar.FullName -Destination (Join-Path $depsDir 'gson.jar') -Force
Copy-Item -LiteralPath $mixinJar.FullName -Destination (Join-Path $depsDir 'mixin.jar') -Force
$classPath = (Join-Path $depsDir 'labymod.jar') + ';' + (Join-Path $depsDir 'gson.jar') + ';' + (Join-Path $depsDir 'mixin.jar')
$sources = @(Get-ChildItem -LiteralPath (Join-Path $projectDir 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName)
& $compiler --release 21 -encoding UTF-8 -proc:none -classpath $classPath -d $classesDir @sources
if ($LASTEXITCODE -ne 0) { throw 'Kompilierung fehlgeschlagen.' }
Copy-Item -Path (Join-Path $projectDir 'resources\*') -Destination $classesDir -Recurse -Force
$outputJar = Join-Path $projectDir 'Mermil-Paradis-0.3.1-release.jar'
& $jarTool --create --file $outputJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR konnte nicht erstellt werden.' }
Write-Output "Erstellt: $outputJar"
