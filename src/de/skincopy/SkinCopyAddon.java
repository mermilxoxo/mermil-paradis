package de.skincopy;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import de.skincopy.lookup.HistoryActivity;
import de.skincopy.lookup.HistoryService;
import net.labymod.api.client.chat.command.Command;
import net.labymod.api.Laby;
import net.labymod.api.addon.LabyAddon;
import net.labymod.api.client.component.Component;
import net.labymod.api.client.component.format.NamedTextColor;
import net.labymod.api.client.entity.player.Player;
import net.labymod.api.client.entity.player.ClientPlayer;
import net.labymod.api.client.entity.player.interaction.AbstractBulletPoint;
import net.labymod.api.client.gui.icon.Icon;
import net.labymod.api.client.network.NetworkPlayerInfo;
import net.labymod.api.client.network.PlayerSkin;
import net.labymod.api.client.resources.ResourceLocation;
import net.labymod.api.client.resources.texture.GameImage;
import net.labymod.api.client.session.MinecraftServices;
import net.labymod.api.client.session.Session;
import net.labymod.api.client.session.model.MojangTextureChangedResponse;
import net.labymod.api.mojang.texture.MojangTextureType;
import net.labymod.api.models.addon.annotation.AddonMain;
import net.labymod.api.mojang.Property;
import net.labymod.api.util.io.web.request.Response;
import net.labymod.api.util.io.web.exception.WebRequestException;
import net.labymod.api.event.Subscribe;
import net.labymod.api.event.Phase;
import net.labymod.api.event.client.lifecycle.GameTickEvent;
import net.labymod.api.event.client.network.server.ServerDisconnectEvent;
import net.labymod.api.event.client.session.SessionUpdateEvent;

@AddonMain
public class SkinCopyAddon extends LabyAddon<SkinCopyConfig> {
    private static final String MENU_ID = "skincopy:apply";
    private static final String HISTORY_MENU_ID = "skincopy:history";
    private static final String PROFILE_MENU_ID = "skincopy:profile";
    private static final String RESTORE_MENU_ID = "skincopy:restore";
    private final AtomicBoolean busy = new AtomicBoolean();
    private CopySkinEntry menuEntry;
    private HistoryEntry historyEntry;
    private HistoryService historyService;
    private ProfileEntry profileEntry;
    private RestoreEntry restoreEntry;
    private long skinGeneration;
    private volatile boolean active;
    // Accessed only on the game thread. Never send a client texture to a server.
    private LiveSkin liveSkin;
    private boolean liveHookReported;

    @Override
    protected void enable() {
        this.registerSettingCategory();
        this.registerListener(this);
        this.historyService = new HistoryService(this.labyAPI());
        this.registerCommand(new ParadisCommand());
        this.active = true;
        this.registerMenu();
    }

    @Override
    protected void onActivated() {
        this.active = true;
        this.registerMenu();
    }

    @Override
    protected void onDeactivated() {
        this.active = false;
        this.skinGeneration++;
        this.liveSkin = null;
        LiveSkinOverride.clear();
        this.labyAPI().interactionMenuRegistry().unregister(MENU_ID);
        this.labyAPI().interactionMenuRegistry().unregister(HISTORY_MENU_ID);
        this.labyAPI().interactionMenuRegistry().unregister(PROFILE_MENU_ID);
        this.labyAPI().interactionMenuRegistry().unregister(RESTORE_MENU_ID);
    }

    private void registerMenu() {
        if (this.menuEntry == null) {
            this.menuEntry = new CopySkinEntry();
        }
        this.labyAPI().interactionMenuRegistry().getOrRegister(MENU_ID, this.menuEntry);
        if (this.historyEntry == null) this.historyEntry = new HistoryEntry();
        this.labyAPI().interactionMenuRegistry().getOrRegister(HISTORY_MENU_ID, this.historyEntry);
        if (this.profileEntry == null) this.profileEntry = new ProfileEntry();
        if (this.restoreEntry == null) this.restoreEntry = new RestoreEntry();
        this.labyAPI().interactionMenuRegistry().getOrRegister(PROFILE_MENU_ID, this.profileEntry);
        this.labyAPI().interactionMenuRegistry().getOrRegister(RESTORE_MENU_ID, this.restoreEntry);
    }

    @Override
    protected Class<? extends SkinCopyConfig> configurationClass() {
        return SkinCopyConfig.class;
    }

    public boolean enabledNow() {
        return this.active && Boolean.TRUE.equals(this.configuration().enabled().get());
    }

    private void copySkin(Player player) {
        if (!Boolean.TRUE.equals(configuration().skinCopyEnabled().get())) return;
        if (!this.enabledNow() || player == null) {
            return;
        }
        final SkinData skin;
        final ResourceLocation loadedTexture;
        final String playerName = player.getName();
        try {
            Collection<Property> properties = player.profile().getProperties().get("textures");
            if (properties == null || properties.isEmpty()) {
                throw new IllegalArgumentException("Fuer diesen Spieler sind keine Skin-Daten geladen.");
            }
            skin = SkinData.fromEncodedProperty(properties.iterator().next().getValue());
            loadedTexture = player.skinTexture();
        } catch (RuntimeException e) {
            this.message("Dieser Spieler hat keinen lesbaren Minecraft-Skin.", false);
            return;
        }
        changeSkin(skin, loadedTexture, "Skin von " + playerName, false);
    }

    /** Applies a validated public profile skin through the existing backup and upload flow. */
    public void applyProfileSkin(SkinData skin) {
        if (skin != null && Boolean.TRUE.equals(configuration().skinCopyEnabled().get()))
            changeSkin(skin, null, "Profil-Skin", false);
    }

    public UUID accountId() {
        Session session = labyAPI().minecraft().sessionAccessor().getSession();
        return session != null && session.hasUniqueId() ? session.getUniqueId() : null;
    }
    public boolean busy() { return busy.get(); }
    public SkinBackups.Snapshot previousSkin() {
        UUID account = accountId();
        if (account == null) return null;
        try { return SkinBackups.parse(configuration().skinBackups().get()).restorable(account); }
        catch (RuntimeException ignored) { return null; }
    }
    public String backupDescription() {
        UUID account = accountId();
        if (account == null) return "Bitte zuerst mit einem Minecraft-Account anmelden.";
        try {
            SkinBackups store = SkinBackups.parse(configuration().skinBackups().get());
            SkinBackups.Snapshot snapshot = store.restorable(account);
            if (snapshot == null) return "Noch keine Sicherung fuer diesen Account. Entsteht vor der naechsten Uebernahme.";
            String time = java.time.Instant.ofEpochMilli(snapshot.capturedAt())
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime().withNano(0).toString();
            return (store.pending(account) == null ? "Vorheriger Skin: " : "Ungeklaerter Upload - Sicherung: ")
                    + time + " | " + (snapshot.skin().slim() ? "Slim" : "Classic");
        } catch (RuntimeException ignored) {
            return "Sicherungsdaten unlesbar. Bestehende Sicherung wird nicht ueberschrieben.";
        }
    }
    public void restorePreviousSkin() {
        if (!enabledNow()) return;
        SkinBackups.Snapshot snapshot = previousSkin();
        if (snapshot == null) { message("Keine lesbare Sicherung fuer diesen Account vorhanden.", false); return; }
        changeSkin(snapshot.skin(), null, "Vorheriger Skin", true);
    }
    public void saveSettings() {
        try { saveConfiguration(); }
        catch (Exception ignored) { message("Einstellungen konnten nicht dauerhaft gespeichert werden.", false); }
        if (!Boolean.TRUE.equals(configuration().liveSkinEnabled().get())) {
            liveSkin = null;
            LiveSkinOverride.clear();
        }
    }

    private void changeSkin(SkinData skin, ResourceLocation loadedTexture, String label, boolean restoring) {
        if (!enabledNow()) return;
        Session session = labyAPI().minecraft().sessionAccessor().getSession();
        if (session == null || !session.isPremium() || !session.hasUniqueId()) {
            message("Bitte mit deinem Minecraft-Account anmelden.", false); return;
        }
        if (!busy.compareAndSet(false, true)) { message("Eine Skin-Aenderung laeuft bereits.", false); return; }
        UUID accountId = session.getUniqueId();
        long operation = ++skinGeneration;
        boolean makeBackup = restoring || Boolean.TRUE.equals(configuration().backupEnabled().get());
        message(label + " wird " + (restoring ? "wiederhergestellt" : "uebernommen") + " ...", true);
        // Tokens remain inside LabyMod. Backup writes run on the game thread before upload.
        CompletableFuture.runAsync(() -> {
            String stage = "Profilpruefung";
            String backupBefore = null;
            boolean staged = false;
            boolean uploadStarted = false;
            try {
                if (!sameAccount(accountId)) throw new IllegalStateException("Account gewechselt");
                MinecraftServices services = Laby.references().minecraftServices();
                Response<MojangTextureChangedResponse> profile = services.getProfile();
                int code = statusCode(profile);
                if (code < 200 || code >= 300 || profile.hasException()) {
                    message(ServiceStatus.failure(stage, code), false); return;
                }
                if (!profile.isPresent() || !accountId.equals(profile.get().getUniqueId())) {
                    message("Abgebrochen: Minecraft meldet ein anderes oder unlesbares Profil.", false); return;
                }
                SkinBackups.Snapshot candidate = null;
                if (makeBackup) {
                    stage = "Sicherung des bisherigen Skins";
                    var skins = profile.get().getSkins();
                    if (skins != null) for (var old : skins) {
                        if (old != null && old.isActive() && old.getVariant() != null) {
                            candidate = new SkinBackups.Snapshot(new SkinData(old.getUrl(),
                                    old.getVariant() == MinecraftServices.SkinVariant.SLIM), System.currentTimeMillis());
                            break;
                        }
                    }
                    if (candidate == null) {
                        message("Kein sicherbarer Account-Skin gefunden (z.B. Standard-Skin). Aenderung abgebrochen.", false);
                        return;
                    }
                }
                stage = "Skin-Download";
                byte[] png = SkinDownload.download(skin.url());
                GameImage image = Laby.references().gameImageProvider().getImage(new ByteArrayInputStream(png));
                Response<MojangTextureChangedResponse> response;
                try {
                    if (image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                        message("Der Skin hat kein unterstuetztes Minecraft-PNG-Format.", false); return;
                    }
                    if (makeBackup) {
                        stage = "Sicherung speichern";
                        SkinBackups.Snapshot capture = candidate;
                        backupBefore = persistBackups(accountId, store -> {
                            if (!sameAccount(accountId)) throw new IllegalStateException("Account gewechselt");
                            if (restoring) store.stageRecovery(accountId, capture); else store.stage(accountId, capture);
                        }).join();
                        staged = true;
                    } else {
                        // Even with automatic backups disabled, do not erase an unresolved journal.
                        if (SkinBackups.parse(configuration().skinBackups().get()).pending(accountId) != null) {
                            message("Ungeklaerte vorherige Aenderung: zuerst den Sicherungs-Skin wiederherstellen.", false); return;
                        }
                    }
                    stage = "PNG-Upload";
                    if (!sameAccount(accountId)) throw new IllegalStateException("Account gewechselt");
                    MinecraftServices.SkinVariant variant = skin.slim()
                            ? MinecraftServices.SkinVariant.SLIM : MinecraftServices.SkinVariant.CLASSIC;
                    uploadStarted = true;
                    response = services.changeSkin(variant, new MinecraftServices.SkinPayload(image));
                } finally { image.close(); }
                int status = statusCode(response);
                if (status >= 200 && status < 300 && !response.hasException()) {
                    if (staged) {
                        try { persistBackups(accountId, store -> store.commit(accountId)).join(); }
                        catch (RuntimeException ignored) {
                            message("Skin gespeichert. Sicherungsjournal bleibt als Wiederherstellungsoption erhalten.", false);
                        }
                    }
                    labyAPI().minecraft().executeNextTick(() -> {
                        if (operation == skinGeneration && sameAccount(accountId)) {
                            try { installLiveSkin(accountId, skin, loadedTexture, operation); }
                            catch (RuntimeException ignored) { message("Account-Skin gespeichert; lokale Live-Anzeige nicht aktualisiert.", false); }
                            message(label + (restoring ? " wiederhergestellt." : " gespeichert.")
                                    + (makeBackup ? " Rueckkehr-Skin gesichert." : ""), true);
                        }
                    });
                } else {
                    if (staged && status >= 400 && status < 500) rollbackBackups(backupBefore).join();
                    message(ServiceStatus.failure(stage, status), false);
                }
            } catch (WebRequestException e) {
                if (staged && e.getResponseCode() >= 400 && e.getResponseCode() < 500) {
                    try { rollbackBackups(backupBefore).join(); } catch (RuntimeException ignored) { }
                }
                message(ServiceStatus.failure(stage, e.getResponseCode()), false);
            } catch (Exception ignored) {
                message(stage + ": fehlgeschlagen."
                        + (staged ? " Sicherung bleibt fuer die Wiederherstellung erhalten."
                                : uploadStarted ? " Status der Account-Aenderung unbekannt." : " Es wurde kein Skin hochgeladen."), false);
            } finally { busy.set(false); }
        });
    }

    private CompletableFuture<String> persistBackups(UUID account, Consumer<SkinBackups> edit) {
        CompletableFuture<String> result = new CompletableFuture<>();
        labyAPI().minecraft().executeNextTick(() -> {
            String before = configuration().skinBackups().get();
            try {
                SkinBackups backups = SkinBackups.parse(before);
                edit.accept(backups);
                configuration().skinBackups().set(backups.toJson());
                saveConfiguration();
                result.complete(before);
            } catch (Exception e) {
                configuration().skinBackups().set(before);
                result.completeExceptionally(e);
            }
        });
        return result;
    }
    private CompletableFuture<Void> rollbackBackups(String json) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        labyAPI().minecraft().executeNextTick(() -> {
            try { configuration().skinBackups().set(json); saveConfiguration(); result.complete(null); }
            catch (Exception e) { result.completeExceptionally(e); }
        });
        return result;
    }
    private boolean sameAccount(UUID id) {
        return enabledNow() && id.equals(accountId());
    }
    private void installLiveSkin(UUID account, SkinData skin, ResourceLocation loaded, long operation) {
        if (!Boolean.TRUE.equals(configuration().liveSkinEnabled().get())) return;
        MinecraftServices.SkinVariant variant = skin.slim()
                ? MinecraftServices.SkinVariant.SLIM : MinecraftServices.SkinVariant.CLASSIC;
        liveSkin = null;
        LiveSkinOverride.clear();
        Laby.references().mojangTextureService().applySkinTexture(account, variant, skin.url());
        Consumer<ResourceLocation> apply = texture -> labyAPI().minecraft().executeNextTick(() -> {
            if (texture == null || operation != skinGeneration || !sameAccount(account)
                    || !Boolean.TRUE.equals(configuration().liveSkinEnabled().get())) return;
            liveSkin = new LiveSkin(account, texture, variant);
            liveHookReported = false;
            LiveSkinOverride.activate(account, texture, variant);
            applyLiveSkin();
        });
        if (loaded != null) apply.accept(loaded);
        else Laby.references().mojangTextureService().getTexture(account, MojangTextureType.SKIN, apply);
    }
    @Subscribe
    public void onTick(GameTickEvent event) {
        if (!Boolean.TRUE.equals(configuration().liveSkinEnabled().get())) {
            this.liveSkin = null;
            LiveSkinOverride.clear();
        }
        if (event.phase() == Phase.PRE && this.liveSkin != null && this.enabledNow()) {
            this.applyLiveSkin();
            if (!this.liveHookReported && LiveSkinOverride.hasAppliedFromRender()) {
                this.liveHookReported = true;
                this.message("Live-Hook aktiv: Die Skin-Abfrage deines Spielers liefert jetzt den neuen Skin."
                        + " Mit F5 und im Inventar pruefen.", true);
            }
        }
    }

    @Subscribe
    public void onDisconnect(ServerDisconnectEvent event) {
        this.skinGeneration++;
        this.liveSkin = null;
        LiveSkinOverride.clear();
    }

    @Subscribe
    public void onSessionUpdate(SessionUpdateEvent event) {
        if (event.isAnotherAccount()) {
            this.skinGeneration++;
            this.liveSkin = null;
            LiveSkinOverride.clear();
        }
    }

    /** Keep the current local PlayerSkin in sync even if the client replaces its cached instance. */
    private boolean applyLiveSkin() {
        LiveSkin desired = this.liveSkin;
        if (desired == null) return false;
        Session session = this.labyAPI().minecraft().sessionAccessor().getSession();
        if (session == null || !desired.accountId().equals(session.getUniqueId())) {
            this.liveSkin = null;
            LiveSkinOverride.clear();
            return false;
        }
        ClientPlayer local = this.labyAPI().minecraft().clientPlayer();
        if (local == null || !desired.accountId().equals(local.getUniqueId())) return false;
        NetworkPlayerInfo info = local.networkPlayerInfo();
        if (info == null) return false;
        try {
            PlayerSkin current = info.getSkin();
            if (current == null) return false;
            if (!Objects.equals(current.getCompletableSkinTexture().getCompleted(), desired.texture())) {
                current.setSkinTexture(desired.texture());
            }
            if (current.getSkinVariant() != desired.variant()) {
                current.setSkinVariant(desired.variant());
            }
            return true;
        } catch (RuntimeException ignored) {
            this.liveSkin = null;
            // A failed cache setter does not disable the direct render hook.
            this.message("Account-Skin gespeichert; lokale Live-Anzeige konnte nicht aktualisiert werden.", false);
            return false;
        }
    }

    private record LiveSkin(UUID accountId, ResourceLocation texture, MinecraftServices.SkinVariant variant) {}

    private static int statusCode(Response<?> response) {
        int status = response.getStatusCode();
        if (response.hasException() && response.exception().getResponseCode() > 0) {
            status = response.exception().getResponseCode();
        }
        return status;
    }

    private void message(String text, boolean success) {
        if (success && !Boolean.TRUE.equals(configuration().chatFeedback().get())) return;
        this.labyAPI().minecraft().executeNextTick(() -> this.displayMessage(
                Component.text("[Mermil Paradis] " + text, success ? NamedTextColor.AQUA : NamedTextColor.RED)));
    }

    private void openHistory(String input, UUID uuid) {
        openPanel(input, uuid, "history");
    }
    private void openPanel(String input, UUID uuid, String tab) {
        if (!this.enabledNow()) return;
        this.labyAPI().minecraft().executeNextTick(() -> {
            if (!this.enabledNow()) return;
            try {
                this.labyAPI().minecraft().minecraftWindow().displayScreen(new HistoryActivity(
                        this, this.historyService, input, uuid, tab));
            } catch (RuntimeException e) {
                this.message("Mermil-Fenster konnte nicht geoeffnet werden (" + e.getClass().getSimpleName()
                        + "). Bitte den Mermil-Fehler aus latest.log pruefen.", false);
                this.logger().error("Mermil UI konnte nicht geoeffnet werden", e);
            }
        });
    }

    private final class HistoryEntry extends AbstractBulletPoint {
        private HistoryEntry() {
            super(Component.text("User-History", NamedTextColor.AQUA),
                    Icon.texture(SkinCopyAddon.this.resource("textures/mermil.png")));
        }
        @Override
        public boolean isVisible(Player player) {
            return SkinCopyAddon.this.enabledNow() && player != null
                    && Boolean.TRUE.equals(configuration().lookupEnabled().get());
        }
        @Override
        public void execute(Player player) {
            if (player != null) SkinCopyAddon.this.openHistory(player.getName(), player.profile().getUniqueId());
        }
    }

    private final class ProfileEntry extends AbstractBulletPoint {
        private ProfileEntry() {
            super(Component.text("Mermil Profil", NamedTextColor.LIGHT_PURPLE),
                    Icon.texture(SkinCopyAddon.this.resource("textures/mermil.png")));
        }
        @Override public boolean isVisible(Player player) {
            return enabledNow() && player != null && Boolean.TRUE.equals(configuration().quickActionsEnabled().get());
        }
        @Override public void execute(Player player) {
            if (player != null) openPanel(player.getName(), player.profile().getUniqueId(), "profile");
        }
    }
    private final class RestoreEntry extends AbstractBulletPoint {
        private RestoreEntry() {
            super(Component.text("Mermil: Skin zurueck", NamedTextColor.LIGHT_PURPLE),
                    Icon.texture(SkinCopyAddon.this.resource("textures/mermil.png")));
        }
        @Override public boolean isVisible(Player player) {
            return enabledNow() && previousSkin() != null && !busy();
        }
        @Override public void execute(Player player) { restorePreviousSkin(); }
    }

    private final class ParadisCommand extends Command {
        private ParadisCommand() { super("mp", "mermilparadis"); }
        @Override
        public boolean execute(String prefix, String[] arguments) {
            if (!SkinCopyAddon.this.enabledNow()) {
                SkinCopyAddon.this.message("Addon ist deaktiviert.", false); return true;
            }
            if (arguments.length == 0 || (arguments.length == 1 && arguments[0].equalsIgnoreCase("lookup"))) {
                if (arguments.length == 0) SkinCopyAddon.this.openPanel("", null, "profile");
                else SkinCopyAddon.this.openHistory("", null);
            } else if (arguments.length == 1 && arguments[0].equalsIgnoreCase("settings")) {
                SkinCopyAddon.this.openPanel("", null, "settings");
            } else if (arguments.length == 1 && arguments[0].equalsIgnoreCase("skins")) {
                SkinCopyAddon.this.openPanel("", null, "skins");
            } else if (arguments.length == 1 && arguments[0].equalsIgnoreCase("restore")) {
                SkinCopyAddon.this.restorePreviousSkin();
            } else if (arguments.length == 2 && arguments[0].equalsIgnoreCase("profile")) {
                SkinCopyAddon.this.openPanel(arguments[1], null, "profile");
            } else if (arguments.length == 2 && arguments[0].equalsIgnoreCase("lookup")) {
                SkinCopyAddon.this.openHistory(arguments[1], null);
            } else {
                SkinCopyAddon.this.message("/mp lookup <Name|UUID> | /mp profile <Name|UUID> | /mp skins | /mp restore | /mp settings", false);
            }
            return true;
        }
    }

    private final class CopySkinEntry extends AbstractBulletPoint {
        private CopySkinEntry() {
            super(Component.text("Skin ǟ��bernehmen", NamedTextColor.AQUA),
                    Icon.texture(SkinCopyAddon.this.resource("textures/mermil.png")));
        }

        @Override
        public boolean isVisible(Player player) {
            return SkinCopyAddon.this.enabledNow() && player != null
                    && Boolean.TRUE.equals(configuration().skinCopyEnabled().get())
                    && !Objects.equals(player.profile().getUniqueId(), SkinCopyAddon.this.labyAPI().getUniqueId());
        }

        @Override
        public void execute(Player player) {
            SkinCopyAddon.this.copySkin(player);
        }
    }
}
