package de.skincopy;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

/** Downloads only public Minecraft texture PNGs, without account credentials. */
public final class SkinDownload {
    private static final int MAX_BYTES = 131072;
    private static final byte[] PNG = {(byte)137,80,78,71,13,10,26,10};
    private SkinDownload() {}

    public static byte[] download(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(SkinData.normalizeUrl(url)).toURL().openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);
        connection.setInstanceFollowRedirects(false);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Texture HTTP " + status);
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Skin too large");
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(MAX_BYTES + 1);
                validatePng(bytes);
                return bytes;
            }
        } finally {
            connection.disconnect();
        }
    }

    static void validatePng(byte[] bytes) throws IOException {
        if (bytes.length < PNG.length || bytes.length > MAX_BYTES) throw new IOException("Invalid PNG size");
        for(int i=0;i<PNG.length;i++) if(bytes[i]!=PNG[i]) throw new IOException("Not a PNG");
    }
}
