package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Qualification source only. Scalar observations are never gameplay control or acceptance. */
public final class U0OrdinaryTrace {
    private static final int MAX_RECORDS = 4096;
    private static final int MAX_DEPTH = 4;
    private static final ThreadLocal<Span> CURRENT = new ThreadLocal<>();
    private static final List<Observation> ROWS = new ArrayList<>();
    private static long ordinal;
    private static long nativeTick = -1;
    private static boolean failed;
    private static boolean overflow;
    private static boolean written;
    private static long lastClientGate = Long.MIN_VALUE;
    private static long lastClientSuppression = Long.MIN_VALUE;
    private U0OrdinaryTrace() { }

    public static synchronized void nativeTick(long tick) {
        try { if (tick < 0) { failed = true; } else { nativeTick = tick; } }
        catch (RuntimeException | Error observationFailure) { failed = true; }
    }
    public static synchronized void enter(String tag, long runtimeTick) {
        try {
            var previous = CURRENT.get();
            int depth = previous == null ? 1 : previous.depth() + 1;
            if (depth > MAX_DEPTH || ordinal == Long.MAX_VALUE) { failed = true; return; }
            var span = new Span(++ordinal, runtimeTick, depth, tag, previous, System.nanoTime(), ROWS.size());
            CURRENT.set(span);
            if (!tag.equals("INPUT_POST")) { note(tag + "_ENTER", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
        } catch (RuntimeException | Error observationFailure) { failed = true; }
    }
    public static synchronized void leave(String tag) {
        try {
            var span = CURRENT.get();
            if (span == null) { failed = true; return; }
            if (!tag.equals(span.kind())) { failed = true; }
            boolean observedSend = false;
            if (tag.equals("INPUT_POST")) {
                for (int i = span.firstObservationIndex(); i < ROWS.size(); i++) {
                    var row = ROWS.get(i);
                    if (row.span() == span.id() && row.tag().equals("N1_SEND_RETURN")) { observedSend = true; break; }
                }
            }
            if (!tag.equals("INPUT_POST") || observedSend) {
                long elapsedNanos = System.nanoTime() - span.startedNanos();
                if (elapsedNanos < 0) { failed = true; }
                else { note("SPAN_NANOS", elapsedNanos, 0, 0, 0, 0, 0, 0, 0, 0, span.kind()); }
            }
            if (!tag.equals("INPUT_POST")) { note(tag + "_LEAVE", 0, 0, 0, 0, 0, 0, 0, 0, 0, ""); }
            if (span.previous() == null) { CURRENT.remove(); } else { CURRENT.set(span.previous()); }
        } catch (RuntimeException | Error observationFailure) { failed = true; CURRENT.remove(); }
    }
    public static synchronized void note(String tag, long a, long b, long c, long d, long e, long f,
            int first, int second, int third, String text) {
        try {
            var span = CURRENT.get();
            if ("NATIVE_TICKET_ADD".equals(tag) && (span == null || !("DISPATCH".equals(span.kind()) || "ENTITY".equals(span.kind())))) { return; }
            if ("BOUNDED_SCAN_RETURN".equals(tag) && (span == null || !"ENTITY".equals(span.kind()))) { return; }
            if ("TRUE_POST_CALLBACK".equals(tag) && (span == null || !"DISPATCH".equals(span.kind()))) { return; }
            if (tag != null && (tag.startsWith("OBSERVATION_FAILURE") || tag.startsWith("INVALID_"))) { failed = true; }
            if ("N1_SEND_RETURN".equals(tag) && (a <= 0 || span == null || !"INPUT_POST".equals(span.kind()))) { failed = true; }
            if ("CLIENT_GATE".equals(tag)) {
                if (lastClientGate == a && lastClientSuppression == b) { return; }
                lastClientGate = a; lastClientSuppression = b;
            }
            if (tag == null || !tag.matches("[A-Z0-9_]{1,64}") || text == null || text.length() > 64
                    || ordinal == Long.MAX_VALUE) { failed = true; return; }
            if (ROWS.size() == MAX_RECORDS) { overflow = true; return; }
            ROWS.add(new Observation(++ordinal, span == null ? 0 : span.id(),
                    span == null || span.previous() == null ? 0 : span.previous().id(), nativeTick,
                    span == null ? -1 : span.runtimeTick(), tag, a, b, c, d, e, f, first, second, third, text));
        } catch (RuntimeException | Error observationFailure) { failed = true; }
    }
    /** Called by the existing terminal tick owner outside every wrapped gameplay method. */
    public static synchronized void write(Path directory) throws IOException {
        if (written) { return; }
        if (!directory.isAbsolute() || !Files.isDirectory(directory) || Files.isSymbolicLink(directory)) {
            throw new IOException("QUALIFICATION_OUTPUT_REQUIRED");
        }
        var material = Map.of("format", "U0_ORDINARY_SCALAR_TRACE_V1", "status",
                failed || overflow || CURRENT.get() != null ? "OBSERVER_FAILED" : "OBSERVATIONS_ONLY_NOT_QUALIFIED",
                "maxRecords", MAX_RECORDS, "failed", failed, "overflow", overflow, "rows", List.copyOf(ROWS));
        Files.writeString(directory.resolve("ordinary-scalar-trace.json"),
                new GsonBuilder().setPrettyPrinting().create().toJson(material) + '\n', StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        written = true;
    }
    private record Span(long id, long runtimeTick, int depth, String kind, Span previous,
            long startedNanos, int firstObservationIndex) { }
    private record Observation(long ordinal, long span, long parent, long nativeTick, long runtimeTick, String tag,
            long a, long b, long c, long d, long e, long f, int first, int second, int third, String text) { }
}
