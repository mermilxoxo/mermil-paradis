package de.skincopy.lookup;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/** Offline checks for identity binding, texture validation and response limits. */
public final class ProfileSkinTest {
    public static void main(String[] args) {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        String texture = "https://textures.minecraft.net/texture/" + "a".repeat(64);
        String inner = "{\"textures\":{\"SKIN\":{\"url\":\"" + texture
                + "\",\"metadata\":{\"model\":\"slim\"}}}}";
        String encoded = Base64.getEncoder().encodeToString(inner.getBytes(StandardCharsets.UTF_8));
        String json = "{\"id\":\"" + id.toString().replace("-", "")
                + "\",\"properties\":[{\"name\":\"textures\",\"value\":\"" + encoded + "\"}]}";
        var skin = ProfileSkin.parse(json, id);
        if (!skin.slim() || !skin.url().equals(texture)) throw new AssertionError("Wrong skin");
        reject(json, UUID.fromString("00000000-0000-0000-0000-000000000002"));
        reject("x".repeat(65537), id);
        reject("{}", id);
        reject("{\"id\":\"" + id + "\",\"properties\":[]}", id);
        String bad = Base64.getEncoder().encodeToString(inner.replace("textures.minecraft.net", "evil.example")
                .getBytes(StandardCharsets.UTF_8));
        reject(json.replace(encoded, bad), id);
        reject(json.replace(encoded, "not-base64"), id);
        System.out.println("PASS ProfileSkinTest: public profile identity, Slim, texture allowlist, bounds and malformed data");
    }
    private static void reject(String json, UUID id) {
        try { ProfileSkin.parse(json, id); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Invalid profile accepted");
    }
}
