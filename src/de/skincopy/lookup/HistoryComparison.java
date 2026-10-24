package de.skincopy.lookup;

import java.util.ArrayList;
import java.util.Comparator;
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
                .sorted(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public static String absence(Result result, boolean enabled) {
        if (!enabled) return "deaktiviert";
        if (result == null) return "laedt noch";
        return result.success() ? "kein Eintrag" : "nicht verfuegbar";
    }
}
