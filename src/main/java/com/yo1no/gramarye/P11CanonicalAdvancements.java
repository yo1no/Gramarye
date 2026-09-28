package com.yo1no.gramarye;

import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.network.Connection;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/** Native-call-local copying and delivery bookkeeping; neither class grants source authority. */
public final class P11CanonicalAdvancements {
    private static final ThreadLocal<CapturedCallbacks> CALLBACKS = new ThreadLocal<>();
    private static final ThreadLocal<CapturedListener> LISTENERS = new ThreadLocal<>();
    private static long scopeFailures;
    private P11CanonicalAdvancements() {}

    /** Uses the native codec, including its intentional criterion/requirements representation. */
    public static <T> T copy(Codec<T> codec, T input) {
        var encoded = codec.encodeStart(JsonOps.INSTANCE, input).getOrThrow();
        return codec.parse(JsonOps.INSTANCE, encoded).getOrThrow();
    }

    /** Both generation and native holder identity matter; a matching ID is insufficient. */
    public static boolean currentHolder(AdvancementTree tree, AdvancementHolder holder,
            long capturedGeneration, long currentGeneration) {
        var node = tree.get(holder.id());
        return capturedGeneration == currentGeneration && node != null && node.holder() == holder;
    }

    /** Same native read values/requirements, isolated only from publication into the current map. */
    public static AdvancementProgress detachedProgress(AdvancementProgress progress,
            AdvancementRequirements requirements) {
        var detached = copy(AdvancementProgress.CODEC, progress);
        detached.update(requirements);
        return detached;
    }

    public static CapturedCallbacks beginCallbacks(PlayerAdvancements canonical) {
        if (canonical == null) { return null; }
        return beginCallbacks(canonical, ((Access) canonical).p11$treeGeneration());
    }

    static CapturedCallbacks beginCallbacks(Object canonical, long generation) {
        if (canonical == null) { return null; }
        var scope = new CapturedCallbacks(canonical, generation, CALLBACKS.get());
        CALLBACKS.set(scope);
        return scope;
    }

    static long callbackGeneration(Object canonical, long fallback) {
        for (var scope = CALLBACKS.get(); scope != null; scope = scope.previous) {
            if (scope.canonical == canonical) { return scope.generation; }
        }
        return fallback;
    }

    public static void endCallbacks(CapturedCallbacks scope) {
        if (scope == null) { return; }
        if (CALLBACKS.get() != scope) { scopeFailed(); return; }
        if (scope.previous == null) { CALLBACKS.remove(); } else { CALLBACKS.set(scope.previous); }
    }

    public static CapturedListener beginListener(PlayerAdvancements canonical) {
        if (canonical == null) { return null; }
        return beginListener(canonical, ((Access) canonical).p11$treeGeneration());
    }

    static CapturedListener beginListener(Object canonical, long currentGeneration) {
        if (canonical == null) { return null; }
        var scope = new CapturedListener(canonical, callbackGeneration(canonical, currentGeneration), LISTENERS.get());
        LISTENERS.set(scope);
        return scope;
    }

    public static long listenerGeneration(PlayerAdvancements canonical, long currentGeneration) {
        return listenerGeneration((Object) canonical, currentGeneration);
    }

    static long listenerGeneration(Object canonical, long currentGeneration) {
        var scope = LISTENERS.get();
        if (scope != null && scope.canonical == canonical && !scope.claimed) {
            // Native Listener.run has one immediate award. Its later callback may issue
            // legitimate new-tree awards; those must not inherit the captured old batch.
            scope.claimed = true;
            return scope.generation;
        }
        return currentGeneration;
    }

    public static void endListener(CapturedListener scope) {
        if (scope == null) { return; }
        if (LISTENERS.get() != scope) { scopeFailed(); return; }
        if (scope.previous == null) { LISTENERS.remove(); } else { LISTENERS.set(scope.previous); }
    }

    private static void scopeFailed() {
        if (scopeFailures != Long.MAX_VALUE) { scopeFailures++; }
    }

    static long scopeFailures() { return scopeFailures; }

    /** Existing native trigger stack only: no captured list, old tree, progress, or replay payload. */
    public static final class CapturedCallbacks {
        private final Object canonical;
        private final long generation;
        private final CapturedCallbacks previous;
        private CapturedCallbacks(Object canonical, long generation, CapturedCallbacks previous) {
            this.canonical = canonical; this.generation = generation; this.previous = previous;
        }
    }

    /** Exact selected native listener invocation only; no holder, progress, tree or replay. */
    public static final class CapturedListener {
        private final Object canonical;
        private final long generation;
        private final CapturedListener previous;
        private boolean claimed;
        private CapturedListener(Object canonical, long generation, CapturedListener previous) {
            this.canonical = canonical; this.generation = generation; this.previous = previous;
        }
    }

    /** Read-only observation for the root-owned, exact native constructor association scope. */
    public interface Access {
        ServerPlayer p11$associatedPlayer();
        long p11$treeGeneration();
    }

    /** Only referenced from the live native reload call stack, never stored as another truth. */
    public static final class ReloadInput {
        public final ServerAdvancementManager manager;
        public final PlayerAdvancements.Data data;
        public final long generation;
        public boolean inputClaimed, inputApplied;

        public ReloadInput(ServerAdvancementManager manager, PlayerAdvancements.Data data, long generation) {
            this.manager = manager; this.data = data; this.generation = generation;
        }
    }

    /** One synchronous native flush attempt; never a queued packet or delivery permission. */
    public static final class Flush {
        public final ServerPlayer recipient;
        public final Connection connection;
        public final long treeGeneration, deliveryGeneration;
        public final boolean initial;
        public boolean submitted;

        public Flush(ServerPlayer recipient, Connection connection, long treeGeneration,
                long deliveryGeneration, boolean initial) {
            this.recipient = recipient; this.connection = connection;
            this.treeGeneration = treeGeneration; this.deliveryGeneration = deliveryGeneration;
            this.initial = initial;
        }
    }

    /** One current obligation, no packet/history retention. Tokens confer no delivery authority. */
    public static final class Delivery {
        private long generation;
        private boolean pending;
        private boolean exhausted;

        public Delivery(boolean pending) { this.pending = pending; }

        public void requireInitial() {
            pending = true;
            if (generation == Long.MAX_VALUE) { exhausted = true; }
            else { generation++; }
        }

        public long generation() { return generation; }
        public boolean pending() { return pending; }
        public boolean usable() { return !exhausted; }

        public boolean current(long expected) {
            return !exhausted && expected == generation;
        }

        public boolean submitted(long expected) {
            if (!current(expected)) { return false; }
            pending = false;
            return true;
        }
    }
}
