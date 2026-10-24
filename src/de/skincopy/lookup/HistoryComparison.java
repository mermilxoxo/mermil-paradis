package de.skincopy.lookup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import static de.skincopy.lookup.HistoryData.*;

public final class HistoryComparison {
    public record Row(String name, List<Name> laby, List<Name> rebel) {
        public Row { laby = List.copyOf(laby); rebel = List.copyOf(rebel); }
        public boolean both() { return !laby.isEmpty() && !rebel.isEmpty(); }
    }
    private HistoryComparison() {}
    public static List<Row> compare(Map<Source, Result> results) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, List<Name>> laby = new LinkedHashMap<>(), rebel = new LinkedHashMap<>();
        for (Source source : Source.values()) {
            Result result = results.get(source);
            if (result == null || !result.success()) continue;
            Map<String, List<Name>> target = source == Source.LABY ? laby : rebel;
            for (Name name : result.names()) {
                String key = name.username().toLowerCase(Locale.ROOT);
                labels.putIfAbsent(key, name.username());
                target.computeIfAbsent(key, ignored -> new ArrayList<>()).add(name);
            }
        }
        return labels.entrySet().stream().map(entry -> new Row(entry.getValue(),
                laby.getOrDefault(entry.getKey(), List.of()), rebel.getOrDefault(entry.getKey(), List.of())))
                // Name databases list the current name on top: rows by newest dated event.
                .sorted((left, right) -> {
                    LocalDate a = newestEvent(left), b = newestEvent(right);
                    if (a == null || b == null) {
                        if (a == null && b == null)
                            return String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name());
                        return a == null ? 1 : -1;
                    }
                    int order = b.compareTo(a);
                    return order != 0 ? order
                            : String.CASE_INSENSITIVE_ORDER.compare(left.name(), right.name());
                }).toList();
    }

    private static LocalDate newestEvent(Row row) {
        LocalDate best = null;
        for (Name name : row.laby()) { LocalDate value = dateKey(name); if (value != null && (best == null || value.isAfter(best))) best = value; }
        for (Name name : row.rebel()) { LocalDate value = dateKey(name); if (value != null && (best == null || value.isAfter(best))) best = value; }
        return best;
    }
    public static String absence(Result result, boolean enabled) {
        if (!enabled) return "deaktiviert";
        if (result == null) return "laedt noch";
        return result.success() ? "kein Eintrag" : "nicht verfuegbar";
    }
}
