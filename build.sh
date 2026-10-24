#!/usr/bin/env bash
# Linux-Variante zu build.ps1: baut die Addon-JAR (siehe README "Bauen") und
# kann sie zusaetzlich in den LabyMod-Addon-Ordner einsetzen.
#
# Aufrufe:
#   ./build.sh
#   ./build.sh repo
#   ./build.sh addons
#   ./build.sh addons --addons-dir /pfad/spielverzeichnis/labymod-neo/addons
#   ./build.sh addons --laby-root /pfad/labymod-datenordner
#
# Suchreihenfolge:
#   - JDK ab 21: PATH, $HOME/.gradle/jdks/*, sdkman, LabyMod-Runtimes,
#     /usr/lib/jvm*, /opt/* (jeder Kandidat wird auf Version >= 21 geprueft)
#   - LabyMod-4-Client-JAR: bekannte LabyMod-Ordner und Launcher-Daten (Prism,
#     PolyMC, PollyMC, MultiMC, ATLauncher, Technic, Modrinth, CurseForge,
#     GDLauncher), bevorzugt die Instanz zur Minecraft-Version aus
#     resources/addon.json; zuletzt globaler Suchlauf unter $HOME
#   - gson/sponge-mixin: Bibliothekscaches neben der Client-JAR, sonst
#     Suchlauf bis $HOME
#   - Addon-Ordner: labymod-neo/addons unter den Spielordnern der Launcher-
#     Instanzen (.minecraft/labymod-neo/addons) und direkten LabyMod-Ordnern
#
# Umgebungsvariablen (uebersteuern alles Automatische):
#   LABY_ROOT
#   LABY_MOD_JAR
#   LABY_GSON_JAR
#   LABY_MIXIN_JAR
#   ADDONS_DIR
#
# Benoetigt bash sowie find, sort, sed, awk; python3 optional.
set -euo pipefail

project_dir="$(cd "$(dirname "$0")" && pwd)"
build_dir="$project_dir/build"

usage() { sed -n '2,29p' "$0"; exit 0; }

# ---- Argumente ----
TARGET=''
ADDONS_DIR="${ADDONS_DIR:-}"
LABY_ROOT_ARG=''
while [ $# -gt 0 ]; do
    case "$1" in
        repo|addons) TARGET="$1" ;;
        --addons-dir) shift; ADDONS_DIR="${1:-}" ;;
        --laby-root) shift; LABY_ROOT_ARG="${1:-}" ;;
        -h|--help|help) usage ;;
        *) echo "Unbekanntes Argument: $1" >&2
           echo "Verfuegbar: repo | addons | --addons-dir PFAD | --laby-root PFAD | -h" >&2
           exit 2 ;;
    esac
    shift
done

# ---- Metadaten aus resources/addon.json ----
version="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["version"])' \
    "$project_dir/resources/addon.json" 2>/dev/null \
    || sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$project_dir/resources/addon.json")"
mc_version="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["compatibleMinecraftVersions"])' \
    "$project_dir/resources/addon.json" 2>/dev/null \
    || sed -n 's/.*"compatibleMinecraftVersions": *"\([^"]*\)".*/\1/p' "$project_dir/resources/addon.json")"
if [ -z "$version" ]; then
    echo 'Version aus resources/addon.json nicht lesbar.' >&2
    exit 1
fi
[ -n "$mc_version" ] || mc_version='x-none-x'

# ---- Java 21+ suchen ----
javac_major() { "$1" -version 2>&1 | awk '{print $2}' | cut -d. -f1; }

find_javac() {
    local try
    if [ -n "$LABY_ROOT_ARG" ]; then
        try="$LABY_ROOT_ARG/runtime/java-runtime-delta/bin/javac"
        if [ -x "$try" ] && [ "$(javac_major "$try")" -ge 21 ]; then echo "$try"; return 0; fi
    fi
    if command -v javac >/dev/null 2>&1; then
        try="$(command -v javac)"
        if [ "$(javac_major "$try")" -ge 21 ]; then echo "$try"; return 0; fi
    fi
    for try in "$HOME/.gradle/jdks/"*"/bin/javac" \
               "$HOME/.sdkman/candidates/java/"*"/bin/javac" \
               "$HOME/.laby/runtime/java-runtime-delta/bin/javac" \
               "$HOME/.labymod/runtime/java-runtime-delta/bin/javac" \
               "$HOME/.local/share/LabyMod/runtime/java-runtime-delta/bin/javac" \
               /usr/lib/jvm/*/bin/javac \
               /usr/lib64/jvm/*/bin/javac \
               /opt/*/bin/javac \
               /opt/jdk*/bin/javac; do
        if [ -x "$try" ] && [ "$(javac_major "$try")" -ge 21 ]; then echo "$try"; return 0; fi
    done
    echo ''
}

javac_bin="$(find_javac)"
if [ -z "$javac_bin" ]; then
    echo 'Kein JDK ab Version 21 gefunden (javac).' >&2
    echo 'Beispiele: sudo dnf install java-21-openjdk-devel | apt install openjdk-21-jdk | pacman -S jdk21-openjdk' >&2
    exit 1
fi
echo "Verwende JDK:       $(dirname "$javac_bin")"
jars_bin="$(dirname "$javac_bin")/jar"
if [ ! -x "$jars_bin" ] && command -v jar >/dev/null 2>&1; then jars_bin="$(command -v jar)"; fi
if [ ! -x "$jars_bin" ]; then
    echo "Zu diesem JDK fehlt das jar-Werkzeug: $jars_bin" >&2
    exit 1
fi

# ---- Launcher- und LabyMod-Ordner ----
LABY_ROOT="${LABY_ROOT:-$LABY_ROOT_ARG}"
DEFAULT_ROOTS=(
    "$HOME/.minecraft"
    "$HOME/.laby"
    "$HOME/.labymod"
    "$HOME/.local/share/LabyMod"
    "$HOME/.local/share/labymod"
    "$HOME/.config/LabyMod"
    "$HOME/.config/labymod"
    "$HOME/.local/share/PrismLauncher"
    "$HOME/.local/share/PolyMC"
    "$HOME/.local/share/PollyMC"
    "$HOME/.local/share/MultiMC"
    "$HOME/.local/share/multimc"
    "$HOME/.local/share/ATLauncher"
    "$HOME/.local/share/Technic"
    "$HOME/.local/share/modrinth"
    "$HOME/.local/share/Modrinth"
    "$HOME/.local/share/CurseForge"
    "$HOME/.config/CurseForge"
    "$HOME/.gdlauncher"
    "$HOME/MultiMC"
    "$HOME/PolyMC"
    "$HOME/PrismLauncher"
    "$HOME/ATLauncher"
    "$HOME/Technic"
)

# LabyMod-4-Client-JAR: 1) direkter Pfad, 2) Standard-LabyMod-Struktur,
# 3) Launcher-/Instanz-Scan, 4) globaler Fallback unter $HOME.
laby_mod_jar="${LABY_MOD_JAR:-}"
canonical_root=''
if [ -n "$laby_mod_jar" ] && [ ! -f "$laby_mod_jar" ]; then
    echo "LABY_MOD_JAR angegeben, aber fehlt: $laby_mod_jar" >&2
    exit 1
fi
if [ -z "$laby_mod_jar" ]; then
    for r in ${DEFAULT_ROOTS[@]+"${DEFAULT_ROOTS[@]}"} "$LABY_ROOT"; do
        if [ -n "$r" ] && [ -f "$r/libraries/net/labymod/LabyMod/4-production/LabyMod-4-production.jar" ]; then
            laby_mod_jar="$r/libraries/net/labymod/LabyMod/4-production/LabyMod-4-production.jar"
            canonical_root="$r"
            break
        fi
    done
fi
if [ -z "$laby_mod_jar" ]; then
    for r in ${DEFAULT_ROOTS[@]+"${DEFAULT_ROOTS[@]}"} "$LABY_ROOT"; do
        if [ -n "$r" ] && [ -d "$r" ]; then
            hits="$(find "$r" -maxdepth 6 -type f \( -name 'LabyMod-4-production.jar' -o -name 'LabyMod-4.jar' \) -print 2>/dev/null | sort)"
            if [ -n "$hits" ]; then
                laby_mod_jar="$(printf '%s\n' "$hits" | tail -1)"
                if [ "$mc_version" != 'x-none-x' ]; then
                    pref="$(printf '%s\n' "$hits" | grep -- ".*-$mc_version-" | tail -1 || true)"
                    if [ -n "$pref" ]; then laby_mod_jar="$pref"; fi
                fi
                break
            fi
        fi
    done
fi
if [ -z "$laby_mod_jar" ]; then
    echo "Suche LabyMod-4-JAR breit unter $HOME ... (kann einen Moment dauern)"
    hit="$(find "$HOME" -maxdepth 7 -type f \( -name 'LabyMod-4-production.jar' -o -name 'LabyMod-4.jar' \) -print 2>/dev/null | sort | tail -1)"
    if [ -n "$hit" ]; then laby_mod_jar="$hit"; fi
fi
if [ -z "$laby_mod_jar" ]; then
    echo 'LabyMod-4-Client-JAR (LabyMod-4-production.jar bzw. LabyMod-4.jar) nicht gefunden.' >&2
    echo 'Abhilfe: LABY_ROOT oder LABY_MOD_JAR setzen, falls LabyMod woanders liegt.' >&2
    exit 1
fi
if [ "$mc_version" != 'x-none-x' ]; then
    case "$laby_mod_jar" in
        *"-$mc_version-"*) : ;;
        *) echo "Hinweis: gefundene LabyMod-Instanz entspricht nicht Minecraft $mc_version." >&2 ;;
    esac
fi
echo "Verwende LabyMod-JAR: $laby_mod_jar"

# gson und sponge-mixin suchen: Bibliothekscaches neben der Client-JAR und
# darueberliegend (Prism haelt gson/mixin zentral unter $LauncherWurzel/libraries).
jar_dir="$(dirname "$laby_mod_jar")"
instance_dir="$(cd "$jar_dir/.." 2>/dev/null && pwd || true)"
launcher_root="$(cd "$instance_dir/.." 2>/dev/null && pwd || true)"
launcher_parent="$(cd "$launcher_root/.." 2>/dev/null && pwd || true)"
search_bases=()
add_base() {
    if [ -d "$1" ]; then search_bases+=("$1"); fi
}
add_base "$jar_dir"
if [ -n "$instance_dir" ]; then add_base "$instance_dir/libraries"; fi
if [ -n "$launcher_root" ]; then add_base "$launcher_root/libraries"; fi
if [ -n "$launcher_parent" ]; then add_base "$launcher_parent/libraries"; fi
if [ -n "$canonical_root" ]; then add_base "$canonical_root/libraries"; fi
add_base "$HOME/.minecraft/libraries"

laby_gson_jar="${LABY_GSON_JAR:-}"
laby_mixin_jar="${LABY_MIXIN_JAR:-}"
if [ -n "$laby_gson_jar" ] && [ ! -f "$laby_gson_jar" ]; then
    echo "LABY_GSON_JAR angegeben, aber fehlt: $laby_gson_jar" >&2
    exit 1
fi
if [ -n "$laby_mixin_jar" ] && [ ! -f "$laby_mixin_jar" ]; then
    echo "LABY_MIXIN_JAR angegeben, aber fehlt: $laby_mixin_jar" >&2
    exit 1
fi
if [ -z "$laby_gson_jar" ]; then
    # Erst den echten gson-Artefaktort (com/google/code/gson), sonst Namensstreuung
    # wie gson-io-*.jar vermeiden.
    for b in ${search_bases[@]+"${search_bases[@]}"}; do
        hit="$(find "$b" -maxdepth 8 -path '*com/google/code/gson/*' -name '*.jar' -print 2>/dev/null | sort -V | tail -1)"
        if [ -n "$hit" ]; then laby_gson_jar="$hit"; break; fi
    done
fi
if [ -z "$laby_gson_jar" ]; then
    for b in ${search_bases[@]+"${search_bases[@]}"}; do
        hit="$(find "$b" -maxdepth 8 -name 'gson-*.jar' ! -name 'gson-io-*' ! -name 'gson-extras-*' -print 2>/dev/null | sort -V | tail -1)"
        if [ -n "$hit" ]; then laby_gson_jar="$hit"; break; fi
    done
fi
if [ -z "$laby_mixin_jar" ]; then
    for b in ${search_bases[@]+"${search_bases[@]}"}; do
        hit="$(find "$b" -maxdepth 8 -path '*net/labymod/sponge-mixin*' -name '*.jar' -print 2>/dev/null | sort | tail -1)"
        if [ -n "$hit" ]; then laby_mixin_jar="$hit"; break; fi
    done
fi
if [ -z "$laby_mixin_jar" ]; then
    for b in ${search_bases[@]+"${search_bases[@]}"}; do
        hit="$(find "$b" -maxdepth 8 -path '*sponge-mixin*' -name '*.jar' -print 2>/dev/null | sort | tail -1)"
        if [ -n "$hit" ]; then laby_mixin_jar="$hit"; break; fi
    done
fi
if [ -z "$laby_gson_jar" ]; then
    hit="$(find "$HOME" -maxdepth 7 -path '*com/google/code/gson*' -name '*.jar' -print 2>/dev/null | sort -V | tail -1)"
    if [ -n "$hit" ]; then laby_gson_jar="$hit"; fi
fi
if [ -z "$laby_mixin_jar" ]; then
    hit="$(find "$HOME" -maxdepth 7 -path '*sponge-mixin*' -name '*.jar' -print 2>/dev/null | sort | tail -1)"
    if [ -n "$hit" ]; then laby_mixin_jar="$hit"; fi
fi
if [ -z "$laby_gson_jar" ] || [ -z "$laby_mixin_jar" ]; then
    echo 'gson bzw. sponge-mixin wurde nicht gefunden.' >&2
    echo 'Abhilfe: LABY_GSON_JAR bzw. LABY_MIXIN_JAR (oder LABY_ROOT) setzen.' >&2
    exit 1
fi
echo "Verwende gson:      $laby_gson_jar"
echo "Verwende mixin:     $laby_mixin_jar"

# ---- Addon-Ordner suchen (Spielordner der Launcher-Instanzen + direkte) ----
find_addon_folders() {
    local r pass hits
    for pass in 1 2; do
        for r in ${DEFAULT_ROOTS[@]+"${DEFAULT_ROOTS[@]}"} "$LABY_ROOT"; do
            if [ -n "$r" ] && [ -d "$r" ]; then
                hits="$(find "$r" -maxdepth 6 -type d -path '*labymod-neo/addons' -print 2>/dev/null | sort)"
                if [ -n "$hits" ]; then
                    if [ "$pass" = '1' ] && [ "$mc_version" != 'x-none-x' ]; then
                        printf '%s\n' "$hits" | grep -- ".*-$mc_version-.*" || true
                    elif [ "$pass" = '2' ] && [ "$mc_version" != 'x-none-x' ]; then
                        printf '%s\n' "$hits" | grep -v -- ".*-$mc_version-.*" || true
                    else
                        printf '%s\n' "$hits"
                    fi
                fi
            fi
        done
    done
    return 0
}

deploy_addon_jar() {
    local jar_path="$1" addons_target="$2"
    if [ ! -d "$addons_target" ]; then
        echo "Addon-Ordner nicht gefunden: $addons_target" >&2
        exit 1
    fi
    # Nur alte SkinCopy-/Mermil-JARs entfernen, fremde Addons bleiben unberuehrt.
    find "$addons_target" -maxdepth 1 -type f -name '*.jar' \
        \( -name '*SkinCopy*.jar' -o -name 'Mermil-Paradis-*.jar' \) -delete
    cp "$jar_path" "$addons_target/"
    echo "Eingesetzt: $addons_target"
}

if [ -z "$TARGET" ]; then
    echo ''
    echo 'Ziel fuer die JAR:'
    echo ' [1] Nur diesen Repository-Ordner (Standard)'
    echo ' [2] Zusaetzlich in den LabyMod-Addon-Ordner einsetzen'
    if [ -t 0 ]; then
        read -r -p 'Auswahl (1/2, Enter = 1): ' choice || choice=''
        if [ "$choice" = '2' ]; then TARGET='addons'; else TARGET='repo'; fi
    else
        TARGET='repo'
    fi
fi

# ---- Kompilieren und JAR bauen ----
mkdir -p "$build_dir"
classes_dir="$(mktemp -d "$build_dir/classes-XXXXXXXX")"
trap 'rm -rf "$classes_dir"' EXIT

find "$project_dir/src" -name '*.java' | sort > "$classes_dir/sources.txt"
"$javac_bin" --release 21 -encoding UTF-8 -proc:none \
    -classpath "$laby_mod_jar:$laby_gson_jar:$laby_mixin_jar" \
    -d "$classes_dir" "@$classes_dir/sources.txt"

cp -a "$project_dir/resources/." "$classes_dir/"

output_jar="$project_dir/Mermil-Paradis-$version-release.jar"
"$jars_bin" --create --file "$output_jar" -C "$classes_dir" .

echo "Erstellt: $output_jar"

if [ "$TARGET" = 'addons' ]; then
    addons_target=''
    if [ -n "$(printf '%s' "$ADDONS_DIR" | tr -d '[:space:]')" ]; then
        addons_target="$ADDONS_DIR"
    else
        candidates="$(find_addon_folders | head -1)"
        if [ -n "$candidates" ]; then
            echo "Addon-Ordner gefunden: $candidates"
            keep='j'
            if [ -t 0 ]; then
                read -r -p 'Diesen Ordner benutzen? (j/n): ' confirm || confirm=''
                if [ "$confirm" = 'n' ]; then keep='n'; fi
            fi
            if [ "$keep" = 'j' ]; then addons_target="$candidates"; fi
        fi
        if [ -z "$addons_target" ] && [ -t 0 ]; then
            read -r -p 'Pfad zum Addon-Ordner eingeben (.../labymod-neo/addons, leer = Abbrechen): ' manual || manual=''
            if [ -n "$manual" ]; then addons_target="$manual"; fi
        fi
    fi
    if [ -n "$addons_target" ]; then
        deploy_addon_jar "$output_jar" "$addons_target"
    else
        echo 'Kein Addon-Ordner angegeben. JAR bleibt im Repository-Ordner.'
    fi
fi
