package com.yo1no.gramarye;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.Visibility;

/** Synchronous terminal removal after native section traversal; never a work permit. */
public final class P11P9TrackingCleanup {
    // Snapshot bound from the active P9 permit ceiling, not a native-graph claim.
    // P5 admission queues work; only its original drain can materialize a new P9,
    // and a reentrant drain during dispatch is rejected. No cross-call queue.
    private static final int MAX_TERMINALS = 128;
    private static final ThreadLocal<Traversal> CURRENT = new ThreadLocal<>();

    private P11P9TrackingCleanup() {}

    public static void chunkStatus(EntitySectionStorage<? extends EntityAccess> sections,
            ChunkPos position, Visibility visibility, Operation<Void> original) {
        var traversal = enter();
        Throwable primary = null;
        try {
            original.call(position, visibility);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            try {
                sections.getExistingSectionsInChunk(position.toLong())
                        .flatMap(section -> section.getEntities()).forEach(traversal::capture);
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            } finally {
                leave(traversal, primary, cleanup);
            }
        }
    }

    public static void moved(EntityAccess entity, Operation<Void> original) {
        if (!(entity instanceof P9StarterProjectile) && CURRENT.get() == null) {
            original.call();
            return;
        }
        var traversal = enter();
        Throwable primary = null;
        try {
            original.call();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable cleanup = null;
            try {
                traversal.capture(entity);
            } catch (RuntimeException | Error failure) {
                cleanup = failure;
            } finally {
                leave(traversal, primary, cleanup);
            }
        }
    }

    private static Traversal enter() {
        var traversal = CURRENT.get();
        if (traversal == null) {
            traversal = new Traversal();
            CURRENT.set(traversal);
        }
        traversal.depth++;
        return traversal;
    }

    static void discardClosed(P9StarterProjectile projectile) {
        if (!projectile.closedForTrackingRemoval()) { return; }
        var traversal = CURRENT.get();
        if (traversal == null) { projectile.discard(); }
        else { traversal.capture(projectile); }
    }

    private static void leave(Traversal traversal, Throwable primary, Throwable cleanup) {
        traversal.depth--;
        if (traversal.depth != 0 || traversal.flushing) {
            if (primary == null) { rethrow(cleanup); }
            return;
        }
        try {
            traversal.flushing = true;
            // A failed collection still cleans every previously captured terminal.
            for (int index = 0; index < traversal.size; index++) {
                try {
                    var projectile = traversal.terminals[index];
                    if (projectile.closedForTrackingRemoval()) { projectile.discard(); }
                } catch (RuntimeException | Error failure) {
                    if (cleanup == null) { cleanup = failure; }
                }
            }
        } finally {
            // Even allocation/collection/discard Error cannot leak the native call scope.
            CURRENT.remove();
            for (int index = 0; index < traversal.size; index++) {
                traversal.terminals[index] = null;
            }
        }
        if (primary == null) { rethrow(cleanup); }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) { throw runtime; }
        if (failure instanceof Error error) { throw error; }
    }

    private static final class Traversal {
        private P9StarterProjectile[] terminals;
        private int depth;
        private int size;
        private boolean flushing;

        private void capture(EntityAccess entity) {
            if (!(entity instanceof P9StarterProjectile projectile)
                    || !projectile.closedForTrackingRemoval()) { return; }
            for (int index = 0; index < size; index++) {
                if (terminals[index] == projectile) { return; }
            }
            if (size == MAX_TERMINALS) {
                throw new IllegalStateException("P9 terminal tracking cleanup bound exceeded");
            }
            if (terminals == null) { terminals = new P9StarterProjectile[MAX_TERMINALS]; }
            terminals[size++] = projectile;
        }
    }
}
