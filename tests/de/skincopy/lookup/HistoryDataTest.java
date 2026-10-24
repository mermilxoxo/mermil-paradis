package de.skincopy.lookup;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import static de.skincopy.lookup.HistoryData.*;

/** Offline contract tests using the published RebelCore profile schema, not a live response. */
public final class HistoryDataTest {
    private static final UUID ID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    public static void main(String[] args) throws Exception {
        check(uuid("069a79f444e94726a5befca90e38aaf5").equals(ID), "compact UUID");
        check(uuid(ID.toString()).equals(ID), "canonical UUID");
        fails(() -> uuid("1-1-1-1-1"));
        fails(() -> uuid("../../private"));
        check(validName("Player_123") && !validName("wrong/name") && !validName("x&secret=1")
                && !validName("12345678901234567") && !validName("§4admin"), "name validation");
        check(date("2020-02-29T16:20:11Z").equals("2020-02-29"), "ISO date");
        check(date("2020-02-29 16:20:11").equals("2020-02-29"), "SQL date");
        check(date(null).equals("Zeit unbekannt") && date("2021-02-29").equals("Zeit unbekannt")
                && date("§4untrusted").equals("Zeit unbekannt"), "unknown dates");
        String json = """
            {"uuid":"069a79f4-44e9-4726-a5be-fca90e38aaf5","username":"Notch",
             "history":[{"username":"OldName","last_used":"2020-02-29T16:20:11Z"},
                        {"username":"NewName","last_used":null},{"username":"bad/name"}],
             "textures":[{"SKIN":[{"data":"https://untrusted.invalid/never-loaded.png",
              "active":true,"slim":true,"first_seen":"2020-01-01","last_seen":"2020-02-29"},
              {"data":"ignored"}],"CAPE":[{"active":true}]}]}
            """;
        Result result = RebelCore.parse(json, ID);
        check(result.success() && result.source() == Source.REBEL, "source");
        check(result.names().size() == 2 && result.names().getFirst().username().equals("OldName"), "names");
        check(!result.names().getFirst().accurate(), "last_used must not become an exact change date");
        check(result.names().get(1).date().equals("Zeit unbekannt"), "nullable time");
        check(result.skins().size() == 2 && result.skins().getFirst().slim()
                && result.skins().getFirst().active(), "skin metadata");
        check(result.skins().get(1).slim() == null && result.skins().get(1).active() == null,
                "missing flags must not become Classic/inactive");
        fails(() -> result.names().clear());
        fails(() -> RebelCore.parse(json, UUID.randomUUID()));
        fails(() -> RebelCore.parse("{}", ID));
        fails(() -> RebelCore.parse("[]", ID));
        fails(() -> RebelCore.parse("{broken", ID));
        fails(() -> RebelCore.parse("{\"uuid\":\"" + ID + "\",\"history\":{}}", ID));
        Result empty = RebelCore.parse("{\"uuid\":\"" + ID + "\",\"history\":[]}", ID);
        check(empty.names().isEmpty() && empty.skins().isEmpty(), "empty history");
        String many = "{\"uuid\":\"" + ID + "\",\"history\":["
                + "{\"username\":\"Name\"},".repeat(250) + "{\"username\":\"Last\"}]}";
        Result limited = RebelCore.parse(many, ID);
        check(limited.names().size() == 200 && limited.message().contains("200"), "record limit");
        check(RebelCore.readBounded(new ByteArrayInputStream(new byte[RebelCore.MAX_BYTES])).length
                == RebelCore.MAX_BYTES, "exact byte limit");
        try {
            RebelCore.readBounded(new ByteArrayInputStream(new byte[RebelCore.MAX_BYTES + 1]));
            throw new AssertionError("Oversized response accepted");
        } catch (IOException expected) { }
        check(RebelCore.profileUrl(ID).equals("https://api.rebelcore.org/v1/minecraft/user/" + ID + "/profile"), "fixed endpoint");
        java.util.List<Name> ordered = newestFirst(new java.util.ArrayList<>(java.util.List.of(
                new Name("Old", "2020-01-01", true),
                new Name("Zeta", "2024-12-31", false),
                new Name("Alpha", "2024-12-31", false),
                new Name("Dateless", "Zeit unbekannt", false))));
        check(ordered.size() == 4 && ordered.stream().map(Name::username).toList().equals(
                java.util.List.of("Alpha", "Zeta", "Old", "Dateless")),
                "newest first, unknown last, ties alphabetical");
        check(newestFirst(null) == null && newestFirst(java.util.List.of()).isEmpty()
                && newestFirst(java.util.List.of(new Name("Lone", "2020-01-01", true)))
                        .getFirst().username().equals("Lone"), "degenerate inputs");
        check(status(429).contains("Abfragelimit") && status(403).contains("erlaubt"), "service status");
        System.out.println("PASS HistoryDataTest: UUID, schema, provenance, dates, order, limits");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
    }
    private static void fails(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
