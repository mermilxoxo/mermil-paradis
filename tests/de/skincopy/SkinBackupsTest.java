package de.skincopy;

import java.util.UUID;

public final class SkinBackupsTest {
    public static void main(String[] args) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        SkinBackups.Snapshot first = new SkinBackups.Snapshot(
                new SkinData("http://textures.minecraft.net/texture/" + "a".repeat(64), false), 1000);
        SkinBackups.Snapshot second = new SkinBackups.Snapshot(
                new SkinData("https://textures.minecraft.net/texture/" + "b".repeat(64), true), 2000);
        SkinBackups store = SkinBackups.parse("{}");
        check(store.restorable(a) == null, "no invented backup before first capture");
        store.stage(a, first);
        store = SkinBackups.parse(store.toJson());
        check(store.restorable(a).equals(first) && store.saved(a) == null, "pending persists before remote upload");
        check(store.restorable(b) == null, "account isolation");
        SkinBackups blocked = store;
        fails(() -> blocked.stage(a, second));
        store.commit(a);
        check(store.saved(a).equals(first) && store.pending(a) == null, "commit after success");
        store.stage(a, second);
        check(store.saved(a).equals(first), "failed attempts cannot replace saved slot");
        store.discard(a);
        check(store.restorable(a).equals(first), "known failure preserves last successful backup");
        store.stage(a, second);
        store = SkinBackups.parse(store.toJson());
        check(store.pending(a).equals(second) && store.saved(a).equals(first), "uncertain outcome survives restart");
        store.stageRecovery(a, first);
        store = SkinBackups.parse(store.toJson());
        check(store.saved(a).equals(second) && store.pending(a).equals(first), "recovery keeps both snapshots");
        store.commit(a);
        check(store.saved(a).equals(first), "restore swaps return snapshot");
        store.stage(b, second); store.commit(b);
        check(store.restorable(a).equals(first) && store.restorable(b).equals(second), "two independent accounts");
        fails(() -> SkinBackups.parse("not json"));
        fails(() -> SkinBackups.parse("{\"not-a-uuid\":{}}"));
        fails(() -> SkinBackups.parse(" ".repeat(131073)));
        fails(() -> new SkinBackups.Snapshot(new SkinData("https://evil.invalid/skin.png", false), 1));
        fails(() -> new SkinBackups.Snapshot(first.skin(), -1));
        check(first.skin().url().startsWith("https:"), "normalizes allowed public URL");
        System.out.println("PASS SkinBackupsTest: journal, commit/failure, recovery/restart, account isolation, validation");
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }
    private static void fails(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Expected rejection");
    }
}
