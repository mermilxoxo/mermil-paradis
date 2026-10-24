package de.skincopy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class SkinDataTest {
    private static final String HASH = "a".repeat(64);
    private static String encoded(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
    private static void expectRejected(Runnable task) {
        try { task.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Invalid texture was accepted");
    }
    public static void main(String[] args) {
        SkinData slim = SkinData.fromEncodedProperty(encoded("{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + HASH + "\",\"metadata\":{\"model\":\"slim\"}}}}"));
        if (!slim.slim() || !slim.url().equals("https://textures.minecraft.net/texture/" + HASH)) throw new AssertionError("Slim skin");
        SkinData classic = SkinData.fromEncodedProperty(encoded("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + HASH + "\"}}}"));
        if (classic.slim()) throw new AssertionError("Classic skin");
        expectRejected(() -> SkinData.fromEncodedProperty(encoded("{\"textures\":{}}")));
        expectRejected(() -> SkinData.fromEncodedProperty("not base64!"));
        expectRejected(() -> SkinData.fromEncodedProperty("a".repeat(65537)));
        String[] urls = {"https://evil.example/texture/" + HASH,
                "https://textures.minecraft.net.evil.example/texture/" + HASH,
                "https://textures.minecraft.net@evil.example/texture/" + HASH,
                "https://textures.minecraft.net:443/texture/" + HASH,
                "https://textures.minecraft.net/texture/" + HASH + "?x=1",
                "https://textures.minecraft.net/texture/" + HASH + "#frag",
                "file:///texture/" + HASH, "https://textures.minecraft.net/texture/not-a-hash"};
        for (String url : urls) expectRejected(() -> SkinData.normalizeUrl(url));
        System.out.println("PASS: slim, classic, HTTPS normalization, missing/malformed data and 8 rejected URLs.");
    }
}
