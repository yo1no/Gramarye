package com.yo1no.gramarye;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

/** Excluded, bounded original-terminal diagnostics. Never reads exception text or native logs. */
public final class P11C4aServerTerminalProbe {
    private static boolean runCaught, stopCaught, haltObserved;

    private P11C4aServerTerminalProbe() { }

    public static synchronized void caught(MinecraftServer server, boolean stopping, Throwable original) {
        if (!P11C4aEvidence.enabled() || original == null || (stopping ? stopCaught : runCaught)) { return; }
        if (stopping) { stopCaught = true; } else { runCaught = true; }
        try {
            var output = P11C4aEvidence.root().resolve("server");
            if (!Files.isDirectory(output)) { return; }
            var kinds = new ArrayList<Map<String, Object>>();
            var seen = new IdentityHashMap<Throwable, Boolean>();
            var value = original;
            for (int depth = 0; value != null && depth < 4 && seen.put(value, true) == null; depth++) {
                kinds.add(Map.of("depth", depth, "category", category(value), "sourceFrames", frames(value.getStackTrace())));
                value = value.getCause();
            }
            P11C4aEvidence.write(output, stopping ? "native-stop-catch.json" : "native-run-catch.json", Map.of(
                    "status", "OBSERVED_ORIGINAL_SERVER_TERMINAL_CATCH_NOT_ACCEPTANCE",
                    "serverThread", server.isSameThread(), "serverTick", server.getTickCount(),
                    "boundedThrowableMetadata", List.copyOf(kinds), "exceptionTextExported", false));
        } catch (IOException | RuntimeException | Error evidenceFailure) {
            // The original logger, crash handling and terminal finally still own the original Throwable.
        }
    }

    public static synchronized void halt(MinecraftServer server, boolean wait) {
        if (!P11C4aEvidence.enabled() || haltObserved) { return; }
        haltObserved = true;
        try {
            var output = P11C4aEvidence.root().resolve("server");
            if (!Files.isDirectory(output)) { return; }
            P11C4aEvidence.write(output, "native-halt.json", Map.of(
                    "status", "ORIGINAL_HALT_ENTRY_NOT_TERMINAL_PROOF", "wait", wait,
                    "serverThread", server.isSameThread(), "serverTick", server.getTickCount(),
                    "sourceFrames", frames(Thread.currentThread().getStackTrace())));
        } catch (IOException | RuntimeException | Error evidenceFailure) {
            // Diagnostic failure never replaces or suppresses the original halt.
        }
    }

    private static String category(Throwable value) {
        return value instanceof net.minecraft.ReportedException ? "REPORTED_EXCEPTION"
                : value instanceof AssertionError ? "ASSERTION_ERROR"
                : value instanceof OutOfMemoryError ? "OUT_OF_MEMORY"
                : value instanceof StackOverflowError ? "STACK_OVERFLOW"
                : value instanceof LinkageError ? "LINKAGE_ERROR"
                : value instanceof IllegalStateException ? "ILLEGAL_STATE"
                : value instanceof IllegalArgumentException ? "ILLEGAL_ARGUMENT"
                : value instanceof NullPointerException ? "NULL_POINTER"
                : value instanceof java.util.NoSuchElementException ? "NO_SUCH_ELEMENT"
                : value instanceof IOException ? "IO_EXCEPTION"
                : value instanceof Error ? "OTHER_ERROR" : "OTHER_EXCEPTION";
    }

    private static List<Map<String, Object>> frames(StackTraceElement[] values) {
        var result = new ArrayList<Map<String, Object>>();
        for (var frame : values) {
            if (result.size() == 16) { break; }
            String owner = frame.getClassName(), method = frame.getMethodName();
            if (!(owner.startsWith("com.yo1no.gramarye.") || owner.startsWith("net.minecraft.server.")
                    || owner.startsWith("net.minecraft.network.") || owner.startsWith("net.minecraft.world.entity."))) { continue; }
            if (owner.length() > 200 || method.length() > 200
                    || !owner.matches("[A-Za-z0-9_.$]+") || !method.matches("[A-Za-z0-9_$<>]+")) { continue; }
            result.add(Map.of("class", owner, "method", method, "line", frame.getLineNumber()));
        }
        return List.copyOf(result);
    }
}
