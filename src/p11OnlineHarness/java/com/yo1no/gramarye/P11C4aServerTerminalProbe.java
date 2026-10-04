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
        if ((!P11C4aEvidence.enabled() && !contextDiagnosticCase()) || original == null || (stopping ? stopCaught : runCaught)) { return; }
        if (stopping) { stopCaught = true; } else { runCaught = true; }
        try {
            var output = P11C4aEvidence.root().resolve("server");
            if (!Files.isDirectory(output)) { return; }
            if (contextDiagnosticCase()) {
                var facts = new java.util.LinkedHashMap<String, Object>();
                facts.put("status", "OBSERVED_ORIGINAL_SERVER_TERMINAL_CATCH_NOT_ACCEPTANCE");
                facts.put("serverThread", server.isSameThread()); facts.put("serverTick", server.getTickCount());
                facts.put("boundedThrowableMetadata", contextThrowableFacts(original));
                facts.put("exceptionTextExported", false); facts.put("stackExported", false);
                try { facts.put("contextObservation", P11L1ContextRefusalProbe.pending()); }
                catch (RuntimeException | Error ignoredObservation) { facts.put("contextObservation", Map.of("status", "UNAVAILABLE")); }
                P11C4aEvidence.write(output, stopping ? "native-stop-catch.json" : "native-run-catch.json", facts);
                return;
            }
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
        P11L1HostStopProbe.originalBaseHalt(server, wait);
        if ((!P11C4aEvidence.enabled() && !contextDiagnosticCase()) || haltObserved) { return; }
        haltObserved = true;
        try {
            var output = P11C4aEvidence.root().resolve("server");
            if (!Files.isDirectory(output)) { return; }
            if (contextDiagnosticCase()) {
                P11C4aEvidence.write(output, "native-halt.json", Map.of(
                        "status", "ORIGINAL_HALT_ENTRY_NOT_TERMINAL_PROOF", "wait", wait,
                        "serverThread", server.isSameThread(), "serverTick", server.getTickCount(),
                        "stackExported", false));
                return;
            }
            P11C4aEvidence.write(output, "native-halt.json", Map.of(
                    "status", "ORIGINAL_HALT_ENTRY_NOT_TERMINAL_PROOF", "wait", wait,
                    "serverThread", server.isSameThread(), "serverTick", server.getTickCount(),
                    "sourceFrames", frames(Thread.currentThread().getStackTrace())));
        } catch (IOException | RuntimeException | Error evidenceFailure) {
            // Diagnostic failure never replaces or suppresses the original halt.
        }
    }

    private static boolean contextDiagnosticCase() {
        return "l1-work-context-refusal".equals(System.getProperty("gramarye.p11.online.case", ""));
    }

    /** Only fixed categories/codes, following at most three exact known wrapper causes. */
    private static List<Map<String, Object>> contextThrowableFacts(Throwable original) {
        var result = new ArrayList<Map<String, Object>>();
        var value = original;
        for (int depth = 0; value != null && depth < 4; depth++) {
            result.add(Map.of("depth", depth, "category", category(value), "fixedCode", fixedFailureCode(value)));
            if (depth == 3 || !(value.getClass() == net.minecraft.ReportedException.class
                    || value.getClass() == java.util.concurrent.CompletionException.class
                    || value.getClass() == java.util.concurrent.ExecutionException.class)) { break; }
            var next = value.getCause();
            if (next == value) { break; }
            value = next;
        }
        return List.copyOf(result);
    }

    private static String fixedFailureCode(Throwable value) {
        return switch (P11C4aEvidence.failureCode(value)) {
            case "L1_CONTEXT_TASK_MUST_ARRIVE_BEFORE_NATURAL_HIT",
                    "L1_CONTEXT_EXACT_NATIVE_REQUIRED_TASK_CAPTURE",
                    "L1_CONTEXT_NATIVE_TASK_HOLD_DEADLINE",
                    "L1_CONTEXT_RECONNECT_AFTER_TRUE_LOGOUT",
                    "L1_CONTEXT_NATURAL_HURT_OWNER",
                    "L1_CONTEXT_TASK_HOLD_OWNER",
                    "L1_CONTEXT_CAPTURE_REMAINS_OWNED_AT_COMMAND",
                    "L1_CONTEXT_ORIGINAL_TASK_RELEASE_IN_REAL_WORK_CONTEXT",
                    "L1_CONTEXT_REAL_OFFLINE_WORK_FUNCTION",
                    "L1_CONTEXT_ACTUAL_COMBINED_FOP_AND_TWO_QCTX_BINDINGS",
                    "L1_CONTEXT_ORIGINAL_RELOAD_APPLICATION",
                    "L1_CONTEXT_ONE_RELOAD_LISTENER",
                    "L1_CONTEXT_RELOAD_REFUSAL_WITHOUT_NATIVE_BODY",
                    "L1_CONTEXT_ORIGINAL_CONTEXT_REMAINS_OWNED_AFTER_RELOAD",
                    "L1_WORK_TO_ORIGINAL_NATIVE_OPERATION",
                    "L1_FIRST_HIT_DID_NOT_BEGIN_NATIVE_CREDIT_FREE",
                    "L1_WORK_REWARD_FIRST_NATURAL_HURT_AFTER_NORMAL_LOGOUT",
                    "L1_WORK_REWARD_W_FOP_BEFORE_FIRST_N",
                    "EVIDENCE_IO_FAILURE", "EVIDENCE_JSON_SERIALIZATION_FAILURE",
                    "HARNESS_LINKAGE_FAILURE", "UNCLASSIFIED_HARNESS_FAILURE" -> P11C4aEvidence.failureCode(value);
            default -> "UNAVAILABLE";
        };
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
