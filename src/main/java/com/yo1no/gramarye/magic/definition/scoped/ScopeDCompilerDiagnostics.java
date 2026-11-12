package com.yo1no.gramarye.magic.definition.scoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.MAX_COMPILER_DIAGNOSTICS;

/** A bounded diagnostic stream in canonical traversal order, with independent fatal accounting. */
final class ScopeDCompilerDiagnostics {
    enum Stage { RESOLVER, MANIFEST, FRONTEND, LOWERING, FEEDBACK, PROOF }
    enum Code {
        ROOT_UNAVAILABLE, MISSING_DEPENDENCY, FOREIGN_OWNER, STALE_GENERATION, STALE_CONTEXT,
        UNKNOWN_GAMEPLAY, MISSING_REQUIRED, HOOK_ONLY, BACKEND, DESCRIPTOR, CAPABILITY,
        ARGUMENT, TYPE, CREATION_INPUT, EVENT_FACT, RUNTIME_POLICY_REQUIRED,
        CAPACITY, VISUAL_FALLBACK, PROOF_BINDING, SESSION_BUSY
    }

    record Location(Optional<ExactReference> root, Optional<HandlerId> handler,
                    Optional<BlockId> block, String path) {
        static Location at(ExactReference root, String path) {
            return new Location(Optional.ofNullable(root), Optional.empty(), Optional.empty(), path);
        }
    }

    record Entry(Stage stage, Code code, Location location, boolean fatal) {}
    record Report(List<Entry> entries, int omitted, int fatals) {
        Report { entries = List.copyOf(entries); }
        boolean valid() { return fatals == 0; }
    }

    private final List<Entry> entries = new ArrayList<>();
    private int omitted;
    private int fatals;

    void error(Stage stage, Code code, Location location) { add(stage, code, location, true); }
    void warn(Stage stage, Code code, Location location) { add(stage, code, location, false); }
    int fatals() { return fatals; }

    private void add(Stage stage, Code code, Location location, boolean fatal) {
        if (fatal) {
            fatals = Math.incrementExact(fatals);
        }
        if (entries.size() < MAX_COMPILER_DIAGNOSTICS) {
            entries.add(new Entry(stage, code, location, fatal));
        } else {
            omitted = Math.incrementExact(omitted);
        }
    }

    void merge(Report report) {
        for (var entry : report.entries()) {
            if (entries.size() < MAX_COMPILER_DIAGNOSTICS) {
                entries.add(entry);
            } else {
                omitted = Math.incrementExact(omitted);
            }
        }
        omitted = Math.addExact(omitted, report.omitted());
        fatals = Math.addExact(fatals, report.fatals());
    }

    Report report() { return new Report(entries, omitted, fatals); }
}
