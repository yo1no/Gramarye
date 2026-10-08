package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.RegistryFriendlyByteBuf;

/** Bounded scalar costs of selected actual calls, not a scheduler, heap estimate or latency gate. */
public final class P11CooldownCostProbe {
    public interface CellCounts { int p11$cooldownRetainedAttempts(); }
    public record Timing(int operation, long startNanos) { }
    public record EncodeTiming(int writerIndex, long startNanos) { }
    private static final String[] OPERATIONS = { "prepareAdmission", "installPending", "prepareArm", "completeArm" };
    private static final long[] count = new long[4], normal = new long[4], nanos = new long[4], maximum = new long[4];
    private static final long[] minimum = { Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE };
    private static MinecraftServer server;
    private static Object owner;
    private static int cells, peakCells, receipts, peakReceipts, publishedDatumEntries, peakPublishedDatumEntries;
    private static long publications;
    private static volatile boolean failed, saturated;
    private static boolean stopped, sealed;
    private static volatile boolean encodeArmed;
    private static long encodeCalls, encodeReturns, encodeBytes, encodeNanos, encodeMaximumBytes, encodeMaximumNanos;
    private static long clientGeneration, clientSequence, clientAppliedAt, clientApplied, clientDrawn,
            clientDrawNanos, clientMaximumDrawNanos;
    private static boolean clientPending, clientFailed;
    private P11CooldownCostProbe() { }
    public static boolean selected() {
        return P11CooldownFaultProbe.selected() || P11CooldownDurabilityProbe.selected()
                || "cooldown-clone".equals(System.getProperty("gramarye.p11.online.case", ""));
    }
    static void started(MinecraftServer exact, Object service) {
        if (!selected()) return;
        P11C4aEvidence.require(server == null && exact.isSameThread() && service != null, "COOLDOWN_COST_EXACT_START");
        server = exact; owner = service;
        encodeArmed = true;
    }
    public static EncodeTiming encodeBefore(RegistryFriendlyByteBuf buffer) {
        if (!encodeArmed) return null;
        try { return new EncodeTiming(buffer.writerIndex(), System.nanoTime()); }
        catch (RuntimeException | Error secondary) { synchronized (P11CooldownCostProbe.class) { failed = true; } return null; }
    }
    public static synchronized void encodeAfter(EncodeTiming timing, RegistryFriendlyByteBuf buffer, boolean normal) {
        if (timing == null) return;
        try {
            long elapsed = System.nanoTime() - timing.startNanos();
            long bytes = (long) buffer.writerIndex() - timing.writerIndex();
            if (elapsed < 0 || bytes < 0) { failed = true; return; }
            encodeCalls = add(encodeCalls, 1);
            if (normal) {
                encodeReturns = add(encodeReturns, 1); encodeBytes = add(encodeBytes, bytes);
                encodeMaximumBytes = Math.max(encodeMaximumBytes, bytes);
            }
            encodeNanos = add(encodeNanos, elapsed); encodeMaximumNanos = Math.max(encodeMaximumNanos, elapsed);
        } catch (RuntimeException | Error secondary) { failed = true; }
    }
    static void clientApplied(long generation, long sequence) {
        if (!selected()) return;
        try {
            if (generation <= 0 || sequence <= 0) { clientFailed = true; return; }
            clientGeneration = generation; clientSequence = sequence; clientAppliedAt = System.nanoTime();
            clientApplied = add(clientApplied, 1); clientPending = true;
        } catch (RuntimeException | Error secondary) { clientFailed = true; }
    }
    static void clientDrawReturned(long generation, long sequence) {
        if (!selected() || !clientPending || generation != clientGeneration || sequence != clientSequence) return;
        try {
            long elapsed = System.nanoTime() - clientAppliedAt;
            if (elapsed < 0) { clientFailed = true; return; }
            clientDrawn = add(clientDrawn, 1); clientDrawNanos = add(clientDrawNanos, elapsed);
            clientMaximumDrawNanos = Math.max(clientMaximumDrawNanos, elapsed); clientPending = false;
        } catch (RuntimeException | Error secondary) { clientFailed = true; }
    }
    static void sealClient(Path output) throws IOException {
        if (!selected()) return;
        P11C4aEvidence.write(output, "cooldown-cost.json", Map.of("status", clientFailed
                ? "BOUNDED_CLIENT_COST_OBSERVER_FAILED" : "SAME_CLIENT_APPLIED_TO_MATCHING_DRAW_RETURN_COST",
                "acceptedSnapshots", clientApplied, "matchingOriginalDrawReturns", clientDrawn,
                "totalNanos", clientDrawNanos, "maximumNanos", clientMaximumDrawNanos,
                "counterSaturated", saturated, "crossJvmNetworkLatencyClaim", false, "latencyThresholdClaim", false));
    }
    public static Timing before(Object service, int operation) {
        if (!selected() || service != owner || server == null) return null;
        try {
            if (!server.isSameThread() || operation < 0 || operation >= 4) { failed = true; return null; }
            return new Timing(operation, System.nanoTime());
        } catch (RuntimeException | Error secondary) { failed = true; return null; }
    }
    public static void after(Object service, Timing timing, boolean returned, Map<?, ?> actualCells) {
        if (timing == null) return;
        try {
            if (service != owner || !server.isSameThread()) { failed = true; return; }
            long elapsed = System.nanoTime() - timing.startNanos();
            if (elapsed < 0) { failed = true; return; }
            int index = timing.operation(); count[index] = add(count[index], 1);
            if (returned) normal[index] = add(normal[index], 1);
            nanos[index] = add(nanos[index], elapsed); maximum[index] = Math.max(maximum[index], elapsed);
            minimum[index] = Math.min(minimum[index], elapsed); sample(actualCells);
        } catch (RuntimeException | Error secondary) { failed = true; }
    }
    public static void published(Object service, Object replacement, Map<?, ?> actualCells) {
        if (!selected() || service != owner || server == null) return;
        try {
            if (!server.isSameThread() || !(replacement instanceof P11CastCooldownData data)) { failed = true; return; }
            publications = add(publications, 1);
            publishedDatumEntries = data.entries.size();
            peakPublishedDatumEntries = Math.max(peakPublishedDatumEntries, publishedDatumEntries);
            sample(actualCells);
        } catch (RuntimeException | Error secondary) { failed = true; }
    }
    public static void stopped(Object service, Map<?, ?> actualCells) {
        if (!selected() || service != owner || server == null) return;
        try {
            if (!server.isSameThread()) { failed = true; return; }
            sample(actualCells); stopped = true;
        } catch (RuntimeException | Error secondary) { failed = true; }
    }
    private static void sample(Map<?, ?> actualCells) {
        int total = 0;
        for (Object cell : actualCells.values()) {
            if (!(cell instanceof CellCounts view)) { failed = true; return; }
            total = Math.addExact(total, view.p11$cooldownRetainedAttempts());
        }
        cells = actualCells.size(); receipts = total;
        peakCells = Math.max(peakCells, cells); peakReceipts = Math.max(peakReceipts, receipts);
    }
    static synchronized void seal(Path output, P11QualifiedSourceOwner.Summary summary) throws IOException {
        if (!selected() || sealed) return;
        var methods = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < 4; index++) {
            var row = new LinkedHashMap<String, Object>(); row.put("method", OPERATIONS[index]); row.put("calls", count[index]);
            row.put("normalReturns", normal[index]); row.put("totalNanos", nanos[index]); row.put("maximumNanos", maximum[index]);
            row.put("minimumNanos", count[index] == 0 ? -1 : minimum[index]); methods.add(row);
        }
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", failed ? "BOUNDED_COST_OBSERVER_FAILED" : "ACTUAL_BOUNDED_CALL_COSTS_NOT_CAPACITY_QUALIFICATION");
        facts.put("methods", List.copyOf(methods)); facts.put("counterSaturated", saturated);
        facts.put("actualCellsAtLastObservation", cells); facts.put("observedCellPeak", peakCells);
        facts.put("actualRetainedAttemptsAtLastObservation", receipts); facts.put("observedRetainedAttemptPeak", peakReceipts);
        facts.put("originalServiceStoppedObserved", stopped); facts.put("actualPublicationReturns", publications);
        facts.put("lastPublishedDatumEntries", publishedDatumEntries); facts.put("peakEntriesInOnePublishedDatum", peakPublishedDatumEntries);
        facts.put("originalSerializations", summary.serializations()); facts.put("originalSerializerNanos", summary.serializerNanos());
        facts.put("originalWrites", summary.writes()); facts.put("originalWriteNanos", summary.writeNanos());
        facts.put("actualSourceFailures", summary.failures()); facts.put("dirtyUuids", summary.resources().dirtyUuids());
        facts.put("nativeStopNormal", summary.nativeStopNormal()); facts.put("latencyThresholdClaim", false);
        facts.put("actualSnapshotEncodeCalls", encodeCalls); facts.put("actualSnapshotEncodeNormalReturns", encodeReturns);
        facts.put("actualSnapshotEncodedPayloadBytes", encodeBytes); facts.put("maximumSnapshotEncodedPayloadBytes", encodeMaximumBytes);
        facts.put("actualSnapshotEncodeNanos", encodeNanos); facts.put("maximumSnapshotEncodeNanos", encodeMaximumNanos);
        facts.put("wireFramingBytesIncluded", false);
        facts.put("observationBoundary", "ACTUAL_ROOT_RETIRE_RETURN");
        facts.put("retainedUuids", summary.resources().retainedUuids()); facts.put("inFlight", summary.resources().inFlight());
        facts.put("oldestDirtyMillis", summary.dirtyAge().oldestMillis().orElse(-1));
        facts.put("dirtyAgeClockUnavailable", summary.dirtyAge().clockUnavailable());
        facts.put("saveProgressElapsedMillis", summary.saveProgress().elapsedMillis().orElse(-1));
        facts.put("saveProgress", summary.saveProgress().kinds().stream().map(kind -> Map.of(
                "kind", kind.kind().name(), "dirtySources", kind.dirtySources(),
                "oldestDirtyMillis", kind.oldestDirtyMillis().orElse(-1), "ageUnavailable", kind.ageUnavailable(),
                "successfulPhysicalWrites", kind.successfulPhysicalWrites(), "counterSaturated", kind.counterSaturated())).toList());
        facts.put("heapOrLargeServerCapacityClaim", false); facts.put("crossJvmNetworkLatencyClaim", false);
        P11C4aEvidence.write(output, "cooldown-cost.json", facts); sealed = true; encodeArmed = false;
    }
    private static long add(long first, long second) {
        if (Long.MAX_VALUE - first < second) { saturated = true; return Long.MAX_VALUE; }
        return first + second;
    }
}
