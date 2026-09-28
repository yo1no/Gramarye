package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySection;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** One actual native removal stack. Only its proven post-Leave structural tail may be repaired. */
public final class P11NativeCleanup {
    private static final ThreadLocal<Scope> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<LogoutScope> LOGOUT = new ThreadLocal<>();
    private static long secondaryFailures;

    private P11NativeCleanup() {}

    public static void remove(EntityAccess entity, Entity.RemovalReason reason, Manager manager, CallbackState callback,
            EntitySection<?> section, long sectionKey, Operation<Void> original) {
        if (!(entity instanceof ServerPlayer actor)) { original.call(reason); return; }
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        if (body == null) { original.call(reason); return; }
        var scope = new Scope(actor, owner, body, manager, callback, section, sectionKey, reason, CURRENT.get());
        CURRENT.set(scope);
        boolean completed = false;
        try {
            original.call(reason);
            completed = true;
        } catch (RuntimeException | Error primary) {
            // VM errors/opaque callbacks never acquire a guessed recovery guarantee.
            if (!(primary instanceof VirtualMachineError) && scope.leaveThrew && !reason.shouldDestroy()) {
                try { completed = manager.p11$finishLeaveTail(scope); }
                catch (RuntimeException | Error secondary) { secondaryFailure(); }
            }
            throw primary;
        } finally {
            try {
                var result = new Result(scope, completed);
                var logout = LOGOUT.get();
                if (logout != null && logout.actor == actor && logout.worldEntered && !logout.worldReturned) {
                    logout.world = result;
                }
                P11NativeStorageBoundary.nativeCleanupResult(actor, result);
            } catch (RuntimeException | Error secondary) { secondaryFailure(); }
            finally {
                scope.closed = true;
                if (scope.previous == null) { CURRENT.remove(); } else { CURRENT.set(scope.previous); }
            }
        }
    }

    /** Exact event invocation after onRemovedFromLevel has returned; no event is replayed. */
    public static Event leave(IEventBus bus, Event event, Operation<Event> original) {
        var scope = CURRENT.get();
        boolean exact = scope != null && !scope.closed && event instanceof EntityLeaveLevelEvent leave
                && leave.getEntity() == scope.actor && leave.getLevel() == scope.actor.level();
        if (!exact) { return original.call(bus, event); }
        scope.leaveEntered = true;
        try { return original.call(bus, event); }
        catch (RuntimeException | Error primary) { scope.leaveThrew = true; throw primary; }
    }

    /** Native manager alone supplies these private structures for this exact active scope. */
    public static <T extends EntityAccess> boolean finishLeaveTail(Manager manager,
            EntityLookup<T> lookup, Set<UUID> knownUuids, Scope scope, SectionCleanup sectionCleanup) {
        if (scope == null || CURRENT.get() != scope || scope.closed || scope.repairAttempted
                || scope.manager != manager || !scope.leaveEntered || !scope.leaveThrew
                || scope.reason.shouldDestroy() || !scope.owner.owns(scope.actor.getServer())
                || scope.owner.body(scope.actor) != scope.body || scope.body.source != scope.source
                || scope.actor.getRemovalReason() != scope.reason
                || !scope.callback.p11$unchangedRemovalFrame(scope)) { return false; }
        scope.repairAttempted = true;
        T exact = lookup.getEntity(scope.actor.getUUID());
        if (exact != scope.actor || lookup.getEntity(scope.actor.getId()) != scope.actor
                || !knownUuids.contains(scope.actor.getUUID())
                || scope.section.getEntities().anyMatch(value -> value == scope.actor)) { return false; }
        // All are the unexecuted named structural tail. No world/event callback is retried.
        lookup.remove(exact);
        knownUuids.remove(scope.actor.getUUID());
        scope.actor.setLevelCallback(EntityInLevelCallback.NULL);
        sectionCleanup.remove(scope.sectionKey, scope.section);
        return true;
    }

    /** The original Callback supplies its current fields; a moved/rebound owner is UNKNOWN. */
    public static boolean unchangedRemovalFrame(Scope scope, CallbackState callback,
            EntitySection<?> section, long sectionKey) {
        return scope != null && CURRENT.get() == scope && !scope.closed
                && sameRemovalFrame(scope.callback, callback, scope.section, section,
                        scope.sectionKey, sectionKey, scope.level, scope.actor.level())
                && ((EntityCallbackState) scope.actor).p11$hasRemovalCallback((EntityInLevelCallback) callback);
    }

    static boolean sameRemovalFrame(Object expectedCallback, Object callback,
            Object expectedSection, Object section, long expectedKey, long key,
            Object expectedLevel, Object level) {
        return expectedCallback != null && expectedSection != null && expectedLevel != null
                && expectedCallback == callback && expectedSection == section && expectedKey == key
                && expectedLevel == level;
    }

    private static void secondaryFailure() {
        if (secondaryFailures != Long.MAX_VALUE) { secondaryFailures++; }
    }

    static long secondaryFailures() { return secondaryFailures; }

    /** Root-only creation, immediately around the original PlayerList.remove invocation. */
    static LogoutScope beginLogout(ServerPlayer actor) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = owner == null ? null : owner.body(actor);
        if (body == null) { return null; }
        var scope = new LogoutScope(actor, owner, body, LOGOUT.get());
        LOGOUT.set(scope);
        return scope;
    }

    /** The unique PlayerList.remove world call; normal cleanup and native primary are preserved. */
    public static void logoutWorld(ServerLevel level, ServerPlayer actor, Entity.RemovalReason reason,
            Operation<Void> original) {
        var scope = LOGOUT.get();
        boolean exact = scope != null && scope.actor == actor && !scope.closed && !scope.worldEntered;
        if (exact) { scope.worldEntered = true; }
        original.call(level, actor, reason);
        if (exact) { scope.worldReturned = true; }
    }

    /** Never replays boss callbacks, broadcasts, LoggedOut, saving, riding or world removal. */
    static LogoutOutcome finishLogout(LogoutScope scope, boolean nativeReturned) {
        if (scope == null) { return nativeReturned ? LogoutOutcome.WHOLE_NATIVE_COMPLETED : LogoutOutcome.UNKNOWN; }
        if (LOGOUT.get() != scope || scope.closed) { return LogoutOutcome.UNKNOWN; }
        try {
            if (nativeReturned) { return LogoutOutcome.WHOLE_NATIVE_COMPLETED; }
            if (!scope.worldEntered || scope.worldReturned || scope.world == null
                    || !scope.world.structuralComplete() || !scope.world.matches(scope.actor)) {
                return LogoutOutcome.UNKNOWN;
            }
            boolean done = ((PlayerListTail) scope.actor.getServer().getPlayerList()).p11$structuralLogoutTail(scope);
            return done ? LogoutOutcome.STRUCTURAL_ONLY : LogoutOutcome.UNKNOWN;
        } catch (RuntimeException | Error secondary) {
            secondaryFailure();
            return LogoutOutcome.UNKNOWN;
        } finally {
            scope.closed = true;
            if (scope.previous == null) { LOGOUT.remove(); } else { LOGOUT.set(scope.previous); }
        }
    }

    public static boolean logoutTail(LogoutScope scope, PlayerList list, List<ServerPlayer> players,
            Map<UUID, ServerPlayer> byUuid, Map<UUID, ServerStatsCounter> stats,
            Map<UUID, PlayerAdvancements> advancements) {
        if (scope == null || LOGOUT.get() != scope || scope.closed || scope.tailAttempted
                || list != scope.actor.getServer().getPlayerList() || scope.worldReturned
                || scope.world == null || !scope.world.structuralComplete() || !scope.world.matches(scope.actor)
                || !scope.owner.owns(scope.actor.getServer()) || scope.owner.body(scope.actor) != scope.body
                || scope.body.source != scope.source) { return false; }
        scope.tailAttempted = true;
        removeExact(players, scope.actor);
        var uuid = scope.actor.getUUID();
        if (byUuid.get(uuid) == scope.actor) { byUuid.remove(uuid); }
        // Existing roots preserve their exact canonical owners/listeners. This narrow tail
        // cannot retire source duty or decide an opaque callback's completion.
        return players.stream().noneMatch(player -> player == scope.actor) && byUuid.get(uuid) != scope.actor
                && stats.get(uuid) == scope.body.stats && advancements.get(uuid) == scope.body.advancements;
    }

    /** Native Entity.equals is entity-id based; structural actor removal requires identity. */
    public static boolean removeExact(List<?> values, Object actor) {
        for (var iterator = values.iterator(); iterator.hasNext();) {
            if (iterator.next() == actor) { iterator.remove(); return true; }
        }
        return false;
    }

    enum LogoutOutcome { WHOLE_NATIVE_COMPLETED, STRUCTURAL_ONLY, UNKNOWN }

    public interface PlayerListTail {
        boolean p11$structuralLogoutTail(LogoutScope scope);
    }

    public static final class LogoutScope {
        private final ServerPlayer actor;
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body body;
        private final P11ReceiptLedger.Source source;
        private final LogoutScope previous;
        private boolean worldEntered, worldReturned, tailAttempted, closed;
        private Result world;
        private LogoutScope(ServerPlayer actor, P11QualifiedSourceOwner owner,
                P11QualifiedSourceOwner.Body body, LogoutScope previous) {
            this.actor = actor; this.owner = owner; this.body = body;
            source = body.source; this.previous = previous;
        }
    }

    public interface Manager {
        boolean p11$finishLeaveTail(Scope scope);
    }

    public interface CallbackState {
        boolean p11$unchangedRemovalFrame(Scope scope);
    }

    public interface EntityCallbackState {
        boolean p11$hasRemovalCallback(EntityInLevelCallback expected);
    }

    @FunctionalInterface
    public interface SectionCleanup {
        void remove(long key, EntitySection<?> section);
    }

    /** Opaque call-local token; no public constructor, actor accessor or arbitrary repair entry. */
    public static final class Scope {
        private final ServerPlayer actor;
        private final P11QualifiedSourceOwner owner;
        private final P11QualifiedSourceOwner.Body body;
        private final P11ReceiptLedger.Source source;
        private final Manager manager;
        private final CallbackState callback;
        private final Level level;
        private final EntitySection<?> section;
        private final long sectionKey;
        private final Entity.RemovalReason reason;
        private final Scope previous;
        private boolean leaveEntered, leaveThrew, repairAttempted, closed;
        private Scope(ServerPlayer actor, P11QualifiedSourceOwner owner, P11QualifiedSourceOwner.Body body,
                Manager manager, CallbackState callback, EntitySection<?> section, long sectionKey, Entity.RemovalReason reason,
                Scope previous) {
            this.actor = actor; this.owner = owner; this.body = body; this.source = body.source;
            this.manager = manager; this.callback = callback; this.level = actor.level();
            this.section = section; this.sectionKey = sectionKey;
            this.reason = reason; this.previous = previous;
        }
    }

    public static final class Result {
        private final ServerPlayer actor;
        private final P11ReceiptLedger.Source source;
        private final boolean complete;
        private Result(Scope scope, boolean complete) {
            actor = scope.actor; source = scope.source; this.complete = complete;
        }
        public boolean matches(ServerPlayer expected) {
            var owner = P11NativeStorageBoundary.nativeSourceOwner(expected);
            var body = owner == null ? null : owner.body(expected);
            return actor == expected && body != null && body.source == source;
        }
        public boolean structuralComplete() { return complete; }
    }
}
