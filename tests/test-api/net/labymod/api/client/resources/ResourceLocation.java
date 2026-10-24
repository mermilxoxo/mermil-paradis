package net.labymod.api.client.resources;

import java.io.InputStream;
import java.io.IOException;
import net.labymod.api.metadata.MetadataExtension;

/** Test-only API double: avoids LabyMod's static bootstrap in a standalone JVM. */
public interface ResourceLocation extends MetadataExtension {
    <T> T getMinecraftLocation();
    String getNamespace();
    String getPath();
    InputStream openStream() throws IOException;
    boolean exists();
}
