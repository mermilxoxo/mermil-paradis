package de.skincopy.lookup;

import de.skincopy.SkinCopyAddon;
import de.skincopy.SkinCopyConfig;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.labymod.api.LabyAPI;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.component.format.TextColor;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.configuration.loader.property.ConfigProperty;
import net.labymod.api.models.OperatingSystem;
import static de.skincopy.lookup.HistoryData.*;

/** One branded panel: history comparison, profile actions, restore and settings. */
@AutoActivity
public final class HistoryActivity extends SimpleActivity {
    private final SkinCopyAddon addon;
    private final LabyAPI api;
    private final HistoryService service;
    private final UUID initialUuid;
    private final Map<Source, Result> results = new EnumMap<>(Source.class);
    private String input;
    private String tab;
    private UUID selectedUuid;
    private String selectedName;
    private String status = "Spielername oder UUID eingeben.";
    private int generation;
    private long nextSearch;
    private boolean started;
    private boolean historyRequested;
    private boolean observedBusy;
    private ButtonWidget searchButton;
    private ComponentWidget statusWidget;

    public HistoryActivity(SkinCopyAddon addon, HistoryService service, String input, UUID initialUuid, String tab) {
        this.addon = addon;
        this.api = addon.labyAPI();
        this.service = service;
        this.input = input == null ? "" : input;
        this.initialUuid = initialUuid;
        this.tab = tab;
    }
    private SkinCopyConfig config() { return addon.configuration(); }
    private static boolean on(ConfigProperty<Boolean> property) { return Boolean.TRUE.equals(property.get()); }

    // Activity.loadMeta is intentionally inherited. ParadisLinks registers all required styles.
    @Override
    public void initialize(Parent parent) {
        super.initialize(parent);
        addText("MP", "mermil-monogram", NamedTextColor.WHITE);
        addText("MERMIL", "mermil-title", NamedTextColor.LIGHT_PURPLE);
        addText("PARADIS", "mermil-subtitle", NamedTextColor.GRAY);
        addText("0.3.1", "mermil-version", NamedTextColor.GRAY);
        nav("History", "history", "nav-one");
        nav("Profil", "profile", "nav-two");
        nav("Skins", "skins", "nav-three");
        nav("Einstellungen", "settings", "nav-four");
        boolean playerTab = tab.equals("history") || tab.equals("profile");
        searchButton = null;
        if (playerTab) {
        TextFieldWidget field = new TextFieldWidget();
        field.maximalLength(36);
        field.placeholder(Component.text("Spielername / UUID"));
        field.setText(input);
        field.updateListener(value -> input = value);
        field.submitHandler(this::search);
        field.addId("mermil-input");
        document().addChild(field);
        searchButton = ButtonWidget.text("Suchen", () -> search(field.getText()));
        searchButton.addId("mermil-search");
        searchButton.setEnabled(canSearch());
        document().addChild(searchButton);
        }
        statusWidget = addText(status, "mermil-status", NamedTextColor.WHITE);
        String identity = selectedUuid == null ? "Noch kein Spieler ausgewaehlt."
                : (selectedName == null ? "Spieler" : selectedName) + "  /  " + selectedUuid;
        if (playerTab) addText(selectedUuid == null ? "Suche einen Spieler, um loszulegen."
                : selectedName == null ? selectedUuid.toString() : selectedName, "mermil-identity", NamedTextColor.WHITE);

        VerticalListWidget<Widget> list = new VerticalListWidget<>();
        list.addId("mermil-list");
        list.selectable().set(false);
        list.spaceBetweenEntries().set(on(config().compactHistory()) ? 1f : 4f);
        switch (tab) {
            case "profile" -> profile(list);
            case "skins" -> skins(list);
            case "settings" -> settings(list);
            default -> history(list);
        }
        ScrollWidget scroll = new ScrollWidget(list);
        scroll.addId("mermil-scroll", playerTab ? "mermil-player-content" : "mermil-tools-content");
        document().addChild(scroll);

        if (!started) {
            started = true;
            observedBusy = addon.busy();
            api.minecraft().executeNextTick(() -> {
                if (initialUuid != null) {
                    selectedName = validName(input) ? input : null;
                    select(initialUuid, ++generation);
                } else if (!input.isBlank()) search(input);
            });
        }
    }
    private ComponentWidget addText(String text, String id, TextColor color) {
        ComponentWidget widget = ComponentWidget.text(text, color);
        widget.addId(id);
        widget.clippingTextTooltip().set(true);
        document().addChild(widget);
        return widget;
    }
    private void nav(String label, String target, String id) {
        ButtonWidget button = ButtonWidget.text(label, () -> {
            tab = target;
            refresh();
            if (tab.equals("history") && selectedUuid != null && !historyRequested) loadHistory(generation);
        });
        button.addId("mermil-nav", id);
        if (tab.equals(target)) button.addId("mermil-nav-active");
        document().addChild(button);
    }
    private static void line(VerticalListWidget<Widget> list, String text, TextColor color) {
        ComponentWidget widget = ComponentWidget.text(text, color);
        widget.clippingTextTooltip().set(true);
        widget.addId("mermil-line");
        list.addChild(widget);
    }
    private static void title(VerticalListWidget<Widget> list, String text) {
        ComponentWidget widget = ComponentWidget.text(text, NamedTextColor.LIGHT_PURPLE);
        widget.addId("mermil-section");
        widget.clippingTextTooltip().set(true);
        list.addChild(widget);
    }
    private static ButtonWidget button(VerticalListWidget<Widget> list, String label, Runnable click, boolean enabled) {
        ButtonWidget widget = ButtonWidget.text(label, click::run);
        widget.addId("mermil-action");
        widget.setEnabled(enabled);
        list.addChild(widget);
        return widget;
    }
    private void history(VerticalListWidget<Widget> list) {
        title(list, "Namenshistorie");
        if (!on(config().lookupEnabled())) {
            line(list, "History ist deaktiviert. In Einstellungen wieder einschalten.", NamedTextColor.GRAY); return;
        }
        if (selectedUuid == null) {
            line(list, "Suche einen Namen / eine UUID oder nutze das Mausrad-Spielermenue.", NamedTextColor.WHITE);
            line(list, "Befehl: /mp lookup Spielername", NamedTextColor.GRAY); return;
        }
        button(list, on(config().compareHistory()) ? "Ansicht: VERGLEICH - zu Quellen wechseln"
                : "Ansicht: QUELLEN - zum Vergleich wechseln", () -> {
            config().compareHistory().set(!on(config().compareHistory())); addon.saveSettings(); refresh();
        }, true);
        for (Source source : Source.values()) {
            boolean enabled = sourceEnabled(source);
            Result result = results.get(source);
            String summary = !enabled ? "deaktiviert" : result == null ? "Abfrage laeuft ..."
                    : result.success() ? result.names().size() + " Namen | " + result.message() : result.message();
            line(list, source.title + ": " + summary,
                    result != null && !result.success() && enabled ? NamedTextColor.RED : NamedTextColor.GRAY);
        }
        if (on(config().compareHistory())) {
            List<HistoryComparison.Row> rows = HistoryComparison.compare(results);
            long both = rows.stream().filter(HistoryComparison.Row::both).count();
            title(list, rows.size() + " Namen  /  " + both + " in beiden Quellen");
            for (HistoryComparison.Row row : rows) {
                String laby = row.laby().isEmpty() ? HistoryComparison.absence(results.get(Source.LABY), sourceEnabled(Source.LABY))
                        : "gefunden (" + row.laby().size() + ")";
                String rebel = row.rebel().isEmpty() ? HistoryComparison.absence(results.get(Source.REBEL), sourceEnabled(Source.REBEL))
                        : "gefunden (" + row.rebel().size() + ")";
                title(list, row.name());
                line(list, "Laby: " + laby + "    /    Rebel: " + rebel,
                        row.both() ? NamedTextColor.GREEN : NamedTextColor.WHITE);
                if (!on(config().compactHistory())) {
                    for (Name name : row.laby()) line(list, "  Laby - geaendert: "
                            + (name.accurate() ? "" : "~ ") + name.date(), NamedTextColor.GRAY);
                    for (Name name : row.rebel()) line(list, "  Rebel - zuletzt genutzt: " + name.date(), NamedTextColor.GRAY);
                }
            }
            if (rows.isEmpty()) line(list, "Bisher keine Namenseintraege zum Vergleichen.", NamedTextColor.GRAY);
        } else {
            for (Source source : Source.values()) {
                Result result = results.get(source);
                if (!sourceEnabled(source) || result == null || !result.success()) continue;
                title(list, source.title.toUpperCase(java.util.Locale.ROOT));
                for (Name name : result.names()) line(list, name.username() + " | "
                        + (source == Source.LABY ? "geaendert: " + (name.accurate() ? "" : "~ ") : "zuletzt genutzt: ")
                        + name.date(), NamedTextColor.WHITE);
            }
        }
        Result rebel = results.get(Source.REBEL);
        if (sourceEnabled(Source.REBEL) && rebel != null && rebel.success()) {
            title(list, "SKIN-SICHTUNGEN: " + rebel.skins().size());
            for (Skin skin : rebel.skins()) line(list, (skin.slim() == null ? "Modell unbekannt" : skin.slim() ? "Slim" : "Classic")
                    + " | " + (skin.active() == null ? "Status unbekannt" : skin.active() ? "aktiv laut Quelle" : "archiviert")
                    + " | " + skin.firstSeen() + " bis " + skin.lastSeen(), NamedTextColor.GRAY);
        }
        line(list, "Quellen koennen Luecken haben. Sichtungsdatum ist kein exaktes Aenderungsdatum.", NamedTextColor.GRAY);
        line(list, "Abgleich ohne Gross-/Kleinschreibung. Profil-Links und Kopieren: Bereich Profil.", NamedTextColor.GRAY);
    }
    private void profile(VerticalListWidget<Widget> list) {
        title(list, "Profil");
        if (!on(config().quickActionsEnabled())) {
            line(list, "Profil-Schnellaktionen sind in Einstellungen deaktiviert.", NamedTextColor.GRAY); return;
        }
        if (selectedUuid == null) {
            line(list, "Zuerst einen Spieler suchen oder Mausrad > Mermil Profil auswaehlen.", NamedTextColor.GRAY); return;
        }
        UUID id = selectedUuid;
        String name = selectedName;
        String namemc = "https://namemc.com/profile/" + id;
        line(list, "UUID: " + id, NamedTextColor.GRAY);
        title(list, "Kopieren");
        button(list, "Spielername kopieren", () -> copy(name), name != null);
        button(list, "UUID kopieren", () -> copy(id.toString()), true);
        button(list, "NameMC-Profil-Link kopieren", () -> copy(namemc), true);
        title(list, "Profilseiten im Browser");
        button(list, "NameMC im Browser oeffnen", () -> open(namemc), true);
        button(list, "Laby.net im Browser oeffnen", () -> open("https://laby.net/@" + name), name != null);
        button(list, "Crafty im Browser oeffnen", () -> open("https://crafty.gg/@" + name), name != null);
        button(list, "RebelCore Profil-JSON oeffnen", () -> open(RebelCore.profileUrl(id)), true);
        title(list, "Mehr zu diesem Spieler");
        button(list, "History dieses Spielers anzeigen", () -> {
            tab = "history"; refresh(); if (!historyRequested) loadHistory(generation);
        }, on(config().lookupEnabled()));
    }
    private void copy(String value) {
        if (value != null) { api.minecraft().setClipboard(value); setStatus("In die Zwischenablage kopiert."); }
    }
    private void open(String url) {
        try { OperatingSystem.getPlatform().openUrl(url); setStatus("Profil im Browser geoeffnet."); }
        catch (RuntimeException ignored) { setStatus("Browser konnte nicht geoeffnet werden."); }
    }
    private void skins(VerticalListWidget<Widget> list) {
        title(list, "Skin wiederherstellen");
        line(list, addon.backupDescription(), NamedTextColor.WHITE);
        button(list, addon.busy() ? "Skin-Aenderung laeuft ..." : "Vorherigen Skin wiederherstellen",
                addon::restorePreviousSkin, addon.enabledNow() && !addon.busy() && addon.previousSkin() != null);
        line(list, "Ersetzt deinen aktuellen Account-Skin. Der aktuelle Skin wird dabei zur Rueckkehr gesichert.", NamedTextColor.GRAY);
        line(list, "Sicherungen sind nach Account-UUID getrennt und bleiben nach einem Neustart erhalten.", NamedTextColor.GRAY);
        line(list, "Noch keine Sicherung? Erst eine neue Skin-Uebernahme mit aktivierter Sicherung ausfuehren.", NamedTextColor.GRAY);
        line(list, "Skin-Uebernahme: Spieler anvisieren > Mausrad > Skin uebernehmen.", NamedTextColor.GRAY);
        line(list, "Fruehere Versionen ohne Sicherung koennen keinen alten Skin nachtraeglich rekonstruieren.", NamedTextColor.GRAY);
        toggle(list, "Automatische Sicherung vor Uebernahme", config().backupEnabled());
        toggle(list, "Lokaler Live-Skin-Wechsel", config().liveSkinEnabled());
    }
    private void settings(VerticalListWidget<Widget> list) {
        title(list, "Einstellungen");
        line(list, "Aenderungen werden sofort angewendet und gespeichert.", NamedTextColor.GRAY);
        title(list, "Spielermenue");
        toggle(list, "Skin-Uebernahme im Mausrad-Menue", config().skinCopyEnabled());
        toggle(list, "User-History", config().lookupEnabled());
        toggle(list, "Profil-Schnellaktionen im Mausrad-Menue", config().quickActionsEnabled());
        title(list, "Skin und Sicherung");
        toggle(list, "Automatische Skin-Sicherung", config().backupEnabled());
        toggle(list, "Lokaler Live-Skin-Wechsel", config().liveSkinEnabled());
        title(list, "Datenquellen");
        toggle(list, "History-Quelle: Laby.net", config().labySourceEnabled());
        toggle(list, "History-Quelle: RebelCore", config().rebelSourceEnabled());
        title(list, "Darstellung und Meldungen");
        toggle(list, "History-Vergleich als Standard", config().compareHistory());
        toggle(list, "Kompakte History (ohne Datumsdetails)", config().compactHistory());
        toggle(list, "Erfolgsmeldungen im Chat", config().chatFeedback());
        title(list, "Verwaltung");
        button(list, "History-Cache leeren", () -> {
            service.clearCache(); results.clear(); historyRequested = false; generation++;
            setStatus("History-Cache geleert. Naechste Suche fragt die Quellen neu ab.");
        }, true);
        line(list, "Befehle: /mp | /mp settings | /mp skins | /mp restore", NamedTextColor.GRAY);
        line(list, "LabyMod-Addon-Einstellungen bieten dieselben Schalter.", NamedTextColor.GRAY);
    }
    private void toggle(VerticalListWidget<Widget> list, String label, ConfigProperty<Boolean> property) {
        net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget row =
                new net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget();
        row.addId("mermil-setting");
        ComponentWidget name = ComponentWidget.text(label, NamedTextColor.WHITE);
        name.addId("mermil-setting-label");
        name.clippingTextTooltip().set(true);
        row.addFlexibleContent(name);
        var control = net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.text("AN", "AUS", value -> {
            property.set(value); addon.saveSettings();
            generation++; results.clear(); historyRequested = false;
            api.minecraft().executeNextTick(this::refresh);
        });
        control.setValue(on(property));
        control.addId("mermil-switch");
        row.addContent(control);
        list.addChild(row);
    }

    private boolean canSearch() { return addon.enabledNow() && System.currentTimeMillis() >= nextSearch; }
    private void search(String raw) {
        if (!canSearch()) return;
        input = raw.trim();
        UUID parsed = null;
        try { parsed = uuid(input); } catch (IllegalArgumentException ignored) { }
        if (parsed == null && !validName(input)) { setStatus("Bitte einen Minecraft-Namen oder eine UUID eingeben."); return; }
        nextSearch = System.currentTimeMillis() + 2000;
        int request = ++generation;
        selectedUuid = null;
        selectedName = parsed == null ? input : null;
        results.clear(); historyRequested = false;
        if (parsed != null) { select(parsed, request); return; }
        status = "Suche UUID fuer " + input + " ..."; refresh();
        service.resolve(input, uuid -> {
            if (request == generation && addon.enabledNow()) select(uuid, request);
        }, error -> { if (request == generation) setStatus(error); });
    }
    private void select(UUID uuid, int request) {
        if (!addon.enabledNow() || request != generation) return;
        selectedUuid = uuid;
        results.clear(); historyRequested = false;
        status = "Profil geladen. " + (selectedName == null ? "Aktueller Name wird gesucht ..." : selectedName);
        refresh();
        // History calls are independent of optional current-name resolution.
        try {
            api.labyNetController().loadNameByUniqueId(uuid, response -> api.minecraft().executeNextTick(() -> {
                if (request == generation && !response.hasException() && response.isPresent() && validName(response.get())) {
                    selectedName = response.get(); status = "Profil: " + selectedName; refresh();
                }
            }));
        } catch (RuntimeException ignored) { }
        if (tab.equals("history")) loadHistory(request);
    }
    private boolean sourceEnabled(Source source) {
        return on(source == Source.LABY ? config().labySourceEnabled() : config().rebelSourceEnabled());
    }
    private void loadHistory(int request) {
        if (!addon.enabledNow() || !on(config().lookupEnabled()) || selectedUuid == null) return;
        historyRequested = true;
        service.lookup(selectedUuid, sourceEnabled(Source.LABY), sourceEnabled(Source.REBEL), result -> {
            if (request != generation || !addon.enabledNow() || !on(config().lookupEnabled())) return;
            results.put(result.source(), result); refresh();
        });
    }
    private void setStatus(String text) { status = text; if (statusWidget != null) statusWidget.setText(text); }
    private void refresh() { if (isOpen()) reload(); }
    @Override public void tick() {
        super.tick();
        if (searchButton != null) searchButton.setEnabled(canSearch());
        if (observedBusy != addon.busy()) { observedBusy = addon.busy(); if (tab.equals("skins")) refresh(); }
    }
}
