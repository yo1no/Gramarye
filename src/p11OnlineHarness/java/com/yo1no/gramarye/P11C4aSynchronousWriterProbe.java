package com.yo1no.gramarye;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Excluded, four-record observation of the original synchronous writer; never a save or retry. */
public final class P11C4aSynchronousWriterProbe {
    public enum Point { BEGIN_PLAYER_WRITER, COMPLETED_PLAYER_WRITE }

    private static int observations;
    private static int observerFailures;

    private P11C4aSynchronousWriterProbe() {}

    /** Only detached bounded scalars survive the original call; no source/body/receipt is retained. */
    public static final class Observation {
        private final int ordinal;
        private final Point point;
        private final Map<String, Object> before;

        private Observation(int ordinal, Point point, Map<String, Object> before) {
            this.ordinal = ordinal;
            this.point = point;
            this.before = before;
        }
    }

    public static Observation before(Object ownerValue, Object bodyValue, Point point) {
        try {
            if (!P11C4aEvidence.enabled() || observations >= 4
                    || !(ownerValue instanceof P11QualifiedSourceOwner owner)
                    || !(bodyValue instanceof P11QualifiedSourceOwner.Body body)
                    || !owner.owns(body.actor.getServer())) { return null; }
            int ordinal = ++observations;
            return new Observation(ordinal, point, snapshot(owner, body));
        } catch (RuntimeException | Error secondary) {
            failed();
            return null;
        }
    }

    public static void after(Observation observation, Object ownerValue, Object bodyValue,
            Object receiptValue, boolean normal, boolean completed) {
        if (observation == null) { return; }
        try {
            var owner = (P11QualifiedSourceOwner) ownerValue;
            var body = (P11QualifiedSourceOwner.Body) bodyValue;
            var values = new LinkedHashMap<String, Object>();
            values.put("status", "OBSERVED_ORIGINAL_SYNCHRONOUS_WRITER_NOT_ACCEPTANCE");
            values.put("point", observation.point.name());
            values.put("ordinal", observation.ordinal);
            values.put("originalReturnedNormally", normal);
            values.put("before", observation.before);
            values.put("after", snapshot(owner, body));
            values.put("receiptPresent", receiptValue != null);
            if (receiptValue instanceof P11ReceiptLedger.PhysicalWriterReceipt receipt) {
                values.put("receipt", Map.of("kind", receipt.kind().name(),
                        "materialVersion", receipt.materialVersion(),
                        "sourceEpoch", receipt.source().epoch(),
                        "sourceVersion", receipt.source().version(),
                        "exactCurrentSource", receipt.source() == body.source));
            }
            if (observation.point == Point.COMPLETED_PLAYER_WRITE) {
                values.put("completedResult", normal && completed);
            }
            values.put("observerFailures", observerFailures);
            P11C4aEvidence.write(P11C4aEvidence.root().resolve("server"),
                    "synchronous-writer-" + observation.ordinal + ".json", values);
        } catch (IOException | RuntimeException | Error secondary) {
            // Evidence must not replace or suppress an original exception, Error, or return value.
            failed();
        }
    }

    private static Map<String, Object> snapshot(P11QualifiedSourceOwner owner,
            P11QualifiedSourceOwner.Body body) {
        var diagnostic = owner.diagnostics(body.actor.getUUID());
        var values = new LinkedHashMap<String, Object>();
        values.put("bodyComplete", body.complete);
        values.put("bodyFault", body.fault.name());
        values.put("accountFault", body.account.fault.name());
        values.put("exactCurrentBody", body.account.current == body);
        values.put("candidatePresent", body.account.candidate != null);
        values.put("constructorPresent", body.account.constructing != null);
        values.put("canSerialize", owner.canSerialize(body));
        values.put("canCopy", owner.canCopy(body));
        values.put("connectionNull", body.actor.connection == null);
        values.put("removed", body.actor.isRemoved());
        values.put("logoutActive", body.logoutActive);
        values.put("envelopePresent", body.envelope != null);
        values.put("sourceEpoch", body.source.epoch());
        values.put("sourceVersion", body.source.version());
        var roots = new LinkedHashMap<String, Object>();
        for (var kind : new P11ControlBudgets.Root[] { P11ControlBudgets.Root.NATIVE_CREDIT,
                P11ControlBudgets.Root.OPERATION, P11ControlBudgets.Root.COMMAND_CONTEXT,
                P11ControlBudgets.Root.TRANSITION }) {
            roots.put(kind.name(), body.account.nativeCounts[kind.ordinal()]);
        }
        values.put("exactAccountRoots", roots);
        var writers = new ArrayList<Map<String, Object>>();
        for (var writer : diagnostic.writers()) {
            if (!List.of("PLAYER_DATA", "STATISTICS", "ADVANCEMENTS").contains(writer.kind())) { continue; }
            writers.add(Map.of("kind", writer.kind(), "materialVersion", writer.attempt(),
                    "dirty", writer.dirty(), "terminal", writer.terminal(),
                    "encode", writer.encode(), "write", writer.write(),
                    "close", writer.close(), "replace", writer.replace()));
        }
        values.put("writers", List.copyOf(writers));
        return Map.copyOf(values);
    }

    private static void failed() {
        if (observerFailures != Integer.MAX_VALUE) { observerFailures++; }
    }
}
