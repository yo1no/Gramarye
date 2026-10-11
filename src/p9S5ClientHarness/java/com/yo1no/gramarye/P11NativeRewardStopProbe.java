package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

/** Excluded passive stop diagnostics. No actor, holder, future or task graph is retained. */
public final class P11NativeRewardStopProbe {
    private P11NativeRewardStopProbe() { }

    public record Target(Path output, int stopOrdinal) { }

    /** Implemented only by the excluded ChunkMap mixin. */
    public interface MapObservation {
        void p11$beginRewardStop(Target target, String dimension);
        void p11$endRewardStop(boolean originalNormal);
    }

    /** Two scalar fields live on their exact native holder; no side registry or retained holder. */
    public interface HolderObservation {
        long p11$rewardStopOrdinal(int stopOrdinal);
        void p11$rewardStopOrdinal(int stopOrdinal, long ordinal);
    }

    public static boolean begin(MinecraftServer server) {
        Target target;
        try {
            target = P11SourceWriterClientHarness.nativeRewardStopTarget(server);
        } catch (RuntimeException | Error observerFailure) { return false; }
        if (target == null) { return false; }
        try {
            for (var key : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
                try {
                    var level = server.getLevel(key); // Existing level lookup, never a chunk load.
                    if (level != null) {
                        ((MapObservation) level.getChunkSource().chunkMap)
                                .p11$beginRewardStop(target, key.location().getPath());
                    }
                } catch (RuntimeException | Error observerFailure) { /* Other dimensions still observed. */ }
            }
        } catch (RuntimeException | Error observerFailure) {
            // Diagnostic unavailability cannot replace or prevent the original stop.
        }
        return true; // Also clean any partially initialized diagnostic window on original unwind.
    }

    public static void end(MinecraftServer server, boolean observed, boolean originalNormal) {
        if (!observed) { return; }
        try {
            if (!server.isSameThread()) { return; }
            for (var key : java.util.List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
                try {
                    var level = server.getLevel(key);
                    if (level != null) {
                        ((MapObservation) level.getChunkSource().chunkMap).p11$endRewardStop(originalNormal);
                    }
                } catch (RuntimeException | Error observerFailure) { /* Attempt remaining windows. */ }
            }
        } catch (RuntimeException | Error observerFailure) {
            // No native cleanup, primary replacement, retry or evidence of success is inferred.
        }
    }

    /** Read-only scalars sampled after the original readiness invocation. Not an atomic snapshot. */
    public record Snapshot(boolean holderObserved, long holderOrdinal, long chunkPosition,
            int generationRefs, boolean originalReadyKnown, boolean originalReady,
            boolean saveDone, boolean saveExceptional, boolean saveCancelled,
            boolean exactPendingMember, boolean exactUpdatingMember,
            int updatingCount, int pendingUnloadCount, int pendingGenerationTasks,
            boolean unloadQueueEmpty) { }

    public static final class Window {
        private final Target target;
        private final String dimension;
        private final long started = System.nanoTime();
        private long holderSequence, readinessCalls, falseReturns, selectedHolder, selectedFalseReturns;
        private boolean observerFailed;

        public Window(Target target, String dimension) {
            if (target == null || target.stopOrdinal() < 1 || target.stopOrdinal() > 2
                    || !java.util.Set.of("overworld", "the_nether", "the_end").contains(dimension)) {
                throw new IllegalArgumentException("REWARD_STOP_DIAGNOSTIC_ROUTE");
            }
            this.target = target;
            this.dimension = dimension;
        }

        public int stopOrdinal() { return target.stopOrdinal(); }
        public long nextHolderOrdinal() { return holderSequence = Math.incrementExact(holderSequence); }
        public void failed() { observerFailed = true; }

        /** Fixed milestones, not per-loop logging. A false return is not a cause attribution. */
        public int readiness(long ordinal, boolean originalReady) {
            readinessCalls = increment(readinessCalls);
            int mask = readinessCalls == 1 ? 1 : 0;
            if (!originalReady) {
                falseReturns = increment(falseReturns);
                if (selectedHolder == 0) { selectedHolder = ordinal; }
                if (selectedHolder == ordinal) {
                    selectedFalseReturns = increment(selectedFalseReturns);
                    if (selectedFalseReturns == 1) { mask |= 2; }
                }
                if (falseReturns == 4096) { mask |= 4; }
                if (falseReturns == 65536) { mask |= 8; }
            }
            return mask;
        }

        private static long increment(long value) { return value == Long.MAX_VALUE ? value : value + 1; }

        public void report(String stage, Snapshot value, boolean originalNormal) {
            try {
                if (!java.util.Set.of("entry", "first-check", "false-1", "false-4096", "false-65536", "exit").contains(stage)) {
                    observerFailed = true; return;
                }
                var directory = target.output();
                if (!directory.isAbsolute() || !directory.equals(directory.normalize())
                        || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(directory)) { observerFailed = true; return; }
                String text = "P11-NATIVE-REWARD-STOP-DIAGNOSTIC-V1\n"
                        + "scope=PASSIVE_FIXED_SCALARS_NOT_ACCEPTANCE_OR_CAUSE_ATTRIBUTION\n"
                        + "snapshotAtomic=false\nworkerProgressObserved=false\nqueueIteration=false\n"
                        + "stopOrdinal=" + target.stopOrdinal() + "\ndimension=" + dimension + "\nstage=" + stage
                        + "\nelapsedNanos=" + Math.max(0, System.nanoTime() - started)
                        + "\noriginalNormal=" + originalNormal + "\nobserverFailed=" + observerFailed
                        + "\nreadinessCalls=" + readinessCalls + "\nfalseReturns=" + falseReturns
                        + "\nselectedHolderOrdinal=" + selectedHolder + "\nselectedHolderFalseReturns=" + selectedFalseReturns
                        + "\nholderObserved=" + value.holderObserved() + "\nholderOrdinal=" + value.holderOrdinal()
                        + "\nchunkPosition=" + value.chunkPosition() + "\ngenerationRefs=" + value.generationRefs()
                        + "\noriginalReadyKnown=" + value.originalReadyKnown() + "\noriginalReady=" + value.originalReady()
                        + "\nsaveFutureDone=" + value.saveDone() + "\nsaveFutureExceptional=" + value.saveExceptional()
                        + "\nsaveFutureCancelled=" + value.saveCancelled()
                        + "\nexactPendingMember=" + value.exactPendingMember() + "\nexactUpdatingMember=" + value.exactUpdatingMember()
                        + "\nupdatingCount=" + value.updatingCount() + "\npendingUnloadCount=" + value.pendingUnloadCount()
                        + "\npendingGenerationTasks=" + value.pendingGenerationTasks()
                        + "\nunloadQueueEmpty=" + value.unloadQueueEmpty() + "\n";
                if (text.length() > 4096) { observerFailed = true; return; }
                Files.writeString(directory.resolve("reward-stop-" + target.stopOrdinal() + '-' + dimension + '-' + stage + ".txt"),
                        text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException | RuntimeException | Error observerFailure) { observerFailed = true; }
        }
    }
}
