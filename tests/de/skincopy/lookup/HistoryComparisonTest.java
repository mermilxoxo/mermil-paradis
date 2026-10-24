package de.skincopy.lookup;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import static de.skincopy.lookup.HistoryData.*;

public final class HistoryComparisonTest {
    public static void main(String[] args) {
        Map<Source, Result> data = new EnumMap<>(Source.class);
        data.put(Source.LABY, new Result(Source.LABY, true, "", List.of(
                new Name("Shared", "2020-01-01", true), new Name("OnlyLaby", "2020-02-01", false),
                new Name("Shared", "2022-01-01", true)), List.of()));
        data.put(Source.REBEL, new Result(Source.REBEL, true, "", List.of(
                new Name("shared", "2021-01-01", false), new Name("OnlyRebel", "2023-01-01", false)), List.of()));
        var rows = HistoryComparison.compare(data);
        check(rows.size() == 3, "case insensitive union");
        check(rows.getFirst().name().equals("OnlyRebel") && rows.get(1).name().equals("Shared")
                && rows.get(2).name().equals("OnlyLaby"), "rows newest event first");
        var shared = rows.stream().filter(HistoryComparison.Row::both).findFirst().orElseThrow();
        check(shared.laby().size() == 2 && shared.rebel().size() == 1, "keep repeated dated events");
        check(shared.laby().getFirst().accurate() && !shared.rebel().getFirst().accurate(), "keep date semantics");
        check(HistoryComparison.absence(data.get(Source.REBEL), true).equals("kein Eintrag"), "successful source absence");
        data.put(Source.REBEL, Result.error(Source.REBEL, "HTTP 429"));
        check(HistoryComparison.absence(data.get(Source.REBEL), true).equals("nicht verfuegbar"), "failure is not absence");
        check(HistoryComparison.compare(data).size() == 2, "failed source adds no invented names");
        check(HistoryComparison.absence(null, true).equals("laedt noch"), "pending is not absence");
        check(HistoryComparison.absence(data.get(Source.LABY), false).equals("deaktiviert"), "disabled is not absence");
        System.out.println("PASS HistoryComparisonTest: union, order, repeated events, failed/pending/disabled sources");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
}
