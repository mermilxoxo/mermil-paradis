package de.skincopy;

import de.skincopy.lookup.HistoryActivity;
import java.util.List;
import java.util.Map;
import net.labymod.api.client.gui.lss.meta.BulkLinkMeta;
import net.labymod.api.client.gui.lss.meta.LinkMeta;
import net.labymod.api.client.gui.screen.activity.Activity;

/** Explicit equivalent of the metadata normally emitted by LabyMod's annotation processor. */
public final class ParadisLinks implements BulkLinkMeta {
    @Override
    public Map<Class<?>, List<LinkMeta>> getLinks() {
        return Map.of(HistoryActivity.class, List.of(
                new LinkMeta("lss/style.lss", -10127, Activity.class),
                new LinkMeta("lss/primitives.lss", -10126, Activity.class),
                new LinkMeta("lss/history.lss", 0, HistoryActivity.class)));
    }
}
