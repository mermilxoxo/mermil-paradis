package de.skincopy;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** An immutable snapshot of a Minecraft skin; no account credentials. */
public record SkinData(String url, boolean slim) {
    public static SkinData fromEncodedProperty(String encoded) {
        if (encoded == null || encoded.length() > 65536) {
            throw new IllegalArgumentException("Ungueltige Skin-Daten.");
        }
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(decoded).getAsJsonObject();
        JsonObject textures = root.getAsJsonObject("textures");
        if (textures == null || !textures.has("SKIN")) {
            throw new IllegalArgumentException("Dieser Spieler hat keinen kopierbaren Skin.");
        }
        JsonObject skin = textures.getAsJsonObject("SKIN");
        String url = normalizeUrl(skin.get("url").getAsString());
        JsonObject metadata = skin.getAsJsonObject("metadata");
        boolean slim = metadata != null && metadata.has("model")
                && "slim".equals(metadata.get("model").getAsString());
        return new SkinData(url, slim);
    }

    public static String normalizeUrl(String value) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || !"textures.minecraft.net".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null || uri.getPort() != -1
                || uri.getQuery() != null || uri.getFragment() != null
                || uri.getPath() == null || !uri.getPath().matches("/texture/[0-9a-fA-F]{32,128}")) {
            throw new IllegalArgumentException("Der Skin stammt nicht vom Minecraft-Texturserver.");
        }
        return "https://textures.minecraft.net" + uri.getPath();
    }
}
