package de.skincopy;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Small account-scoped journal. Pending snapshots survive uncertain HTTP outcomes. */
public final class SkinBackups {
    public record Snapshot(SkinData skin, long capturedAt) {
        public Snapshot {
            skin = new SkinData(SkinData.normalizeUrl(skin.url()), skin.slim());
            if (capturedAt < 0) throw new IllegalArgumentException("Invalid time");
        }
    }
    private record Entry(Snapshot saved, Snapshot pending) {}
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();

    public static SkinBackups parse(String json) {
        SkinBackups backups = new SkinBackups();
        if (json == null || json.length() > 131072) throw new IllegalArgumentException("Backup data too large");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (root.size() > 32) throw new IllegalArgumentException("Too many accounts");
        for (var row : root.entrySet()) {
            UUID id = de.skincopy.lookup.HistoryData.uuid(row.getKey());
            JsonObject value = row.getValue().getAsJsonObject();
            backups.entries.put(id, new Entry(read(value.get("saved")), read(value.get("pending"))));
        }
        return backups;
    }
    private static Snapshot read(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        JsonObject obj = value.getAsJsonObject();
        return new Snapshot(new SkinData(obj.get("url").getAsString(), obj.get("slim").getAsBoolean()),
                obj.get("time").getAsLong());
    }
    public Snapshot saved(UUID id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.saved;
    }
    public Snapshot pending(UUID id) {
        Entry entry = entries.get(id);
        return entry == null ? null : entry.pending;
    }
    public Snapshot restorable(UUID id) {
        Snapshot pending = pending(id);
        return pending == null ? saved(id) : pending;
    }
    public void stage(UUID id, Snapshot snapshot) {
        if (!entries.containsKey(id) && entries.size() >= 32) {
            throw new IllegalStateException("Backup-Speicher fuer 32 Accounts ist voll.");
        }
        if (pending(id) != null) {
            throw new IllegalStateException("Ungeklaerte Skin-Aenderung: zuerst den Sicherungs-Skin wiederherstellen.");
        }
        entries.put(id, new Entry(saved(id), snapshot));
    }
    /** Used for explicit recovery: preserve the older saved slot until the upload succeeds. */
    public void stageRecovery(UUID id, Snapshot snapshot) {
        entries.put(id, new Entry(restorable(id), snapshot));
    }
    public void commit(UUID id) {
        Entry entry = entries.get(id);
        if (entry != null && entry.pending != null) entries.put(id, new Entry(entry.pending, null));
    }
    public void discard(UUID id) {
        Entry entry = entries.get(id);
        if (entry != null) entries.put(id, new Entry(entry.saved, null));
    }
    public String toJson() {
        JsonObject root = new JsonObject();
        for (var row : entries.entrySet()) {
            JsonObject value = new JsonObject();
            if (row.getValue().saved != null) value.add("saved", write(row.getValue().saved));
            if (row.getValue().pending != null) value.add("pending", write(row.getValue().pending));
            root.add(row.getKey().toString(), value);
        }
        return root.toString();
    }
    private static JsonObject write(Snapshot snapshot) {
        JsonObject obj = new JsonObject();
        obj.addProperty("url", snapshot.skin.url());
        obj.addProperty("slim", snapshot.skin.slim());
        obj.addProperty("time", snapshot.capturedAt);
        return obj;
    }
}
