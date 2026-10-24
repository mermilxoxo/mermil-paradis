# Mermil Paradis

Open-Source-Addon fuer **LabyMod 4 / Minecraft Java 1.21.11**.
Aktueller Quellstand: **0.3.1, experimentell**. Lizenz: [MIT](LICENSE).

![Mermil icon](resources/assets/skincopy/textures/mermil.png)

## Neue Oberflaeche in 0.3.1

Kompakter Kopfbereich, Suche nur in History/Profil und mehr Platz fuer Inhalte.
Namenseintraege stehen in Tabellenzeilen mit dem Datum rechts; Profilaktionen und
Einstellungen sind nach Aufgabe gruppiert. Einstellungen verwenden native
Ein-/Aus-Schalter. Lange Texte behalten ihre Schriftgroesse und zeigen den vollen
Inhalt beim Hover. Das Fenster folgt den Farben des LabyMod-Themes.

## Funktionen

- Eigenes Mermil-Fenster mit den Bereichen History, Profil, Skins und Einstellungen.
- Skin eines anvisierten Spielers als eigenen Minecraft-Account-Skin uebernehmen.
- Bisherigen Skin vor der Uebernahme sichern und spaeter wiederherstellen.
- Namenshistorien von Laby.net und RebelCore vergleichen; RebelCore-Skin-Metadaten anzeigen.
- Profil-Schnellaktionen: Name, UUID und NameMC-Link kopieren; Profilseiten im Browser oeffnen.
- Zehn Einstellungsschalter fuer Funktionen, Datenquellen und Darstellung.

Das Addon verwendet LabyMods Spieler-Interaktionsmenue mit der mittleren Maustaste.
Die Belegung kann von den LabyMod-Einstellungen abhaengen. Es braucht kein Forge
und keinen CustomSkinLoader.

## Befehle

| Befehl | Funktion |
| --- | --- |
| `/mp` | Mermil-Fenster oeffnen |
| `/mp lookup <Name oder UUID>` | History suchen |
| `/mp profile <Name oder UUID>` | Profil-Schnellaktionen |
| `/mp skins` | Skin-Sicherung und Wiederherstellung |
| `/mp restore` | Gesicherten vorherigen Skin wiederherstellen |
| `/mp settings` | Eigene Einstellungen |

`/mermilparadis` ist ein Alias fuer `/mp`.

## Bauen

Voraussetzungen auf Windows:

1. Eine vorhandene LabyMod-4-Installation mit dem Java-21-JDK und Client-Bibliotheken.
2. PowerShell und Zugriff auf die lokal installierten Bibliotheken.

Im Repository-Verzeichnis:

```powershell
powershell -ExecutionPolicy Bypass -File .\build.ps1
```

Die Ausfuehrungsoption gilt nur fuer diesen Prozess. `build.ps1` sucht die
LabyMod-Installation unter `%APPDATA%/LabyMod`, kopiert benoetigte Bibliotheken
in den ignorierten Ordner `build/compile-deps` und erzeugt
`Mermil-Paradis-0.3.1-release.jar`. Installierte Client-Dateien werden nicht veraendert.
Abhaengigkeiten werden weder heruntergeladen noch in diesem Repository mitgeliefert.
Dies ist kein Gradle-Projekt und kein unabhaengig startbares Java-Programm.

Details zu Tests und zum Pruefstand: [docs/TESTING.md](docs/TESTING.md).

## Installation

1. Minecraft und LabyMod vollstaendig beenden.
2. Im LabyMod Launcher den Spielordner der verwendeten Instanz oeffnen.
3. In `labymod-neo/addons` bisherige SkinCopy-/Mermil-Paradis-JARs entfernen.
4. Nur die neu gebaute Addon-JAR einsetzen und mit Minecraft **1.21.11** starten.
5. Zuerst `/mp settings`, dann `/mp lookup <Spielername>` testen.

Ein kompletter Neustart ist wegen des Minecraft-Mixins erforderlich. Der interne
Namespace `skincopy` bleibt fuer bestehende Einstellungen und den Hook erhalten.

## Skin-Uebernahme und Sicherung

Die Uebernahme ersetzt den **echten Account-Skin** und benoetigt eine gueltige
Minecraft-Java-Anmeldung. Das Addon verwendet LabyMods `MinecraftServices`;
eigener Code liest und speichert keine Zugangstokens. Die PNG wird nur von
`textures.minecraft.net` geladen und mit Groessen-/Formatlimits verarbeitet.

Bei aktivierter Sicherung wird die aktuelle oeffentliche Textur-URL mit Armbreite,
Zeitpunkt und Account-UUID vor dem Upload dauerhaft in der lokalen Addon-Konfiguration
gespeichert. Ein Journal unterscheidet bestaetigte und unklare Upload-Ergebnisse.
Sicherungen sind nach Account getrennt, mit einer Grenze von 32 Accounts.
Beim Wiederherstellen wird der gerade aktive Skin zur Rueckkehr gesichert.

Eine Sicherung entsteht erst vor einer **neuen Uebernahme mit aktivierter Sicherung**.
Versionen vor 0.3.1 haben den vorherigen Skin nicht gespeichert. Diese alten
Aenderungen lassen sich nicht rueckwirkend wiederherstellen. Kann kein aktiver
Account-Skin gesichert werden, etwa ein Standard-Skin ohne passende Textur,
wird die Uebernahme bei aktivierter Sicherung abgebrochen.

Die lokale Live-Anzeige nutzt einen eigenen Hook fuer `AbstractClientPlayer.getSkin()`
und gilt nur fuer den eigenen Spieler mit passender UUID. Capes und Elytra bleiben
erhalten. Andere Spieler sehen Aenderungen eventuell erst nach erneutem Beitritt.
Die tatsaechliche Darstellung muss im Spiel geprueft werden.

## Quellen und Datenschutz

| Quelle | Zugriff |
| --- | --- |
| Laby.net | Namenshistorie ueber LabyMods SDK; auch Namens-/UUID-Aufloesung |
| RebelCore | Oeffentliche Profil-API fuer Namenshistorie und Skin-Metadaten |
| NameMC / Crafty | Profil-Links, die erst nach Klick im Browser geoeffnet werden |

Es gibt zwei direkte History-Quellen; NameMC und Crafty werden nicht gescrapt.
History-Abfragen senden die gesuchte UUID an den jeweiligen Anbieter. Bei einer
Namenssuche geht der Name zur Aufloesung an Laby. Quellen lassen sich abschalten;
die separate Namensaufloesung verwendet weiterhin Laby. Eine direkte UUID-Abfrage
ist ebenfalls moeglich. Auch optionale Aufloesung des aktuellen Profilnamens erfolgt ueber Laby.

Historien koennen unvollstaendig, verborgen oder veraltet sein. Laby-
Aenderungsdaten und RebelCore-`last_used` werden getrennt bezeichnet. Eine
Sichtung belegt kein exaktes Namensaenderungsdatum. Ausfall, deaktivierte Quelle
und fehlender Eintrag werden im Vergleich unterschieden.

History-Daten bleiben im Arbeitsspeicher: fuenf Minuten Cache, maximal 64
Quellen-/UUID-Ergebnisse und 200 angezeigte Eintraege je Kategorie. Die
RebelCore-Antwort ist auf 1 MiB begrenzt. Unbekannte Textur-URLs werden nicht geladen.
Es gibt keine Hintergrundsuche nach Spielern und keine eigene Telemetrie.

**Keine lokalen Konfigurationen, Skin-Sicherungen, Spielprotokolle, Account-Dateien
oder Zugangsdaten committen.** Hinweise zum Melden von Problemen stehen in
[SECURITY.md](SECURITY.md) und [CONTRIBUTING.md](CONTRIBUTING.md).

## Pruefstand

Offline-Tests fuer History-Parsing, Vergleich, Skin-Sicherungsjournal, PNG-/URL-
Pruefung und den isolierten Skin-Hook wurden bestanden. Alle 31 Addon-Klassen
wurden gegen die verwendete LabyMod-API geprueft. Die Activity-Metadaten werden
explizit registriert, da dieser Build keinen LabyMod-Annotationsprozessor verwendet.

Der Pruefstand ist keine Garantie fuer die Darstellung oder API-Verfuegbarkeit im
laufenden Spiel. Siehe [docs/TESTING.md](docs/TESTING.md) fuer offene Tests und eine
bekannte Einschraenkung der urspruenglichen Compiler-Umgebung.

## Lizenz und Abhaengigkeiten

Eigener Quellcode und eigene Assets stehen unter [MIT](LICENSE).
Fremde Bibliotheken, Minecraft-Texturen und Laufzeit-Daten werden dadurch nicht
neu lizenziert; siehe [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
Dieses Projekt ist kein offizielles Produkt von Mojang, Microsoft oder LabyMod.
