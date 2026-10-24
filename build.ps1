param(
    # 'repo' = JAR nur im Repository-Ordner (bisheriges Verhalten),
    # 'addons' = zusaetzlich in den LabyMod-Addon-Ordner einsetzen.
    # Ohne Wert wird interaktiv gefragt.
    [ValidateSet('', 'repo', 'addons')]
    [string]$Target = '',
    # Optionaler uebersteuerter Addon-Ordner (voller Pfad bis inclusive
    # .../labymod-neo/addons), falls eine Instanz nicht unter %APPDATA%/.minecraft liegt
    [string]$AddonsDir = ''
)
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
$meta = Get-Content -LiteralPath (Join-Path $projectDir 'resources\addon.json') -Raw | ConvertFrom-Json
$outputJar = Join-Path $projectDir ('Mermil-Paradis-' + $meta.version + '-release.jar')
& $jarTool --create --file $outputJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'JAR konnte nicht erstellt werden.' }
Write-Output "Erstellt: $outputJar"

function Find-AddonFolders {
    $found = @()
    $gameRoot = Join-Path $env:APPDATA '.minecraft'
    if (Test-Path -LiteralPath $gameRoot) {
        $hit = Join-Path $gameRoot 'labymod-neo\addons'
        if (Test-Path -LiteralPath $hit) { $found += $hit }
    }
    return , $found
}

function Deploy-AddonJar {
    param([string]$JarPath, [string]$AddonsTarget)
    if (!(Test-Path -LiteralPath $AddonsTarget)) { throw "Addon-Ordner nicht gefunden: $AddonsTarget" }
    # Nur alte SkinCopy-/Mermil-JARs entfernen, fremde Addons bleiben unberuehrt.
    Get-ChildItem -LiteralPath $AddonsTarget -Filter '*.jar' | Where-Object {
        $_.Name -like '*SkinCopy*.jar' -or $_.Name -like 'Mermil-Paradis-*.jar'
    } | Remove-Item -Force
    Copy-Item -LiteralPath $JarPath -Destination (Join-Path $AddonsTarget (Split-Path $JarPath -Leaf)) -Force
    Write-Output "Eingesetzt: $AddonsTarget"
}

if ($Target -eq '') {
    Write-Host ''
    Write-Host 'Ziel fuer die JAR:'
    Write-Host ' [1] Nur diesen Repository-Ordner (Standard)'
    Write-Host ' [2] Zusaetzlich in den LabyMod-Addon-Ordner einsetzen'
    $choice = Read-Host 'Auswahl (1/2, Enter = 1)'
    $Target = if ($choice -eq '2') { 'addons' } else { 'repo' }
}

if ($Target -eq 'addons') {
    $addonsTarget = ''
    if ($AddonsDir.Trim() -ne '') {
        $addonsTarget = $AddonsDir.Trim()
    } else {
        $candidates = @(Find-AddonFolders)
        if ($candidates.Count -eq 1) {
            Write-Output "Addon-Ordner gefunden: $($candidates[0])"
            $confirm = Read-Host 'Diesen Ordner benutzen? (j/n)'
            if ($confirm -match '^[jJ]') { $addonsTarget = $candidates[0] }
        }
        if ($addonsTarget -eq '') {
            $manual = Read-Host 'Pfad zum Addon-Ordner eingeben (.../labymod-neo/addons, leer = Abbrechen)'
            if ($manual.Trim() -ne '') { $addonsTarget = $manual.Trim() }
        }
    }
    if ($addonsTarget -ne '') {
        Deploy-AddonJar -JarPath $outputJar -AddonsTarget $addonsTarget
    } else {
        Write-Output 'Kein Addon-Ordner angegeben. JAR bleibt im Repository-Ordner.'
    }
}
