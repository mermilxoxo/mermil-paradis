package de.skincopy.lookup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.labymod.api.LabyAPI;
import static de.skincopy.lookup.HistoryData.*;

/** Bounded, demand-only lookups. Every callback is delivered on the Minecraft thread. */
public final class HistoryService {
    private final LabyAPI api;
    private final Map<Key, Cached> cache = new LinkedHashMap<>();
    private final Map<Key, List<Consumer<Result>>> pending = new LinkedHashMap<>();
    private long nextRequest;
    public HistoryService(LabyAPI api) { this.api = api; }
    private record Key(Source source, UUID uuid) {}
    private record Cached(long expires, Result result) {}

    public void resolve(String name, Consumer<UUID> success, Consumer<String> error) {
        if (!validName(name)) { error.accept("Bitte einen Minecraft-Namen oder eine UUID eingeben."); return; }
        AtomicBoolean done = new AtomicBoolean();
        Runnable timeout = () -> {
            if (done.compareAndSet(false, true)) deliver(() -> error.accept(
                    "Namensaufloesung nicht erreichbar. Alternativ direkt die UUID eingeben."));
        };
        CompletableFuture.delayedExecutor(12, TimeUnit.SECONDS).execute(timeout);
        try {
            api.labyNetController().loadUniqueIdByName(name, result -> {
                if (!done.compareAndSet(false, true)) return;
                if (!result.hasException() && result.isPresent() && result.get() != null) {
                    UUID uuid = result.get();
                    deliver(() -> success.accept(uuid));
                } else deliver(() -> error.accept("Name nicht gefunden oder Namensaufloesung nicht erreichbar. UUID ist ebenfalls moeglich."));
            });
        } catch (RuntimeException ignored) { timeout.run(); }
    }

    public void clearCache() { cache.clear(); }

    public void lookup(UUID uuid, boolean useLaby, boolean useRebel, Consumer<Result> callback) {
        long now = System.currentTimeMillis();
        if (now < nextRequest) {
            for (Source source : Source.values()) if (source == Source.LABY ? useLaby : useRebel)
                callback.accept(Result.error(source, "Bitte zwei Sekunden zwischen Abfragen warten."));
            return;
        }
        nextRequest = now + 2000;
        for (Source source : Source.values()) if (source == Source.LABY ? useLaby : useRebel)
            request(new Key(source, uuid), callback);
    }

    private void request(Key key, Consumer<Result> callback) {
        Cached cached = cache.get(key);
        if (cached != null && cached.expires > System.currentTimeMillis()) {
            callback.accept(cached.result); return;
        }
        List<Consumer<Result>> running = pending.get(key);
        if (running != null) { running.add(callback); return; }
        long inFlight = pending.keySet().stream().filter(k -> k.source == key.source).count();
        if (inFlight >= 2) {
            callback.accept(Result.error(key.source, "Abfragen laufen noch. Bitte kurz warten.")); return;
        }
        List<Consumer<Result>> callbacks = new ArrayList<>();
        callbacks.add(callback);
        pending.put(key, callbacks);
        AtomicBoolean finished = new AtomicBoolean();
        Consumer<Result> finish = result -> {
            if (!finished.compareAndSet(false, true)) return;
            deliver(() -> {
                List<Consumer<Result>> listeners = pending.remove(key);
                if (result.success()) {
                    cache.put(key, new Cached(System.currentTimeMillis() + 300000, result));
                    while (cache.size() > 64) cache.remove(cache.keySet().iterator().next());
                }
                if (listeners != null) for (Consumer<Result> listener : listeners) listener.accept(result);
            });
        };
        CompletableFuture.delayedExecutor(18, TimeUnit.SECONDS).execute(
                () -> finish.accept(Result.error(key.source, status(0))));
        if (key.source == Source.REBEL) {
            CompletableFuture.supplyAsync(() -> RebelCore.fetch(key.uuid)).thenAccept(finish);
            return;
        }
        try {
            api.labyNetController().loadNameHistory(key.uuid, response -> {
                if (response.hasException() || !response.isPresent()) {
                    int code = response.hasException() ? response.exception().getResponseCode() : 404;
                    finish.accept(Result.error(Source.LABY, status(code))); return;
                }
                try {
                    List<Name> names = new ArrayList<>();
                    var rows = response.get();
                    for (int i = 0; i < Math.min(rows.size(), 200); i++) {
                        var row = rows.get(i);
                        if (row != null && validName(row.getName())) names.add(new Name(row.getName(),
                                date(row.getChangedAtString()), row.isAccurate()));
                    }
                    finish.accept(new Result(Source.LABY, true, rows.size() > 200
                            ? "Ausgabe auf 200 Eintraege begrenzt." : "Archivierte Namenshistorie; ~ bedeutet ungenau.", names, List.of()));
                } catch (RuntimeException ignored) {
                    finish.accept(Result.error(Source.LABY, "Die Quelle liefert ein unbekanntes Datenformat."));
                }
            });
        } catch (RuntimeException ignored) { finish.accept(Result.error(Source.LABY, status(0))); }
    }

    private void deliver(Runnable callback) { api.minecraft().executeNextTick(callback); }
}
