# Mermil Paradis

Open-Source-Addon fuer **LabyMod 4 / Minecraft Java 1.21.11**.
Aktueller Quellstand: **0.4.2, experimentell**. Lizenz: [MIT](LICENSE).

![Mermil icon](resources/assets/skincopy/textures/mermil.png)

## Lesbarkeit in 0.4.2

Dashboard und Skin-Historie verwenden jetzt natuerlich hohe, scrollbar angeordnete
Eintraege anstelle starrer Rasterkarten. Beschreibungen umbrechen bei kleinen
Breiten. Schriftgroessen sind explizite Skalierungswerte; Button-Textwrapper
haben definierte Breite und Hoehe. Marke, Navigation und Suche haben eigene Zeilen.
Die neue Darstellung muss im laufenden Spiel geprueft werden.

## Zentrale Startseite in 0.4.2

F8 im Spiel, `/mp` oder `/mermil` oeffnen die zentrale Funktionsuebersicht.
Das Mausrad-Menue enthaelt `Mermil Zentrale` und behaelt den Spieler-Kontext.
Ein staendiger Startseiten-Button fuehrt aus allen Bereichen zur Uebersicht.
Die Oberflaeche verwendet groessere Schrift und dunklere Beschriftungen.
F8 wird nur ohne geoeffneten Bildschirm verarbeitet.

## Profilansicht in 0.4.2

Helle Profilansicht mit weichen Karten und violettem Akzent. Skin-Vorschau und Profilinformationen stehen in scrollbar angeordneten Abschnitten;
Texte umbrechen entsprechend der verfuegbaren Breite. Tabs trennen Uebersicht,
Namenshistorie und Skin-Sichtungen. Eigener Skin und Einstellungen sind separat.

Die aktuelle Skin-Vorschau nutzt LabyMods nativen PlayerModelWidget ohne Cosmetics
und kann zwischen Vorder- und Rueckseite wechseln. Das bindet die Darstellung an
die vorhandene LabyMod-Core-Version. Uebernahme verwendet den bestehenden
Account-/Sicherungsablauf. PNGs werden nach Klick unter
`mermil-paradis/skins/<UUID>.png` relativ zum Spiel-Arbeitsverzeichnis gespeichert.
Ein erneuter Download desselben Profils ersetzt diese Datei.

Die Skin-Daten kommen auf ausdrueckliche Profilsuche vom oeffentlichen
Mojang-Sessionserver (UUID, ohne Zugangsdaten). Weiterleitungen und zu grosse
Antworten werden abgewiesen. Heruntergeladene PNGs kommen ausschliesslich vom
Minecraft-Texturserver. Skin-History-Karten zeigen weiterhin nur Metadaten; die
Quelle liefert keine Bild-URLs. Es werden keine historischen Bilder erfunden.

Das bisherige Mermil-Logo bleibt vorlaeufig enthalten, weil der angehaengte
Roboterkopf in der Entwicklungsumgebung nicht heruntergeladen werden konnte.
Der Austausch gegen die bereitgestellte PNG ist noch offen.

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
`Mermil-Paradis-0.4.2-release.jar`. Installierte Client-Dateien werden nicht veraendert.
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
Versionen vor 0.4.2 haben den vorherigen Skin nicht gespeichert. Diese alten
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
Pruefung und den isolierten Skin-Hook wurden bestanden. Alle 32 Addon-Klassen
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
