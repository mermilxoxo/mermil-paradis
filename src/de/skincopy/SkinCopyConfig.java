package de.skincopy;

import net.labymod.api.addon.AddonConfig;
import net.labymod.api.client.gui.screen.widget.widgets.input.SwitchWidget.SwitchSetting;
import net.labymod.api.configuration.loader.annotation.ConfigName;
import net.labymod.api.configuration.loader.property.ConfigProperty;

@ConfigName("settings")
public class SkinCopyConfig extends AddonConfig {
    @SwitchSetting
    private final ConfigProperty<Boolean> enabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> skinCopyEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> lookupEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> quickActionsEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> backupEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> liveSkinEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> labySourceEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> rebelSourceEnabled = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> compareHistory = new ConfigProperty<>(true);
    @SwitchSetting private final ConfigProperty<Boolean> compactHistory = new ConfigProperty<>(false);
    @SwitchSetting private final ConfigProperty<Boolean> chatFeedback = new ConfigProperty<>(true);
    // Persistent public texture snapshots, separated by account UUID. Not an exposed setting.
    private final ConfigProperty<String> skinBackups = new ConfigProperty<>("{}");

    @Override
    public ConfigProperty<Boolean> enabled() {
        return this.enabled;
    }
    public ConfigProperty<Boolean> skinCopyEnabled() { return skinCopyEnabled; }
    public ConfigProperty<Boolean> lookupEnabled() { return lookupEnabled; }
    public ConfigProperty<Boolean> quickActionsEnabled() { return quickActionsEnabled; }
    public ConfigProperty<Boolean> backupEnabled() { return backupEnabled; }
    public ConfigProperty<Boolean> liveSkinEnabled() { return liveSkinEnabled; }
    public ConfigProperty<Boolean> labySourceEnabled() { return labySourceEnabled; }
    public ConfigProperty<Boolean> rebelSourceEnabled() { return rebelSourceEnabled; }
    public ConfigProperty<Boolean> compareHistory() { return compareHistory; }
    public ConfigProperty<Boolean> compactHistory() { return compactHistory; }
    public ConfigProperty<Boolean> chatFeedback() { return chatFeedback; }
    public ConfigProperty<String> skinBackups() { return skinBackups; }
}
