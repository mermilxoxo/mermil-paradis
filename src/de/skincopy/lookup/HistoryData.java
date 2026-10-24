package de.skincopy.lookup;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Provider dates keep their own meaning; a sighting is never a verified name change. */
public final class HistoryData {
    private HistoryData() {}
    public enum Source {
        LABY("Laby.net"), REBEL("RebelCore");
        public final String title;
        Source(String title) { this.title = title; }
    }
    public record Name(String username, String date, boolean accurate) {}
    public record Skin(String firstSeen, String lastSeen, Boolean active, Boolean slim) {}
    public record Result(Source source, boolean success, String message, List<Name> names, List<Skin> skins) {
        public Result {
            names = List.copyOf(names);
            skins = List.copyOf(skins);
        }
        public static Result error(Source source, String message) {
            return new Result(source, false, message, List.of(), List.of());
        }
    }
    public static boolean validName(String value) {
        return value != null && value.matches("[A-Za-z0-9_]{1,16}");
    }
    public static UUID uuid(String input) {
        if (input == null) throw new IllegalArgumentException("UUID fehlt");
        String value = input.trim();
        if (value.matches("(?i)[0-9a-f]{32}")) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-"
                    + value.substring(12, 16) + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        if (!value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw new IllegalArgumentException("Ungueltige UUID");
        }
        return UUID.fromString(value);
    }
    public static String date(String raw) {
        // Show only a validated calendar date, never service text or formatting codes.
        if (raw != null && raw.length() >= 10 && raw.length() <= 64
                && raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}(?:[T ].*)?")) {
            try { return LocalDate.parse(raw.substring(0, 10)).toString(); }
            catch (RuntimeException ignored) { }
        }
        return "Zeit unbekannt";
    }
    public static String status(int code) {
        return switch (code) {
            case 404, 204 -> "Kein oeffentlicher Eintrag gefunden.";
            case 429 -> "Abfragelimit erreicht. Bitte spaeter erneut versuchen.";
            case 401, 403 -> "Quelle erlaubt diese Abfrage derzeit nicht.";
            default -> code > 0 ? "Quelle nicht erreichbar (HTTP " + code + ")."
                    : "Quelle nicht erreichbar oder Zeitlimit ueberschritten.";
        };
    }
}
