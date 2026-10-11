package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11NativeRewardStopProbe;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.util.List;
import java.util.Queue;
import net.minecraft.server.level.ChunkGenerationTask;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Does not mutate native maps, futures, tickets, refcounts or queue progress. */
@Mixin(ChunkMap.class)
abstract class P11RewardChunkMapObservationMixin implements P11NativeRewardStopProbe.MapObservation {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> updatingChunkMap;
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;
    @Shadow @Final private List<ChunkGenerationTask> pendingGenerationTasks;
    @Shadow @Final private Queue<Runnable> unloadQueue;
    @Shadow @Final private ServerLevel level;
    @Unique private P11NativeRewardStopProbe.Window p11$rewardStopWindow;

    @Override public void p11$beginRewardStop(P11NativeRewardStopProbe.Target target, String dimension) {
        try {
            if (!level.getServer().isSameThread() || p11$rewardStopWindow != null) { return; }
            var window = new P11NativeRewardStopProbe.Window(target, dimension);
            p11$rewardStopWindow = window;
            window.report("entry", p11$snapshot(null, 0, false, false), false);
        } catch (RuntimeException | Error observerFailure) { p11$failed(); }
    }

    @Override public void p11$endRewardStop(boolean originalNormal) {
        var window = p11$rewardStopWindow;
        if (window == null) { return; }
        try {
            if (!level.getServer().isSameThread()) { window.failed(); return; }
            window.report("exit", p11$snapshot(null, 0, false, false), originalNormal);
        } catch (RuntimeException | Error observerFailure) { window.failed(); }
        finally { p11$rewardStopWindow = null; }
    }

    // Exact pinned native synthetic descriptor independently checked with javap -p -s.
    @WrapOperation(method = "lambda$scheduleUnload$12(Lnet/minecraft/server/level/ChunkHolder;J)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ChunkHolder;isReadyForSaving()Z"),
            require = 1, expect = 1, allow = 1)
    private boolean p11$originalReadiness(ChunkHolder holder, Operation<Boolean> original) {
        boolean ready = original.call(holder); // Exactly once; an original primary escapes unchanged.
        var window = p11$rewardStopWindow;
        if (window == null) { return ready; }
        try {
            if (!level.getServer().isSameThread()) { window.failed(); return ready; }
            var identity = (P11NativeRewardStopProbe.HolderObservation) holder;
            long ordinal = identity.p11$rewardStopOrdinal(window.stopOrdinal());
            if (ordinal == 0) {
                ordinal = window.nextHolderOrdinal();
                identity.p11$rewardStopOrdinal(window.stopOrdinal(), ordinal);
            }
            int mask = window.readiness(ordinal, ready);
            if (mask != 0) {
                var snapshot = p11$snapshot(holder, ordinal, true, ready);
                if ((mask & 1) != 0) { window.report("first-check", snapshot, false); }
                if ((mask & 2) != 0) { window.report("false-1", snapshot, false); }
                if ((mask & 4) != 0) { window.report("false-4096", snapshot, false); }
                if ((mask & 8) != 0) { window.report("false-65536", snapshot, false); }
            }
        } catch (RuntimeException | Error observerFailure) { window.failed(); }
        return ready;
    }

    @Unique private P11NativeRewardStopProbe.Snapshot p11$snapshot(ChunkHolder holder, long ordinal,
            boolean readyKnown, boolean ready) {
        long position = holder == null ? 0 : holder.getPos().toLong();
        var save = holder == null ? null : holder.getSaveSyncFuture();
        return new P11NativeRewardStopProbe.Snapshot(holder != null, ordinal, position,
                holder == null ? -1 : holder.getGenerationRefCount(), readyKnown, ready,
                save != null && save.isDone(), save != null && save.isCompletedExceptionally(),
                save != null && save.isCancelled(), holder != null && pendingUnloads.get(position) == holder,
                holder != null && updatingChunkMap.get(position) == holder,
                updatingChunkMap.size(), pendingUnloads.size(), pendingGenerationTasks.size(), unloadQueue.isEmpty());
    }

    @Unique private void p11$failed() {
        var window = p11$rewardStopWindow;
        if (window != null) { window.failed(); }
    }
}
