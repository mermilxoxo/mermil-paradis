package de.skincopy.lookup;

import com.google.gson.JsonParser;
import de.skincopy.SkinData;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Public session profile lookup; never sends account credentials or follows redirects. */
public final class ProfileSkin {
    private ProfileSkin() {}

    /** Fetches at most 64 KiB from Mojang's fixed public profile endpoint. */
    public static SkinData fetch(UUID id) throws Exception {
        var connection = (HttpURLConnection) URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                + id.toString().replace("-", "")).toURL().openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setInstanceFollowRedirects(false);
        try {
            if (connection.getResponseCode() != 200) throw new IllegalArgumentException("Profil nicht erreichbar");
            try (var stream = connection.getInputStream()) {
                byte[] body = stream.readNBytes(65537);
                if (body.length > 65536) throw new IllegalArgumentException("Profil zu gross");
                return parse(new String(body, StandardCharsets.UTF_8), id);
            }
        } finally { connection.disconnect(); }
    }

    /** Validates the returned profile identity and its bounded texture property. */
    public static SkinData parse(String json, UUID expected) {
        if (json.length() > 65536) throw new IllegalArgumentException("Profil zu gross");
        var root = JsonParser.parseString(json).getAsJsonObject();
        if (!expected.equals(HistoryData.uuid(root.get("id").getAsString())))
            throw new IllegalArgumentException("Falsches Profil");
        var properties = root.getAsJsonArray("properties");
        if (properties == null || properties.size() > 64) throw new IllegalArgumentException("Keine Texturen");
        for (var property : properties) {
            var item = property.getAsJsonObject();
            if ("textures".equals(item.get("name").getAsString()))
                return SkinData.fromEncodedProperty(item.get("value").getAsString());
        }
        throw new IllegalArgumentException("Kein oeffentlicher Skin");
    }
}
