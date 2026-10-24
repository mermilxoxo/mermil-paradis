package de.skincopy.lookup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static de.skincopy.lookup.HistoryData.*;

/** The documented public profile endpoint. No credentials or arbitrary texture URLs. */
public final class RebelCore {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_RECORDS = 200;
    private RebelCore() {}

    public static String profileUrl(UUID uuid) {
        return "https://api.rebelcore.org/v1/minecraft/user/" + uuid + "/profile";
    }

    public static Result fetch(UUID uuid) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(profileUrl(uuid)).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(10000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "MermilParadis/0.3.0");
            int code = connection.getResponseCode();
            if (code != 200) return Result.error(Source.REBEL, status(code));
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Too large");
            try (InputStream stream = connection.getInputStream()) {
                return parse(new String(readBounded(stream), StandardCharsets.UTF_8), uuid);
            }
        } catch (IllegalArgumentException | com.google.gson.JsonParseException e) {
            return Result.error(Source.REBEL, "Die Quelle liefert ein unbekanntes Datenformat.");
        } catch (Exception ignored) {
            return Result.error(Source.REBEL, status(0));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public static byte[] readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long deadline = System.nanoTime() + 15_000_000_000L;
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (System.nanoTime() > deadline) throw new IOException("Deadline exceeded");
            if (output.size() + count > MAX_BYTES) throw new IOException("Response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    public static Result parse(String json, UUID expected) {
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Too large");
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) throw new IllegalArgumentException("Expected profile");
        JsonObject obj = root.getAsJsonObject();
        if (!expected.equals(uuid(text(obj, "uuid")))) throw new IllegalArgumentException("Wrong profile");
        if (!obj.has("history") || !obj.get("history").isJsonArray()) {
            throw new IllegalArgumentException("Missing history");
        }
        List<Name> names = new ArrayList<>();
        List<Skin> skins = new ArrayList<>();
        JsonArray history = obj.getAsJsonArray("history");
        for (int i = 0; i < Math.min(history.size(), MAX_RECORDS); i++) {
            if (!history.get(i).isJsonObject()) continue;
            JsonObject row = history.get(i).getAsJsonObject();
            String name = text(row, "username");
            if (validName(name)) names.add(new Name(name, date(text(row, "last_used")), false));
        }
        boolean truncated = history.size() > MAX_RECORDS;
        JsonElement textures = obj.get("textures");
        if (textures != null && !textures.isJsonNull()) {
            if (!textures.isJsonArray()) throw new IllegalArgumentException("Unknown textures format");
            JsonArray groups = textures.getAsJsonArray();
            truncated |= groups.size() > MAX_RECORDS;
            int inspected = 0;
            for (int g = 0; g < Math.min(groups.size(), MAX_RECORDS); g++) {
                JsonElement group = groups.get(g);
                if (!group.isJsonObject()) continue;
                JsonElement records = group.getAsJsonObject().get("SKIN");
                if (records == null || records.isJsonNull()) continue;
                if (!records.isJsonArray()) throw new IllegalArgumentException("Unknown skin format");
                for (JsonElement entry : records.getAsJsonArray()) {
                    if (++inspected > MAX_RECORDS) { truncated = true; break; }
                    if (!entry.isJsonObject()) continue;
                    JsonObject row = entry.getAsJsonObject();
                    skins.add(new Skin(date(text(row, "first_seen")), date(text(row, "last_seen")),
                            bool(row, "active"), bool(row, "slim")));
                }
                if (inspected > MAX_RECORDS) break;
            }
        }
        return new Result(Source.REBEL, true,
                truncated ? "Ausgabe auf 200 Eintraege je Kategorie begrenzt." : "Oeffentlich archivierte Sichtungen.", names, skins);
    }

    private static String text(JsonObject row, String field) {
        JsonElement value = row.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                ? value.getAsString() : null;
    }
    private static Boolean bool(JsonObject row, String field) {
        JsonElement value = row.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()
                ? value.getAsBoolean() : null;
    }
}
