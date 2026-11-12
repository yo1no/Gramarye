package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.Code;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerDiagnostics.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.Context;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDProgram.*;

/** Pure, bounded compiler for one captured owner generation. */
final class ScopeDCompiler {
    private static final Semaphore SESSIONS = new Semaphore(MAX_COMPILER_SESSIONS);
    private static final Comparator<ExactReference> ORDER = Comparator
            .comparingInt((ExactReference reference) -> reference instanceof SpellReference ? 0 : 1)
            .thenComparingLong(ScopeDCatalogImage::id).thenComparingLong(ScopeDCatalogImage::revision);

    sealed interface Result permits Compiled, Rejected {}
    record ManifestOrigin(ManifestReference manifest, ManifestEntryId entry, List<ScopeDResolver.EntryBinding> entries,
                          List<SpellReference> wholeClosure) {
        ManifestOrigin {
            entries = List.copyOf(entries);
            wholeClosure = List.copyOf(wholeClosure);
        }
    }
    record Compiled(SpellProgram program, ScopeDResolver.Snapshot snapshot, Optional<ManifestOrigin> manifest,
                    ScopeDResolver.Work resolverWork, Report diagnostics) implements Result {}
    record Rejected(Report diagnostics) implements Result {}

    static Result compile(UUID source, ScopeDCatalogImage image, ScopeDResolver.Root root,
                          CatalogGeneration expected, Context context) throws IOException {
        var diagnostics = new ScopeDCompilerDiagnostics();
        if (!tryEnter()) {
            diagnostics.error(Stage.RESOLVER, Code.SESSION_BUSY, Location.at(root.exact(), "session"));
            return new Rejected(diagnostics.report());
        }
        try {
            var resolver = ScopeDResolver.open(source, image, context.catalogContext());
            return switch (resolver.resolve(root, expected, context.catalogContext())) {
                case ScopeDResolver.Rejected rejected -> {
                    recordRejection(rejected, diagnostics);
                    yield new Rejected(diagnostics.report());
                }
                case ScopeDResolver.Resolved resolved -> compileResolved(resolved, context, diagnostics);
            };
        } catch (ScopeDLowering.Capacity | ArithmeticException failure) {
            diagnostics.error(Stage.LOWERING, Code.CAPACITY, Location.at(root.exact(), "compile"));
            return new Rejected(diagnostics.report());
        } catch (ScopeDGraph.Rejected failure) {
            diagnostics.error(Stage.RESOLVER, graphCode(failure.reason()), Location.at(root.exact(), "graph"));
            return new Rejected(diagnostics.report());
        } finally {
            leave();
        }
    }

    private static Result compileResolved(ScopeDResolver.Resolved resolved, Context context,
                                          ScopeDCompilerDiagnostics diagnostics) throws IOException {
        var selected = resolved.selected();
        var whole = resolved.manifest();
        var lowered = lower(whole.map(ScopeDResolver.ManifestAvailability::documents).orElse(selected.documents()),
                whole.map(ScopeDResolver.ManifestAvailability::bindings).orElse(selected.bindings()), context, diagnostics);
        if (diagnostics.fatals() != 0) {
            return new Rejected(diagnostics.report());
        }
        boolean sameClosure = lowered.units().keySet().equals(selected.documents().keySet());
        var selectedUnits = new LinkedHashMap<SpellReference, SpellUnit>();
        var programDiagnostics = new ScopeDCompilerDiagnostics();
        for (var reference : selected.documents().keySet()) {
            selectedUnits.put(reference, lowered.units().get(reference));
            var report = lowered.reports().get(reference);
            if (report != null) {
                programDiagnostics.merge(report);
            }
        }
        var feedback = ScopeDFeedback.analyze(lowered.units(), context, diagnostics);
        if (diagnostics.fatals() != 0) {
            return new Rejected(diagnostics.report());
        }
        if (!sameClosure) {
            feedback = ScopeDFeedback.analyze(selectedUnits, context, programDiagnostics);
        }
        if (programDiagnostics.fatals() != 0) {
            diagnostics.merge(programDiagnostics.report());
            return new Rejected(diagnostics.report());
        }
        var evidence = new ArrayList<ContentBinding>();
        for (var document : selected.documents().values()) {
            evidence.add(new ContentBinding(document.reference(), ScopeDCatalog.sha256(ScopeDDataCodec.encode(document))));
        }
        var key = new Key(IR_VERSION, selected.root(), evidence, context.identity());
        var units = aggregate(selectedUnits);
        var programReport = programDiagnostics.report();
        int loweringWork = Math.addExact(Math.addExact(units.expressions(), units.operands()), units.sourceEntries());
        var cost = new Cost(selectedUnits.size(), units.createEdges(), selected.cyclicComponents().size(), units,
                feedback.steps(), loweringWork, feedback.scratch(), programReport.entries().size(), programReport.omitted());
        var program = new SpellProgram(key, List.copyOf(selectedUnits.values()), cost, programReport);
        var manifest = whole.map(value -> new ManifestOrigin(value.document().reference(),
                ((ScopeDResolver.ManifestEntryRoot) resolved.root()).entryId(), value.entries(),
                List.copyOf(value.documents().keySet())));
        return new Compiled(program, resolved.snapshot(), manifest, resolved.work(), diagnostics.report());
    }

    /** Full candidate coverage. Only the catalog can turn a successful report into a proof. */
    static Report verifyCandidate(UUID source, ScopeDCatalogImage candidate, Context context) {
        var diagnostics = new ScopeDCompilerDiagnostics();
        if (!tryEnter()) {
            diagnostics.error(Stage.PROOF, Code.SESSION_BUSY, Location.at(null, "session"));
            return diagnostics.report();
        }
        try {
            var resolver = ScopeDResolver.open(source, candidate, context.catalogContext());
            var documents = new LinkedHashMap<SpellReference, SpellDefinition>();
            var bindings = new LinkedHashMap<SpellIdentity, SpellReference>();
            for (var root : candidate.routes().values().stream().sorted(ORDER).toList()) {
                switch (root) {
                    case SpellReference spell -> {
                        var result = resolver.resolve(new ScopeDResolver.StandaloneSpellRoot(spell), candidate.generation(),
                                context.catalogContext());
                        if (result instanceof ScopeDResolver.Rejected rejected) {
                            recordRejection(rejected, diagnostics);
                        } else {
                            var selected = ((ScopeDResolver.Resolved) result).selected();
                            documents.put(spell, selected.documents().get(spell));
                            bindings.put(spell.identity(), spell);
                        }
                    }
                    case ManifestReference manifest -> {
                        var result = resolver.resolveManifest(manifest, candidate.generation(), context.catalogContext());
                        if (result instanceof ScopeDResolver.Rejected rejected) {
                            recordRejection(rejected, diagnostics);
                        }
                    }
                }
            }
            if (diagnostics.fatals() == 0) {
                var lowered = lower(documents, bindings, context, diagnostics);
                if (diagnostics.fatals() == 0) {
                    ScopeDFeedback.analyze(lowered.units(), context, diagnostics);
                }
            }
        } catch (ScopeDLowering.Capacity | ArithmeticException failure) {
            diagnostics.error(Stage.LOWERING, Code.CAPACITY, Location.at(null, "candidate"));
        } catch (ScopeDGraph.Rejected failure) {
            diagnostics.error(Stage.RESOLVER, graphCode(failure.reason()), Location.at(null, "candidate"));
        } finally {
            leave();
        }
        return diagnostics.report();
    }

    private record Lowered(Map<SpellReference, SpellUnit> units, Map<SpellReference, Report> reports) {}

    private static Lowered lower(Map<SpellReference, SpellDefinition> documents,
                                 Map<SpellIdentity, SpellReference> bindings, Context context,
                                 ScopeDCompilerDiagnostics diagnostics) {
        var units = new LinkedHashMap<SpellReference, SpellUnit>();
        var reports = new LinkedHashMap<SpellReference, Report>();
        int retainedBytes = 0;
        for (var entry : documents.entrySet()) {
            var local = new ScopeDCompilerDiagnostics();
            var unit = new ScopeDLowering(entry.getValue(), context, bindings, local).lower();
            if (unit.isPresent()) {
                var value = unit.orElseThrow();
                retainedBytes = Math.addExact(retainedBytes, value.cost().retainedBytes());
                if (retainedBytes > MAX_COMPILER_RETAINED_BYTES) {
                    throw new ScopeDLowering.Capacity();
                }
                units.put(entry.getKey(), value);
            }
            var report = local.report();
            if (!report.entries().isEmpty()) {
                reports.put(entry.getKey(), report);
            }
            diagnostics.merge(report);
        }
        aggregate(units);
        return new Lowered(units, reports);
    }

    private static UnitCost aggregate(Map<SpellReference, SpellUnit> units) {
        int settings = 0, expressions = 0, folded = 0, creationInputs = 0, handlers = 0, blocks = 0;
        int instructions = 0, operands = 0, constants = 0, createEdges = 0, descriptorUnits = 0;
        int sourceEntries = 0, sourceBytes = 0, retainedBytes = 0;
        for (var unit : units.values()) {
            var cost = unit.cost();
            settings = plus(settings, cost.settings(), MAX_COMPILER_SETTINGS);
            expressions = plus(expressions, cost.expressions(), MAX_COMPILER_EXPRESSION_VISITS);
            folded = plus(folded, cost.folded(), MAX_COMPILER_EXPRESSION_VISITS);
            creationInputs = plus(creationInputs, cost.creationInputs(), MAX_COMPILER_CREATION_INPUTS);
            handlers = plus(handlers, cost.handlers(), MAX_COMPILER_HANDLERS);
            blocks = plus(blocks, cost.blocks(), MAX_COMPILER_BLOCKS);
            instructions = plus(instructions, cost.instructions(), MAX_COMPILER_INSTRUCTIONS);
            operands = plus(operands, cost.operands(), MAX_COMPILER_OPERANDS);
            constants = plus(constants, cost.constants(), MAX_COMPILER_CONSTANTS);
            createEdges = plus(createEdges, cost.createEdges(), MAX_GRAPH_EDGES);
            descriptorUnits = plus(descriptorUnits, cost.descriptorUnits(), MAX_COMPILER_DESCRIPTOR_UNITS);
            sourceEntries = plus(sourceEntries, cost.sourceEntries(), MAX_COMPILER_SOURCE_ENTRIES);
            sourceBytes = plus(sourceBytes, cost.sourceBytes(), MAX_COMPILER_SOURCE_BYTES);
            retainedBytes = plus(retainedBytes, cost.retainedBytes(), MAX_COMPILER_RETAINED_BYTES);
        }
        return new UnitCost(settings, expressions, folded, creationInputs, handlers, blocks, instructions, operands,
                constants, createEdges, descriptorUnits, sourceEntries, sourceBytes, retainedBytes);
    }

    private static int plus(int total, int added, int limit) {
        int sum = Math.addExact(total, added);
        if (sum > limit) {
            throw new ScopeDLowering.Capacity();
        }
        return sum;
    }

    private static void recordRejection(ScopeDResolver.Rejected rejected, ScopeDCompilerDiagnostics diagnostics) {
        var stage = rejected.root() instanceof ManifestReference ? Stage.MANIFEST : Stage.RESOLVER;
        var root = rejected.root();
        if (rejected.issues().isPresent()) {
            var structural = rejected.issues().orElseThrow();
            for (var issue : structural.validation().issues()) {
                var location = Location.at(structural.document(), issue.location());
                switch (issue.code()) {
                    case UNKNOWN_VISUAL_FALLBACK -> diagnostics.warn(stage, Code.VISUAL_FALLBACK, location);
                    case UNKNOWN_GAMEPLAY -> diagnostics.error(stage, Code.UNKNOWN_GAMEPLAY, location);
                    case MISSING_REQUIRED -> diagnostics.error(stage, Code.MISSING_REQUIRED, location);
                    case HOOK_ONLY -> diagnostics.error(stage, Code.HOOK_ONLY, location);
                    case UNSUPPORTED_DESCRIPTOR -> diagnostics.error(stage, Code.DESCRIPTOR, location);
                    case UNSUPPORTED_CAPABILITY -> diagnostics.error(stage, Code.CAPABILITY, location);
                    case FOREIGN_OWNER -> diagnostics.error(stage, Code.FOREIGN_OWNER, location);
                    default -> diagnostics.error(stage, Code.ROOT_UNAVAILABLE, location);
                }
            }
            if (structural.validation().truncated()) {
                diagnostics.error(stage, Code.ROOT_UNAVAILABLE, Location.at(structural.document(), "structural_issues_truncated"));
            }
            return;
        }
        Code code = switch (rejected.reason()) {
            case STALE_GENERATION -> Code.STALE_GENERATION;
            case CONTEXT_MISMATCH -> Code.STALE_CONTEXT;
            case ROOT_LIMIT, WORK_LIMIT, BINDING_LIMIT -> Code.CAPACITY;
            case ENTRY_UNAVAILABLE -> Code.ROOT_UNAVAILABLE;
            case STRUCTURE -> rejected.graphReason().map(ScopeDCompiler::graphCode).orElse(Code.ROOT_UNAVAILABLE);
        };
        diagnostics.error(stage, code, Location.at(root, "root"));
    }

    private static Code graphCode(ScopeDGraph.Reason reason) {
        return switch (reason) {
            case FOREIGN_SCOPE -> Code.FOREIGN_OWNER;
            case MISSING_DEPENDENCY -> Code.MISSING_DEPENDENCY;
            case ROOT_LIMIT, ROUTE_LIMIT, EDGE_LIMIT, WORK_LIMIT, BATCH_LIMIT -> Code.CAPACITY;
            default -> Code.ROOT_UNAVAILABLE;
        };
    }

    static boolean tryEnter() { return SESSIONS.tryAcquire(); }

    static void leave() { SESSIONS.release(); }

    private ScopeDCompiler() {}
}
