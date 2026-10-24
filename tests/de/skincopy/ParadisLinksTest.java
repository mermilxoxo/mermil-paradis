package de.skincopy;

import de.skincopy.lookup.HistoryActivity;
import com.google.gson.JsonParser;
import java.util.jar.JarFile;

/** Regression: the annotation alone was insufficient with javac -proc:none. */
public final class ParadisLinksTest {
    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            var descriptor = jar.getJarEntry("META-INF/custom-services/net.labymod.api.client.gui.lss.meta.BulkLinkMeta.json");
            if (descriptor == null) throw new AssertionError("No BulkLinkMeta service descriptor");
            String json = new String(jar.getInputStream(descriptor).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String service = JsonParser.parseString(json).getAsJsonArray().get(0).getAsJsonObject()
                    .get("serviceClass").getAsString();
            Object instance = Class.forName(service).getConstructor().newInstance();
            var links = ((net.labymod.api.client.gui.lss.meta.BulkLinkMeta) instance).getLinks().get(HistoryActivity.class);
            if (links == null || links.size() != 3) throw new AssertionError("Activity not registered with loader");
            boolean base = false, primitives = false, own = false;
            for (var link : links) {
                if (link.getPath().equals("lss/style.lss")) base = true;
                if (link.getPath().equals("lss/primitives.lss")) primitives = true;
                if (link.getDefiner() == HistoryActivity.class && link.getPath().equals("lss/history.lss")) own = true;
            }
            if (!base || !primitives || !own) throw new AssertionError("Missing inherited/custom styles");
            if (jar.getJarEntry("assets/skincopy/themes/vanilla/lss/history.lss") == null)
                throw new AssertionError("No custom stylesheet resource");
            for (var method : HistoryActivity.class.getDeclaredMethods()) {
                if (method.getName().equals("loadMeta")) throw new AssertionError("Unexpected loadMeta override");
            }
        }
        System.out.println("PASS ParadisLinksTest: actual archive service discovery, activity registration, base/custom styles");
    }
}
