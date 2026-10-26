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
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.gui.screen.Parent;
import net.labymod.api.client.gui.screen.activity.AutoActivity;
import net.labymod.api.client.gui.screen.activity.types.SimpleActivity;
import net.labymod.api.client.gui.screen.widget.Widget;
import net.labymod.api.client.gui.screen.widget.widgets.ComponentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.ButtonWidget;
import net.labymod.api.client.gui.screen.widget.widgets.input.TextFieldWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.FlexibleContentWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.ScrollWidget;
import net.labymod.api.client.gui.screen.widget.widgets.layout.list.VerticalListWidget;
import net.labymod.api.client.gui.screen.widget.widgets.renderer.IconWidget;
import net.labymod.api.client.resources.ResourceLocation;
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
    private de.skincopy.SkinData profileSkin;
    private String previewStatus = "Noch kein Spieler ausgewaehlt.";
    private boolean backView;
    private int previewRequest;
    private boolean downloadBusy;
    private boolean narrow;
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
        // Echtes Logo-Asset statt Text-Monogramm: IconWidget laedt die eigene Textur direkt.
        IconWidget brand = new IconWidget(
                Icon.texture(addon.resource("textures/mermil.png")));
        brand.addId("mermil-icon");
        document().addChild(brand);
        addText("MERMIL", "mermil-title", NamedTextColor.LIGHT_PURPLE);
        addText("PARADIS", "mermil-subtitle", NamedTextColor.GRAY);
        boolean playerTab = tab.equals("profile") || tab.equals("history") || tab.equals("skinHistory");
        narrow = api.minecraft().minecraftWindow().getScaledWidth() < 560;
        headerAction("Startseite", "home", "mermil-own");
        headerAction("Einstellungen", "settings", "mermil-options");
        searchButton = null;
        if (playerTab) {
            TextFieldWidget field = new TextFieldWidget();
            field.maximalLength(36);
            field.placeholder(Component.text("Spielername oder UUID suchen"));
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
        VerticalListWidget<Widget> list = stack("mermil-list");
        statusWidget = ComponentWidget.text(status);
        statusWidget.addId("mermil-line");
        statusWidget.allowColors().set(false);
        statusWidget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        list.addChild(statusWidget);
        if (playerTab) {
            VerticalListWidget<Widget> card = stack("mermil-preview-card");
            card.addId("mermil-stacked");
            title(list, selectedUuid == null ? "Spieler suchen" : selectedName == null ? "Spielerprofil" : selectedName);
            if (selectedUuid != null) line(list, "UUID: " + selectedUuid, NamedTextColor.BLACK);
            preview(card);
            VerticalListWidget<Widget> content = stack("mermil-details-card");
            profileTabs(content);
            switch (tab) {
                case "history" -> history(content);
                case "skinHistory" -> skinHistory(content);
                default -> profile(content);
            }
            list.addChild(content);
            list.addChild(card);
        } else if (tab.equals("home")) dashboard(list);
        else if (tab.equals("settings")) settings(list); else skins(list);
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
    /** NameMC-style header: the player name is the heading, the UUID sits beneath it. */
    private void identity() {
        if (selectedUuid == null) {
            addText("Kein Spieler ausgewaehlt.", "mermil-identity", NamedTextColor.GRAY);
            return;
        }
        addText(selectedName == null ? "Name unbekannt" : selectedName,
                "mermil-identity", NamedTextColor.WHITE);
        addComponent(Component.text("UUID   ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(selectedUuid.toString(), NamedTextColor.GRAY)),
                "mermil-identity-sub");
    }
    private ComponentWidget addText(String text, String id, TextColor color) {
        ComponentWidget widget = ComponentWidget.text(text, color);
        widget.addId(id);
        widget.clippingTextTooltip().set(true);
        widget.scaleToFit().set(false);
        widget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        widget.allowColors().set(false);
        document().addChild(widget);
        return widget;
    }
    private void addComponent(Component component, String id) {
        ComponentWidget widget = ComponentWidget.component(component);
        widget.addId(id);
        widget.clippingTextTooltip().set(true);
        widget.scaleToFit().set(false);
        widget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        widget.allowColors().set(false);
        document().addChild(widget);
    }
    /** An independent scrolling card; short screens keep every action reachable. */
    private static VerticalListWidget<Widget> stack(String id) {
        VerticalListWidget<Widget> list = new VerticalListWidget<>();
        list.addId(id);
        list.selectable().set(false);
        list.squeezeHeight().set(false);
        list.spaceBetweenEntries().set(4f);
        return list;
    }
    private void headerAction(String label, String target, String id) {
        ButtonWidget button = ButtonWidget.text(label, () -> switchTab(target));
        button.addId("mermil-header-action", id);
        document().addChild(button);
    }
    private void switchTab(String target) {
        tab = target; refresh();
        if ((tab.equals("history") || tab.equals("skinHistory")) && selectedUuid != null && !historyRequested)
            loadHistory(generation);
    }
    private void profileTabs(VerticalListWidget<Widget> list) {
        FlexibleContentWidget tabs = new FlexibleContentWidget();
        tabs.addId("mermil-tabs");
        String[] labels = {"Uebersicht", "Namen", "Skins"};
        String[] targets = {"profile", "history", "skinHistory"};
        for (int i = 0; i < labels.length; i++) {
            String target = targets[i];
            ButtonWidget button = ButtonWidget.text(labels[i], () -> switchTab(target));
            button.addId("mermil-tab");
            if (tab.equals(target)) button.addId("mermil-tab-active");
            tabs.addFlexibleContent(button);
        }
        list.addChild(tabs);
    }
    /** Central launcher: every feature is reachable without first selecting a player. */
    private void dashboard(VerticalListWidget<Widget> list) {
        title(list, "Alles an einem Ort");
        line(list, "Direktzugriff: F8 im Spiel oder /mp im Chat", NamedTextColor.BLACK);
        dashboardTile(list, "Spielerprofil", "Profil suchen, Links oeffnen und Daten kopieren", "profile");
        dashboardTile(list, "Namenshistorie", "Fruehere Namen aus mehreren Quellen vergleichen", "history");
        dashboardTile(list, "Skin-Historie", "Oeffentliche archivierte Skin-Sichtungen", "skinHistory");
        dashboardTile(list, "Skin uebernehmen", "Profil suchen und aktuellen Skin anwenden", "profile");
        dashboardTile(list, "Mein Skin", "Gesicherten Skin wiederherstellen", "skins");
        dashboardTile(list, "Einstellungen", "Funktionen und Datenquellen verwalten", "settings");
        title(list, "Aktueller Stand");
        line(list, selectedUuid == null ? "Kein Spieler ausgewaehlt" : "Spieler: " + (selectedName == null ? selectedUuid : selectedName), NamedTextColor.BLACK);
        line(list, addon.busy() ? "Skin-Aenderung laeuft" : "Bereit", NamedTextColor.BLACK);
        line(list, addon.backupDescription(), NamedTextColor.BLACK);
    }
    private void dashboardTile(VerticalListWidget<Widget> list,
                               String label, String description, String target) {
        button(list, label, () -> switchTab(target), true).addId("mermil-dashboard-open");
        detail(list, description, NamedTextColor.DARK_GRAY);
    }
    private void preview(VerticalListWidget<Widget> list) {
        title(list, "Aktueller Skin");
        if (selectedUuid != null && profileSkin != null) {
            var model = new net.labymod.core.client.gui.screen.widget.widgets.customization.PlayerModelWidget();
            model.addId("mermil-skin-model");
            model.setRenderCosmetics(false);
            model.setScrolling(false);
            model.updateSkinTextureFrom(selectedUuid);
            model.turnTo(backView ? 180f : 0f, 0);
            list.addChild(model);
            button(list, backView ? "Vorderseite zeigen" : "Rueckseite zeigen", () -> {
                backView = !backView; refresh();
            }, true);
            line(list, profileSkin.slim() ? "Slim / schmale Arme" : "Classic / breite Arme", NamedTextColor.DARK_GRAY);
        }
        line(list, previewStatus, NamedTextColor.DARK_GRAY);
        de.skincopy.SkinData skin = profileSkin;
        button(list, addon.busy() ? "Uebernahme laeuft ..." : "Skin uebernehmen",
                () -> addon.applyProfileSkin(skin), skin != null && addon.enabledNow()
                        && on(config().skinCopyEnabled()) && !addon.busy()).addId("mermil-primary");
        UUID id = selectedUuid;
        button(list, downloadBusy ? "Wird gespeichert ..." : "PNG herunterladen",
                () -> download(skin, id), skin != null && id != null && !downloadBusy);
        if (skin != null) button(list, "Textur-Link kopieren", () -> copy(skin.url()), true);
    }
    /** Only a validated Minecraft PNG is written; filenames contain the UUID, never service text. */
    private void download(de.skincopy.SkinData skin, UUID id) {
        if (skin == null || id == null || downloadBusy) return;
        downloadBusy = true; refresh();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            String message;
            try {
                byte[] png = de.skincopy.SkinDownload.download(skin.url());
                var directory = java.nio.file.Path.of("mermil-paradis", "skins");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.write(directory.resolve(id + ".png"), png);
                message = "PNG gespeichert: mermil-paradis/skins/" + id + ".png";
            } catch (Exception ignored) { message = "PNG konnte nicht gespeichert werden."; }
            String result = message;
            api.minecraft().executeNextTick(() -> { downloadBusy = false; status = result; refresh(); });
        });
    }
    private void skinHistory(VerticalListWidget<Widget> list) {
        title(list, "Skin-Historie");
        if (!on(config().lookupEnabled())) { line(list, "History ist deaktiviert.", NamedTextColor.GRAY); return; }
        if (selectedUuid == null) { line(list, "Zuerst einen Spieler suchen.", NamedTextColor.GRAY); return; }
        sourceRow(list, Source.REBEL);
        Result result = results.get(Source.REBEL);
        if (!sourceEnabled(Source.REBEL) || result == null || !result.success()) return;
        if (result.skins().isEmpty()) { line(list, "Keine oeffentlichen Skin-Sichtungen.", NamedTextColor.GRAY); return; }
        line(list, "Diese Quelle liefert Metadaten ohne Skin-Bilder.", NamedTextColor.DARK_GRAY);
        for (Skin skin : result.skins()) {
            VerticalListWidget<Widget> card = stack("mermil-history-card");
            title(card, skin.slim() == null ? "Modell unbekannt" : skin.slim() ? "Slim" : "Classic");
            line(card, "Vorschau nicht verfuegbar", NamedTextColor.GRAY);
            detail(card, "Von: " + skin.firstSeen(), NamedTextColor.DARK_GRAY);
            detail(card, "Bis: " + skin.lastSeen(), NamedTextColor.DARK_GRAY);
            detail(card, skin.active() == null ? "Status unbekannt" : skin.active() ? "Aktiv laut Quelle" : "Archiviert", NamedTextColor.DARK_GRAY);
            list.addChild(card);
        }
    }
    private static void line(VerticalListWidget<Widget> list, String text, TextColor color) {
        ComponentWidget widget = ComponentWidget.text(text, color);
        widget.clippingTextTooltip().set(true);
        widget.scaleToFit().set(false);
        widget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        widget.allowColors().set(false);
        widget.addId("mermil-line");
        list.addChild(widget);
    }
    /** Muted small-caps section label, like a name-database subsection heading. */
    private static void title(VerticalListWidget<Widget> list, String text) {
        ComponentWidget widget = ComponentWidget.text(text.toUpperCase(java.util.Locale.ROOT),
                NamedTextColor.GRAY);
        widget.addId("mermil-section");
        widget.clippingTextTooltip().set(true);
        widget.scaleToFit().set(false);
        widget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        widget.allowColors().set(false);
        list.addChild(widget);
    }
    /** Muted continuation of a row: documentation text instead of a full-width banner. */
    private static void detail(VerticalListWidget<Widget> list, String text, TextColor color) {
        ComponentWidget widget = ComponentWidget.text(text, color);
        widget.clippingTextTooltip().set(true);
        widget.scaleToFit().set(false);
        widget.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        widget.allowColors().set(false);
        widget.addId("mermil-row-detail");
        list.addChild(widget);
    }
    /** Table row: value on the left, meta on the right, quiet surface between. */
    private static void row(VerticalListWidget<Widget> list, String name, TextColor nameColor,
                            Component meta) {
        Component value = Component.text(name, nameColor);
        if (meta != null) value = value.append(Component.text("  ")).append(meta);
        ComponentWidget text = ComponentWidget.component(value);
        text.addId("mermil-line");
        text.allowColors().set(false);
        text.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        list.addChild(text);
    }

    /**
     * Like {@link #row} but the value is a copy button: pressing it puts the value on the
     * clipboard, exactly how name databases make their entries easy to grab. The trailing
     * clipboard glyph does the same on its own click.
     */
    private void row(VerticalListWidget<Widget> list, String name, Component meta,
                     String clipboardValue) {
        button(list, name, () -> copy(clipboardValue), true);
        if (meta != null) {
            ComponentWidget text = ComponentWidget.component(meta);
            text.addId("mermil-row-detail");
            text.allowColors().set(false);
            text.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
            list.addChild(text);
        }
    }
    /** LabyMod's own clipboard glyph from its default icon pack. */
    private static final Icon COPY_ICON = Icon.texture(
            ResourceLocation.create("labymod", "icon_packs/default/common/copy.png"));
    /** Shared right-hand meta: after the flexible content it hugs the row's right edge. */
    private static void right(VerticalListWidget<Widget> list, FlexibleContentWidget flex, Component meta) {
        if (meta != null) {
            ComponentWidget part = ComponentWidget.component(meta);
            part.addId("mermil-row-meta");
            part.clippingTextTooltip().set(true);
            part.allowColors().set(false);
            flex.addContent(part);
        }
    }
    private static ButtonWidget button(VerticalListWidget<Widget> list, String label, Runnable click, boolean enabled) {
        ButtonWidget widget = ButtonWidget.text(label, click::run);
        widget.addId("mermil-action");
        widget.setEnabled(enabled);
        widget.scaleToFit().set(false);
        list.addChild(widget);
        return widget;
    }
    private void history(VerticalListWidget<Widget> list) {
        if (!on(config().lookupEnabled())) {
            line(list, "History ist deaktiviert. In Einstellungen wieder einschalten.", NamedTextColor.GRAY); return;
        }
        if (selectedUuid == null) {
            line(list, "Spielername / UUID suchen oder Mausrad-Spielermenue nutzen.", NamedTextColor.GRAY);
            line(list, "Befehl: /mp lookup Spielername", NamedTextColor.DARK_GRAY); return;
        }
        button(list, on(config().compareHistory()) ? "Ansicht: VERGLEICH - zu Quellen wechseln"
                : "Ansicht: QUELLEN - zum Vergleich wechseln", () -> {
            config().compareHistory().set(!on(config().compareHistory())); addon.saveSettings(); refresh();
        }, true);
        title(list, "Namenshistorie");
        for (Source source : Source.values()) sourceRow(list, source);
        if (on(config().compareHistory())) compareList(list); else sourceList(list);

        detail(list, "Namen antippen kopiert sie. Sichtungsdatum ist keine exakte Aenderungszeit.",
                NamedTextColor.DARK_GRAY);
    }
    /** Status of one source as a table row: name left, state right. */
    private void sourceRow(VerticalListWidget<Widget> list, Source source) {
        boolean enabled = sourceEnabled(source);
        Result result = results.get(source);
        String meta = "aus";
        if (enabled) {
            if (result == null) meta = "laedt ...";
            else if (result.success()) {
                String names = result.names().size() == 1 ? "1 Name" : result.names().size() + " Namen";
                if (source == Source.REBEL) {
                    String skins = result.skins().size() == 1 ? "1 Sichtung" : result.skins().size() + " Sichtungen";
                    meta = names + " - " + skins;
                } else meta = names;
            } else meta = "nicht verfuegbar";
        }
        TextColor stateColor = enabled && result != null && result.success() ? NamedTextColor.GREEN
                : enabled && result != null && !result.success() ? NamedTextColor.RED
                : NamedTextColor.DARK_GRAY;
        row(list, source.title, NamedTextColor.WHITE, Component.text(meta, stateColor));
        if (enabled && result != null && !result.success())
            detail(list, result.message(), NamedTextColor.DARK_GRAY);
    }
    /** Compare view: one row per name, sources as counts on the right. */
    private void compareList(VerticalListWidget<Widget> list) {
        List<HistoryComparison.Row> rows = HistoryComparison.compare(results);
        long both = rows.stream().filter(HistoryComparison.Row::both).count();
        if (rows.isEmpty()) {
            line(list, "Bisher keine Namenseintraege zum Vergleichen.", NamedTextColor.GRAY); return;
        }
        detail(list, rows.size() + " Namen, " + both + " in beiden Quellen", NamedTextColor.GRAY);
        for (HistoryComparison.Row row : rows) {
            TextColor labyColor = row.laby().isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;
            TextColor rebelColor = row.rebel().isEmpty() ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY;
            Component meta = Component.text("Laby " + (row.laby().isEmpty() ? HistoryComparison.absence(results.get(Source.LABY), sourceEnabled(Source.LABY)) : row.laby().size()), labyColor)
                    .append(Component.text("   ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Rebel " + (row.rebel().isEmpty() ? HistoryComparison.absence(results.get(Source.REBEL), sourceEnabled(Source.REBEL)) : row.rebel().size()), rebelColor));
            row(list, row.name(), meta, row.name());
            dates(list, "Laby", row.laby(), true);
            dates(list, "Rebel", row.rebel(), false);
        }
    }
    private void dates(VerticalListWidget<Widget> list, String sourceLabel, List<Name> names, boolean changes) {
        if (on(config().compactHistory())) return;
        for (Name name : names) detail(list, sourceLabel + (changes ? " - geaendert: "
                + (name.accurate() ? " " : " ~ ") + name.date()
                : " - zuletzt genutzt: " + name.date()), NamedTextColor.DARK_GRAY);
    }
    /** Source view: each successful source as its own dated table. */
    private void sourceList(VerticalListWidget<Widget> list) {
        for (Source source : Source.values()) {
            Result result = results.get(source);
            if (!sourceEnabled(source) || result == null || !result.success()) continue;
            title(list, source.title);
            for (Name name : result.names()) row(list, name.username(),
                    Component.text((source == Source.LABY
                            ? "geaendert: " + (name.accurate() ? "" : "~ ")
                            : "zuletzt genutzt: ") + name.date(), NamedTextColor.GRAY), name.username());
        }
    }
    private void rebelSkins(VerticalListWidget<Widget> list) {
        Result rebel = results.get(Source.REBEL);
        if (!sourceEnabled(Source.REBEL) || rebel == null || !rebel.success() || rebel.skins().isEmpty()) return;
        int skins = rebel.skins().size();
        title(list, "Skin-Sichtungen (" + (skins == 1 ? "1" : skins) + ")");
        for (Skin skin : rebel.skins()) {
            String model = skin.slim() == null ? "Modell unbekannt" : skin.slim() ? "Slim" : "Classic";
            String state = skin.active() == null ? "Status unbekannt"
                    : skin.active() ? "aktiv" : "archiviert";
            row(list, model, NamedTextColor.WHITE, Component.text(state
                    + "   " + skin.firstSeen() + " - " + skin.lastSeen(), NamedTextColor.GRAY));
        }
    }
    private void profile(VerticalListWidget<Widget> list) {
        title(list, "Spielerprofil");
        if (!on(config().quickActionsEnabled())) {
            line(list, "Profil-Schnellaktionen sind in Einstellungen deaktiviert.", NamedTextColor.GRAY); return;
        }
        if (selectedUuid == null) {
            line(list, "Zuerst einen Spieler suchen oder Mausrad > Mermil Profil auswaehlen.", NamedTextColor.GRAY); return;
        }
        UUID id = selectedUuid;
        String name = selectedName;
        String namemc = "https://namemc.com/profile/" + id;
        title(list, "Kopieren");
        button(list, "Spielername kopieren", () -> copy(name), name != null);
        button(list, "UUID kopieren", () -> copy(id.toString()), true);
        button(list, "NameMC-Profil-Link kopieren", () -> copy(namemc), true);
        title(list, "Profilseiten im Browser");
        button(list, "NameMC im Browser oeffnen", () -> open(namemc), true);
        button(list, "Laby.net im Browser oeffnen", () -> open("https://laby.net/@" + name), name != null);
        button(list, "Crafty im Browser oeffnen", () -> open("https://crafty.gg/@" + name), name != null);
        button(list, "RebelCore-Profil oeffnen", () -> open(RebelCore.profileUrl(id)), true);
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
        FlexibleContentWidget row =
                new FlexibleContentWidget();
        row.addId("mermil-setting");
        ComponentWidget name = ComponentWidget.text(label, NamedTextColor.WHITE);
        name.addId("mermil-setting-label");
        name.clippingTextTooltip().set(true);
        name.overflowStrategy().set(net.labymod.api.client.render.font.TextOverflowStrategy.WRAP);
        name.allowColors().set(false);
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
        selectedUuid = null; profileSkin = null; previewStatus = "Profil wird gesucht ...";
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
        profileSkin = null; backView = false; previewStatus = "Skin wird geladen ...";
        int preview = ++previewRequest;
        java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return ProfileSkin.fetch(uuid); } catch (Exception ignored) { return null; }
        }).thenAccept(skin -> api.minecraft().executeNextTick(() -> {
            if (preview != previewRequest || !uuid.equals(selectedUuid) || !addon.enabledNow()) return;
            profileSkin = skin;
            previewStatus = skin == null ? "Kein ladbarer oeffentlicher Skin." : "Oeffentlicher Minecraft-Skin";
            refresh();
        }));
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
        if (tab.equals("history") || tab.equals("skinHistory")) loadHistory(request);
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
        if (observedBusy != addon.busy()) { observedBusy = addon.busy(); refresh(); }
    }
}
