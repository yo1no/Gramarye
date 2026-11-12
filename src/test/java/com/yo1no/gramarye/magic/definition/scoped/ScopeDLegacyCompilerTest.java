package com.yo1no.gramarye.magic.definition.scoped;

import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.yo1no.gramarye.ScopeDLegacyProductionFixtures;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.action.type.ActionPayloadInspector;
import com.yo1no.gramarye.magic.action.type.ActionType;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.capability.AppearanceParameterPolicy;
import com.yo1no.gramarye.magic.capability.ControlClass;
import com.yo1no.gramarye.magic.capability.SourceRequirement;
import com.yo1no.gramarye.magic.capability.TargetRequirement;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.NodeDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReader;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentWriter;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.ActionReferenceProjection;
import com.yo1no.gramarye.magic.definition.inspection.NodeReference;
import com.yo1no.gramarye.magic.definition.inspection.PayloadInspectionResult;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.validation.ValidationCollector;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationIssueMetadata;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLegacyCompiler.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeDLegacyCompilerTest {
    private static final Context DEFAULT = new Context(MagicPolicyLimits.DEFAULTS, Map.of());
    private static final ResourceLocation FIXTURE_ACTION = id("fixture_compiler_action");

    @TempDir Path directory;

    @Test
    void canonicalStarterKeepsExactBytesTypedPayloadAndInspectedOrder() {
        var document = ScopeDLegacyProductionFixtures.starter();
        var input = encoded(document);
        var before = input.copyBytes();
        var accepted = success(compile(exact(document), input, DEFAULT, ScopeDLegacyProductionFixtures.resolver()));
        var freshDocument = ScopeDLegacyProductionFixtures.starter();
        var rebuilt = success(compile(freshDocument, DEFAULT));

        assertEquals(accepted, rebuilt);
        assertArrayEquals(before, input.copyBytes());
        assertArrayEquals(before, encoded(freshDocument).copyBytes());
        assertEquals(document, freshDocument);
        assertEquals(exact(document), accepted.key().reference());
        assertEquals(input.byteCount(), accepted.key().source().bytes());
        assertEquals(ScopeDCatalog.sha256(before), accepted.key().source().sha256());
        assertEquals(ScopeDProgram.IR_VERSION, accepted.key().irVersion());
        var nativeProjection = ScopeDLegacyProductionFixtures.validated(document);
        assertNotNull(nativeProjection);

        var nodes = accepted.key().nodes();
        assertEquals(List.of(0, 1), nodes.stream().map(Node::index).toList());
        assertEquals(List.of(new Field("cooldown_ticks", new IntValue(0))), nodes.get(0).trigger().fields());
        assertEquals(0, nodes.get(0).trigger().sourceSchema());
        assertEquals(1, nodes.get(0).trigger().resolvedSchema());
        assertEquals(id("active_cast"), nodes.get(0).trigger().id());
        assertEquals(nativeProjection.nodes().get(0).trigger().descriptor().capabilities(),
                nodes.get(0).trigger().capabilities());
        assertEquals(List.of(new Field("mana_cost", new LongValue(0)), new Field("profile_code", new IntValue(0))),
                nodes.get(0).action().fields());
        assertEquals(List.of(new Field("include_derived", new BooleanValue(true)),
                        new Field("source_node_index", new IntValue(0)), new Field("source_output_ordinal", new IntValue(0))),
                nodes.get(1).trigger().fields());
        assertEquals(List.of(new Field("magnitude", new LongValue(4000)), new Field("mana_cost", new LongValue(0))),
                nodes.get(1).action().fields());
        assertEquals(0, nodes.get(1).action().sourceSchema());
        assertEquals(1, nodes.get(1).action().resolvedSchema());
        assertEquals(nativeProjection.nodes().get(1).references(), nodes.get(1).references());
        assertEquals(Set.of(ActionOutputKind.PROJECTILE), nodes.get(0).references().action().producedOutputs());
        assertEquals(TargetSelection.CURRENT_TARGET, nodes.get(1).references().action().targetSelection());
        assertEquals(RuntimeNeutralAppearance.Default.INSTANCE, accepted.key().appearance());

        assertEquals(1, accepted.cost().references());
        assertEquals(8, accepted.cost().payloadFields());
        assertEquals(16, accepted.cost().sourceMapEntries());
        assertEquals(IntStream.range(0, 16).boxed().toList(),
                accepted.sourceMap().stream().map(Address::offset).toList());
        var ref = accepted.sourceMap().stream().filter(address -> address.role().isPresent()).findFirst().orElseThrow();
        assertEquals(new Address(exact(document), 1, Side.TRIGGER, Optional.empty(),
                0, Optional.of(ReferenceRole.SOURCE), 11, 1), ref);
        assertEquals(new Address(exact(document), 1, Side.ACTION, Optional.of("magnitude"),
                -1, Optional.empty(), 13, 1), accepted.sourceMap().get(13));
        assertEquals(5, accepted.sourceMap().get(7).length());
        assertThrows(UnsupportedOperationException.class, nodes::clear);
        assertThrows(UnsupportedOperationException.class, accepted.sourceMap()::clear);
    }

    @Test
    void productionTemplateAndLkgKeepExistingStarterRuntimeAndInvalidClassification() throws Exception {
        var a = ScopeDLegacyProductionFixtures.template(0, 0, 0, 0, 4000);
        var b = ScopeDLegacyProductionFixtures.template(1, 1, 0, 1, 5000);
        var runtimeOnly = ScopeDLegacyProductionFixtures.template(2, 1, 120, 1, 5000);
        var invalid = ScopeDLegacyProductionFixtures.template(3, 1, 0, 1, 4500);
        var guardedLegacyInvalid = ScopeDLegacyProductionFixtures.template(4, 0, 0, 0, 5000);
        assertEquals("ACCEPTED", a.classification());
        assertEquals("ACCEPTED", b.classification());
        assertEquals("SEMANTIC_INVALID", runtimeOnly.classification());
        assertEquals("SEMANTIC_INVALID", invalid.classification());
        assertEquals("MIGRATION_FAILED", guardedLegacyInvalid.classification());
        assertTrue(a.runtimeProfile());
        assertTrue(b.runtimeProfile());
        assertTrue(runtimeOnly.runtimeProfile());
        assertFalse(invalid.runtimeProfile());
        assertFalse(guardedLegacyInvalid.runtimeProfile());

        var current = success(compile(a.document(), DEFAULT));
        var next = success(compile(b.document(), DEFAULT));
        assertNotEquals(current.key(), next.key());
        assertEquals(List.of(new Field("magnitude", new LongValue(5000)), new Field("mana_cost", new LongValue(0))),
                next.key().nodes().get(1).action().fields());
        assertEquals(List.of(new Field("cooldown_ticks", new IntValue(120))),
                success(compile(runtimeOnly.document(), DEFAULT)).key().nodes().getFirst().trigger().fields());

        var transition = ScopeDLegacyProductionFixtures.retainLkg(a, invalid);
        var retainedBytes = encoded(transition.retained()).copyBytes();
        assertEquals(current, success(compile(transition.retained(), DEFAULT)));
        assertArrayEquals(encoded(a.document()).copyBytes(), retainedBytes);
        assertArrayEquals(retainedBytes, encoded(transition.retained()).copyBytes());
        var rejectedBytes = encoded(transition.rejected()).copyBytes();
        assertFailure(Failure.VALIDATION, compile(transition.rejected(), DEFAULT));
        assertArrayEquals(rejectedBytes, encoded(transition.rejected()).copyBytes());
        assertFailure(Failure.VALIDATION, compile(guardedLegacyInvalid.document(), DEFAULT));
    }

    @Test
    void rejectsWrongExactReferenceUnknownDescriptorBadReferenceAndMalformedPhysicalInput() {
        var document = ScopeDLegacyProductionFixtures.starter();
        var wrong = new SkillReference(document.skillId(), new SkillRevision(13));
        assertFailure(Failure.EXACT_REFERENCE, compile(wrong, encoded(document), DEFAULT,
                ScopeDLegacyProductionFixtures.resolver()));
        assertFailure(Failure.DOCUMENT_REJECTED, compile(exact(document),
                EncodedSkillDocument.copyOf(new byte[] {1, 2, 3}), DEFAULT, ScopeDLegacyProductionFixtures.resolver()));

        var node = document.nodes().get(1);
        var missing = new NodeDocument(node.trigger(), envelope(id("unresolved_compiler_test")), node.appearanceOverride());
        var unknown = assertFailure(Failure.VALIDATION,
                compile(replace(document, 1, missing), DEFAULT));
        assertTrue(unknown.findings().stream().anyMatch(finding -> finding.path().render().startsWith("nodes[1].action")));

        var invalidReference = copyJson(document);
        invalidReference.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("trigger")
                .getAsJsonObject("payload").addProperty("source_node_index", 1);
        var bad = assertFailure(Failure.VALIDATION, compile(read(invalidReference), DEFAULT));
        assertTrue(bad.findings().stream().anyMatch(finding -> finding.path().render().startsWith("nodes[1].trigger")));
    }

    @Test
    void typedAndUnparsedAppearanceStayRawFreeWithImmutableProfileContext() {
        var source = copyJson(ScopeDLegacyProductionFixtures.starter());
        var top = new JsonObject();
        top.addProperty("intensity_milli", 200);
        var specified = new JsonObject();
        specified.addProperty("mode", "specified");
        specified.addProperty("id", "gramarye:legacy_fixture_sound");
        top.add("sound_profile", specified);
        source.add("appearance", top);
        var unsupportedVisual = new JsonObject();
        unsupportedVisual.addProperty("intensity_milli", "opaque visual material");
        source.getAsJsonArray("nodes").get(1).getAsJsonObject().add("appearance_override", unsupportedVisual);
        var document = read(source);
        var canonicalBefore = encoded(document).copyBytes();
        var program = success(compile(document, DEFAULT));

        assertInstanceOf(RuntimeNeutralAppearance.Typed.class, program.key().appearance());
        assertInstanceOf(RuntimeNeutralAppearanceOverride.Fallback.class, program.key().nodes().get(1).appearance());
        assertTrue(program.diagnostics().findings().stream().anyMatch(finding -> finding.code().toString()
                .contains("appearance_unparsed_fallback") || finding.path().render().contains("appearance_override")));
        assertFalse(program.toString().contains("opaque visual material"));
        assertArrayEquals(canonicalBefore, encoded(document).copyBytes());
        unsupportedVisual.addProperty("intensity_milli", "different opaque visual material");
        var other = success(compile(read(source), DEFAULT));
        assertEquals(program.key().reference(), other.key().reference());
        assertEquals(program.key().nodes(), other.key().nodes());
        assertNotEquals(program.key().source(), other.key().source());
        var sound = new ProfileKey(AppearanceField.SOUND_PROFILE, id("legacy_fixture_sound"));
        var unavailable = success(compile(document, new Context(MagicPolicyLimits.DEFAULTS,
                Map.of(sound, ProfileAvailability.MISSING))));
        assertNotEquals(program.key(), unavailable.key());
        assertEquals(program.key().appearance(), unavailable.key().appearance());
        assertTrue(unavailable.diagnostics().findings().stream()
                .anyMatch(finding -> finding.path().render().equals("appearance.sound_profile")));
        var changedPolicy = new Context(policy(2), Map.of());
        assertNotEquals(program.key(), success(compile(document, changedPolicy)).key());
    }

    @Test
    void boundedDiagnosticsKeepFatalScalarAfterWarningFlood() {
        var source = ScopeDLegacyProductionFixtures.starter();
        var first = source.nodes().getFirst();
        var withWarnings = replace(source, 0, new NodeDocument(first.trigger(), envelope(FIXTURE_ACTION),
                first.appearanceOverride()));
        var raw = copyJson(withWarnings);
        var damage = raw.getAsJsonArray("nodes").get(1).getAsJsonObject().getAsJsonObject("action");
        damage.addProperty("schema_version", 1);
        damage.getAsJsonObject("payload").addProperty("magnitude", 4500);
        var withLateFatal = read(raw);
        var action = new FixtureAction(0, List.of(), true);
        var resolver = ScopeDLegacyProductionFixtures.resolver(Map.of(), Map.of(FIXTURE_ACTION, action));
        var report = assertFailure(Failure.VALIDATION, compile(exact(withLateFatal), encoded(withLateFatal), DEFAULT, resolver));
        assertEquals(MAX_COMPILER_DIAGNOSTICS, report.findings().size());
        assertTrue(report.omitted() > 0);
        assertTrue(report.findings().stream().noneMatch(finding -> finding.path().render().contains("nodes[1]")));
        assertTrue(report.fatal());
    }

    @Test
    void nodeReferencePayloadProfileAndMaterialCeilingsRejectBeforeReturningProgram() {
        var max = maxNodes();
        var context = new Context(policy(MAX_LEGACY_NODES), Map.of());
        var valid = success(compile(max, context));
        assertEquals(MAX_LEGACY_NODES, valid.cost().nodes());
        assertEquals(MAX_LEGACY_REFERENCES, valid.cost().references());
        assertEquals(1 + 3 * MAX_LEGACY_NODES + MAX_LEGACY_REFERENCES + valid.cost().payloadFields(),
                valid.cost().sourceMapEntries());
        assertTrue(valid.cost().sourceMapEntries() <= MAX_LEGACY_SOURCE_MAP_ENTRIES);
        assertEquals(MAX_LEGACY_PAYLOAD_FIELDS, valid.key().nodes().getLast().trigger().fields().size());
        var oversized = new ArrayList<>(max.nodes());
        oversized.add(max.nodes().getLast());
        assertThrows(IllegalArgumentException.class, () -> new SkillDocument(max.schemaVersion(),
                max.skillId(), max.revision(), oversized, max.appearance()));

        var old = max.nodes().getLast();
        var extra = replace(max, MAX_LEGACY_NODES - 1, new NodeDocument(old.trigger(),
                envelope(FIXTURE_ACTION), old.appearanceOverride()));
        var reference = new NodeReference(0, ReferenceRole.SOURCE, ValidationPath.empty().field("source"),
                Optional.of(ActionOutputKind.PROJECTILE));
        var withOneExtra = new FixtureAction(0, List.of(reference), false);
        var refResolver = ScopeDLegacyProductionFixtures.resolver(Map.of(), Map.of(FIXTURE_ACTION, withOneExtra));
        assertFailure(Failure.CAPACITY, compile(exact(extra), encoded(extra), context, refResolver));
        var withExtraField = new FixtureAction(MAX_LEGACY_PAYLOAD_FIELDS + 1, List.of(), false);
        var fieldResolver = ScopeDLegacyProductionFixtures.resolver(Map.of(), Map.of(FIXTURE_ACTION, withExtraField));
        assertFailure(Failure.CAPACITY, compile(exact(extra), encoded(extra), context, fieldResolver));

        var profiles = profiles(16, MAX_LEGACY_PROFILE_FACTS);
        var maxProfiles = new Context(policy(MAX_LEGACY_NODES), profiles);
        profiles.clear();
        assertEquals(MAX_LEGACY_PROFILE_FACTS, maxProfiles.profiles().size());
        assertInstanceOf(Success.class, compile(max, maxProfiles));
        assertThrows(IllegalArgumentException.class,
                () -> new Context(MagicPolicyLimits.DEFAULTS, profiles(16, MAX_LEGACY_PROFILE_FACTS + 1)));
        verifyProjectedByteBoundary(max, context);
    }

    @Test
    void fixedHeapChildCompilesLegacyNodeAndReferenceMaximaInAnIsolatedDirectory() throws Exception {
        var log = directory.resolve("legacy-heap.log");
        var classpath = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .map(Path::of).map(Path::toAbsolutePath).map(Path::toString)
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms32m", "-Xmx32m", "-XX:+ExitOnOutOfMemoryError", "-cp", classpath,
                Child.class.getName()).directory(directory.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!child.waitFor(60, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            fail("Legacy fixed-heap child timed out");
        }
        var output = Files.readString(log);
        assertEquals(0, child.exitValue(), output);
        assertTrue(output.contains("cold nodes=256 refs=255"), output);
        assertTrue(output.contains("rebuild nodes=256 refs=255"), output);
        assertTrue(output.contains("legacy material accepted=2097152 rejected=2097153"), output);
        System.out.print(output);
    }

    public static final class Child {
        public static void main(String[] arguments) {
            var context = new Context(policy(MAX_LEGACY_NODES), profiles(16, MAX_LEGACY_PROFILE_FACTS));
            LegacyProgram baseline = null;
            for (var mode : List.of("cold", "rebuild")) {
                var start = System.nanoTime();
                var document = maxNodes();
                var program = success(compile(document, context));
                assertEquals(MAX_LEGACY_NODES, program.cost().nodes());
                assertEquals(MAX_LEGACY_REFERENCES, program.cost().references());
                if (baseline != null) {
                    assertEquals(baseline, program);
                }
                baseline = program;
                System.out.printf("%s nodes=%d refs=%d projectedBytes=%d elapsedNanos=%d maxHeap=%d%n", mode,
                        program.cost().nodes(), program.cost().references(), program.cost().projectedBytes(),
                        System.nanoTime() - start, Runtime.getRuntime().maxMemory());
            }
            verifyProjectedByteBoundary(maxNodes(), new Context(policy(MAX_LEGACY_NODES), Map.of()));
            System.out.printf("legacy material accepted=%d rejected=%d%n",
                    MAX_LEGACY_PROJECTED_BYTES, MAX_LEGACY_PROJECTED_BYTES + 1);
        }
    }

    private static void verifyProjectedByteBoundary(SkillDocument document, Context empty) {
        var even = success(compile(document, empty)).cost().projectedBytes();
        var exact = new Context(empty.policy(), profilesForMaterial(MAX_LEGACY_PROJECTED_BYTES - even));
        assertEquals(MAX_LEGACY_PROJECTED_BYTES, success(compile(document, exact)).cost().projectedBytes());

        var last = document.nodes().getLast();
        var alternate = replace(document, document.nodes().size() - 1,
                new NodeDocument(last.trigger(), envelope(FIXTURE_ACTION), last.appearanceOverride()));
        var resolver = ScopeDLegacyProductionFixtures.resolver(Map.of(),
                Map.of(FIXTURE_ACTION, new FixtureAction(1, List.of(), false)));
        var input = encoded(alternate);
        var odd = success(compile(exact(alternate), input, empty, resolver)).cost().projectedBytes();
        var oneOver = new Context(empty.policy(), profilesForMaterial(MAX_LEGACY_PROJECTED_BYTES + 1 - odd));
        assertFailure(Failure.CAPACITY, compile(exact(alternate), input, oneOver, resolver));
    }

    private static Map<ProfileKey, ProfileAvailability> profilesForMaterial(int material) {
        // The actual ResourceLocation is ASCII. The compiler charges the conservative
        // symbol width of four bytes per character, plus its fixed record overhead.
        int fixed = LEGACY_PROFILE_FACT_BYTES + Integer.BYTES;
        assertEquals(0, material % 2);
        int maximumLength = com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_STRING_LENGTH;
        int maximumFact = fixed + 4 * maximumLength;
        int count = (material + maximumFact - 1) / maximumFact;
        while ((material - fixed * count) % 4 != 0) {
            count++;
        }
        assertTrue(count <= MAX_LEGACY_PROFILE_FACTS);
        int remainingLength = (material - fixed * count) / 4;
        var result = new LinkedHashMap<ProfileKey, ProfileAvailability>();
        for (int index = 0; index < count; index++) {
            var base = "profile_" + index;
            int prefixLength = "gramarye:".length() + base.length();
            int totalLength = remainingLength / (count - index);
            assertTrue(totalLength >= prefixLength && totalLength <= maximumLength);
            remainingLength -= totalLength;
            result.put(new ProfileKey(AppearanceField.SOUND_PROFILE,
                    id(base + "x".repeat(totalLength - prefixLength))), ProfileAvailability.AVAILABLE);
        }
        assertEquals(0, remainingLength);
        return result;
    }

    private static SkillDocument maxNodes() {
        var starter = ScopeDLegacyProductionFixtures.starter();
        var nodes = new ArrayList<NodeDocument>();
        nodes.add(starter.nodes().getFirst());
        for (int index = 1; index < MAX_LEGACY_NODES; index++) {
            nodes.add(starter.nodes().getLast());
        }
        return new SkillDocument(starter.schemaVersion(), starter.skillId(), starter.revision(), nodes, starter.appearance());
    }

    private static Map<ProfileKey, ProfileAvailability> profiles(int padding, int count) {
        var result = new LinkedHashMap<ProfileKey, ProfileAvailability>();
        for (int index = 0; index < count; index++) {
            result.put(new ProfileKey(AppearanceField.SOUND_PROFILE, id("profile_" + index + "a".repeat(padding))),
                    ProfileAvailability.AVAILABLE);
        }
        return result;
    }

    private static MagicPolicyLimits policy(int maxNodes) {
        var p = MagicPolicyLimits.DEFAULTS;
        return new MagicPolicyLimits(maxNodes, p.maxStringLength(), p.maxRawPayloadBytes(), p.maxRuntimeTags(),
                p.maxVisitedTargets(), p.maxAppearanceIntensity(), p.maxUnparsedAppearanceDepth(),
                p.maxUnparsedAppearanceNodes(), p.maxSkillDocumentDepth(), p.maxSkillDocumentBytes(),
                p.maxSkillDocumentTreeNodes());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye", path);
    }

    private static SkillDocument replace(SkillDocument document, int index, NodeDocument replacement) {
        var nodes = new ArrayList<>(document.nodes());
        nodes.set(index, replacement);
        return new SkillDocument(document.schemaVersion(), document.skillId(), document.revision(), nodes, document.appearance());
    }

    private static DefinitionEnvelope envelope(ResourceLocation type) {
        return new DefinitionEnvelope(type, 0, new Dynamic<>(JsonOps.INSTANCE, new JsonObject()));
    }

    private static JsonObject copyJson(SkillDocument document) {
        return SkillDocumentWriter.write(document, JsonOps.INSTANCE).getOrThrow().getAsJsonObject();
    }

    private static SkillDocument read(JsonObject document) {
        return SkillDocumentReader.read(new Dynamic<>(JsonOps.INSTANCE, document)).getOrThrow().document();
    }

    private static SkillReference exact(SkillDocument document) {
        return new SkillReference(document.skillId(), document.revision());
    }

    private static EncodedSkillDocument encoded(SkillDocument document) {
        return assertInstanceOf(SkillDocumentStorePersistenceFacade.Encoded.class,
                SkillDocumentStorePersistenceFacade.encodeCurrent(document)).document();
    }

    private static Result compile(SkillDocument document, Context context) {
        return compile(exact(document), encoded(document), context, ScopeDLegacyProductionFixtures.resolver());
    }

    private static Result compile(SkillReference reference, EncodedSkillDocument bytes, Context context,
            SkillCandidateResolver resolver) {
        return ScopeDLegacyCompiler.compile(reference, bytes, Optional.empty(), resolver, context);
    }

    private static LegacyProgram success(Result result) {
        return assertInstanceOf(Success.class, result).program();
    }

    private static Diagnostics assertFailure(Failure expected, Result result) {
        var report = assertInstanceOf(Rejected.class, result).diagnostics();
        assertEquals(Optional.of(expected), report.failure(), () -> report.toString());
        assertTrue(report.fatal());
        return report;
    }

    private enum FixturePayload implements ActionPayload { INSTANCE }

    private record FixtureAction(int encodedFieldCount, List<NodeReference> references, boolean warnings)
            implements ActionType<FixturePayload> {
        @Override
        public int currentPayloadSchemaVersion() {
            return 0;
        }

        @Override
        public MapCodec<FixturePayload> payloadCodec() {
            return new MapCodec<>() {
                @Override
                public <T> DataResult<FixturePayload> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return DataResult.success(FixturePayload.INSTANCE);
                }

                @Override
                public <T> RecordBuilder<T> encode(FixturePayload value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    for (int index = 0; index < encodedFieldCount; index++) {
                        prefix.add("value_" + index, ops.createInt(index));
                    }
                    return prefix;
                }

                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return IntStream.range(0, encodedFieldCount).mapToObj(index -> ops.createString("value_" + index));
                }
            };
        }

        @Override
        public ActionCapabilities capabilities() {
            return new ActionCapabilities(references.isEmpty() ? SourceRequirement.NONE : SourceRequirement.PRIOR_NODE,
                    TargetRequirement.NONE, false, Set.of(), false, false, false, false, false,
                    false, false, ControlClass.NONE, AppearanceParameterPolicy.none());
        }

        @Override
        public Optional<ActionPayloadInspector<FixturePayload>> payloadInspector() {
            return Optional.of(value -> new PayloadInspectionResult.Success<>(new ActionReferenceProjection(
                    references.isEmpty() ? SourceSelection.NONE : SourceSelection.PRIOR_NODE,
                    TargetSelection.NONE, references, Set.of())));
        }

        @Override
        public ValidationResult validate(FixturePayload payload, ValidationContext context) {
            var collector = new ValidationCollector();
            if (warnings) {
                var code = new ValidationIssueCode(id("fixture_compiler_warning"));
                for (int index = 0; index <= MAX_COMPILER_DIAGNOSTICS; index++) {
                    collector.add(new ValidationIssue(code, ValidationSeverity.WARNING,
                            ValidationPath.empty().field("warn").index(index), ValidationIssueMetadata.none()));
                }
            }
            return collector.result();
        }
    }
}
