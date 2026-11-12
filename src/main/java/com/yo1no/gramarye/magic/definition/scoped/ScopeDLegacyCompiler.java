package com.yo1no.gramarye.magic.definition.scoped;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.yo1no.gramarye.magic.action.type.ActionPayload;
import com.yo1no.gramarye.magic.capability.ActionCapabilities;
import com.yo1no.gramarye.magic.capability.TriggerCapabilities;
import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentReadReport;
import com.yo1no.gramarye.magic.definition.document.SkillDocumentStorePersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.inspection.NodeProjectionResolver;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import com.yo1no.gramarye.magic.definition.migration.SkillCandidateResolver;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearance;
import com.yo1no.gramarye.magic.definition.validation.RuntimeNeutralAppearanceOverride;
import com.yo1no.gramarye.magic.definition.validation.SkillDefinitionProjector;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationAnalyzer;
import com.yo1no.gramarye.magic.definition.validation.SkillValidationOutcome;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeReference;
import com.yo1no.gramarye.magic.definition.validation.ValidatedNodeReferenceProjection;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.trigger.type.TriggerPayload;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import com.yo1no.gramarye.magic.validation.ValidationIssueCode;
import com.yo1no.gramarye.magic.validation.ValidationPath;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import com.yo1no.gramarye.magic.validation.ValidationSeverity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static com.yo1no.gramarye.magic.limits.MagicSafetyCeilings.MAX_STRING_LENGTH;

/** Projects one exact, legacy P4 document through the existing P3 validation gate. */
final class ScopeDLegacyCompiler {
    private static final SkillDocumentReadReport NO_READ_FACTS =
            new SkillDocumentReadReport(List.of(), false);

    private ScopeDLegacyCompiler() {}

    static synchronized Result compile(SkillReference expected, EncodedSkillDocument bytes,
            Optional<HolderLookup.Provider> provider, SkillCandidateResolver resolver,
            Context context) {
        if (!ScopeDCompiler.tryEnter()) {
            return failure(Failure.SESSION_BUSY, ValidationPath.empty(), ValidationResult.valid());
        }
        try {
            return compileExact(expected, bytes, provider, resolver, context);
        } finally {
            ScopeDCompiler.leave();
        }
    }

    private static Result compileExact(SkillReference expected, EncodedSkillDocument bytes,
            Optional<HolderLookup.Provider> provider, SkillCandidateResolver resolver, Context context) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(resolver);
        Objects.requireNonNull(context);
        var loaded = SkillDocumentStorePersistenceFacade.load(bytes, provider);
        if (!(loaded instanceof SkillDocumentStorePersistenceFacade.Loaded success)) {
            return failure(Failure.DOCUMENT_REJECTED, ValidationPath.empty(), ValidationResult.valid());
        }
        var document = success.document();
        if (!expected.equals(new SkillReference(document.skillId(), document.revision()))) {
            return failure(Failure.EXACT_REFERENCE, ValidationPath.empty(), ValidationResult.valid());
        }
        if (document.nodes().size() > MAX_LEGACY_NODES) {
            return failure(Failure.CAPACITY, ValidationPath.empty().field("nodes"), ValidationResult.valid());
        }

        var candidate = resolver.resolve(document, NO_READ_FACTS);
        var analyzer = new SkillValidationAnalyzer(new NodeProjectionResolver(), context::availability);
        var analysis = analyzer.analyze(candidate, new ValidationContext(context.policy()));
        var projected = new SkillDefinitionProjector().project(analysis);
        if (!(projected instanceof SkillValidationOutcome.Accepted accepted)) {
            return failure(Failure.VALIDATION, ValidationPath.empty(), projected.report());
        }
        var definition = accepted.definition();
        if (!expected.equals(definition.reference())) {
            return failure(Failure.EXACT_REFERENCE, ValidationPath.empty(), accepted.report());
        }

        try {
            var projection = new Projection(expected);
            projection.addAppearance(definition.appearance());
            for (var node : definition.nodes()) {
                var source = document.nodes().get(node.nodeIndex());
                var trigger = triggerBinding(source.trigger(), node.trigger(), nodePath(node.nodeIndex(), "trigger"));
                var action = actionBinding(source.action(), node.action(), nodePath(node.nodeIndex(), "action"));
                projection.add(new Node(node.nodeIndex(), trigger, action, node.references(), node.appearanceOverride()));
            }
            var diagnostics = diagnostics(Optional.empty(), ValidationPath.empty(), accepted.report());
            var cost = projection.cost(context, diagnostics);
            var evidence = new SourceEvidence(bytes.byteCount(), ScopeDCatalog.sha256(bytes.copyBytes()));
            var key = new LegacyKey(ScopeDProgram.IR_VERSION, ScopeDFrontend.LEGACY_FRONTEND_ABI, expected, evidence,
                    context, definition.appearance(), projection.nodes);
            return new Success(new LegacyProgram(key, projection.addresses, cost, diagnostics));
        } catch (CannotProject rejected) {
            return failure(rejected.failure, rejected.path, accepted.report());
        }
    }

    private static <P extends TriggerPayload> TriggerBinding triggerBinding(
            DefinitionEnvelope source, ResolvedTriggerDefinition<P> trigger, ValidationPath path) {
        var descriptor = trigger.descriptor();
        return new TriggerBinding(source.typeId(), source.schemaVersion(), trigger.schemaVersion(),
                descriptor.capabilities(), fields(descriptor.payloadCodec(), trigger.payload(), path));
    }

    private static <P extends ActionPayload> ActionBinding actionBinding(
            DefinitionEnvelope source, ResolvedActionDefinition<P> action, ValidationPath path) {
        var descriptor = action.descriptor();
        return new ActionBinding(source.typeId(), source.schemaVersion(), action.schemaVersion(),
                descriptor.capabilities(), fields(descriptor.payloadCodec(), action.payload(), path));
    }

    private static <P> List<Field> fields(MapCodec<P> codec, P payload, ValidationPath path) {
        var encoded = codec.codec().encodeStart(JsonOps.INSTANCE, payload).result();
        if (encoded.isEmpty() || !(encoded.orElseThrow() instanceof JsonObject object)) {
            throw new CannotProject(Failure.PAYLOAD_SHAPE, path);
        }
        if (object.size() > MAX_LEGACY_PAYLOAD_FIELDS) {
            throw new CannotProject(Failure.CAPACITY, path);
        }
        var fields = new ArrayList<Field>(object.size());
        for (var name : object.keySet().stream().sorted().toList()) {
            var element = object.get(name);
            requireLength(name, path);
            if (!element.isJsonPrimitive()) {
                throw new CannotProject(Failure.PAYLOAD_SHAPE, path);
            }
            var primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                fields.add(new Field(name, new BooleanValue(primitive.getAsBoolean())));
            } else if (primitive.isNumber()) {
                var value = switch (primitive.getAsNumber()) {
                    case Integer integer -> new IntValue(integer);
                    case Long longValue -> new LongValue(longValue);
                    default -> throw new CannotProject(Failure.PAYLOAD_SHAPE, path);
                };
                fields.add(new Field(name, value));
            } else {
                throw new CannotProject(Failure.PAYLOAD_SHAPE, path);
            }
        }
        return List.copyOf(fields);
    }

    private static void requireLength(String text, ValidationPath path) {
        if (text.length() > MAX_STRING_LENGTH) {
            throw new CannotProject(Failure.CAPACITY, path);
        }
    }

    private static ValidationPath nodePath(int node, String side) {
        return ValidationPath.empty().field("nodes").index(node).field(side);
    }

    private static Rejected failure(Failure reason, ValidationPath path, ValidationResult report) {
        return new Rejected(diagnostics(Optional.of(reason), path, report));
    }

    private static Diagnostics diagnostics(Optional<Failure> failure, ValidationPath path,
            ValidationResult report) {
        var retained = report.issues().stream().limit(MAX_COMPILER_DIAGNOSTICS)
                .map(issue -> new Finding(issue.code(), issue.severity(), issue.path())).toList();
        return new Diagnostics(failure, path, retained, report.issues().size() - retained.size(),
                report.truncated(), report.hasErrors() || failure.isPresent());
    }

    /** The profile input is a bounded pure snapshot for the address being compiled. */
    record Context(MagicPolicyLimits policy, Map<ProfileKey, ProfileAvailability> profiles) {
        Context {
            Objects.requireNonNull(policy);
            if (profiles.size() > MAX_LEGACY_PROFILE_FACTS) {
                throw new IllegalArgumentException("legacy profile snapshot capacity");
            }
            var ordered = new TreeMap<ProfileKey, ProfileAvailability>(
                    Comparator.comparing(ProfileKey::field).thenComparing(ProfileKey::profile));
            ordered.putAll(Map.copyOf(profiles));
            profiles = Collections.unmodifiableMap(ordered);
        }

        ProfileAvailability availability(AppearanceField field, ResourceLocation profile) {
            if (profile.toString().length() > MAX_STRING_LENGTH) {
                return ProfileAvailability.UNKNOWN;
            }
            return profiles.getOrDefault(new ProfileKey(field, profile), ProfileAvailability.UNKNOWN);
        }
    }

    record ProfileKey(AppearanceField field, ResourceLocation profile) {
        ProfileKey {
            Objects.requireNonNull(field);
            Objects.requireNonNull(profile);
            if (field != AppearanceField.SOUND_PROFILE && field != AppearanceField.PARTICLE_PROFILE
                    && field != AppearanceField.TRAIL_PROFILE) {
                throw new IllegalArgumentException("legacy profile key requires a profile field");
            }
            if (profile.toString().length() > MAX_STRING_LENGTH) {
                throw new IllegalArgumentException("legacy profile identifier capacity");
            }
        }
    }

    /** Structural key material. The existing immutable exact reference is the source identity. */
    record LegacyKey(int irVersion, int frontendVersion, SkillReference reference, SourceEvidence source,
            Context context, RuntimeNeutralAppearance appearance, List<Node> nodes) {
        LegacyKey { nodes = List.copyOf(nodes); }
    }

    /** Evidence for the entire opaque immutable input carrier, never a per-slot raw digest. */
    record SourceEvidence(int bytes, String sha256) {}

    record Node(int index, TriggerBinding trigger, ActionBinding action,
            ValidatedNodeReferenceProjection references, RuntimeNeutralAppearanceOverride appearance) {}

    record TriggerBinding(ResourceLocation id, int sourceSchema, int resolvedSchema,
            TriggerCapabilities capabilities, List<Field> fields) {
        TriggerBinding { fields = List.copyOf(fields); }
    }

    record ActionBinding(ResourceLocation id, int sourceSchema, int resolvedSchema,
            ActionCapabilities capabilities, List<Field> fields) {
        ActionBinding { fields = List.copyOf(fields); }
    }

    record Field(String name, Scalar value) {}

    sealed interface Scalar permits IntValue, LongValue, BooleanValue {}
    record IntValue(int value) implements Scalar {}
    record LongValue(long value) implements Scalar {}
    record BooleanValue(boolean value) implements Scalar {}

    enum Side { TOP_APPEARANCE, TRIGGER, ACTION, APPEARANCE_OVERRIDE }

    /** One slot in the flattened side/reference layout. A side's slots are contiguous. */
    record Address(SkillReference reference, int nodeIndex, Side side,
            Optional<String> payloadField, int referenceOrdinal, Optional<ReferenceRole> role,
            int offset, int length) {}

    record LegacyCost(int nodes, int references, int payloadFields, int sourceMapEntries,
            int projectedBytes, int diagnosticRetained, int diagnosticOmitted) {}

    enum Failure { DOCUMENT_REJECTED, EXACT_REFERENCE, VALIDATION, PAYLOAD_SHAPE, CAPACITY, SESSION_BUSY }
    record Finding(ValidationIssueCode code, ValidationSeverity severity, ValidationPath path) {}

    record Diagnostics(Optional<Failure> failure, ValidationPath location, List<Finding> findings,
            int omitted, boolean upstreamTruncated, boolean fatal) {
        Diagnostics { findings = List.copyOf(findings); }
    }

    record LegacyProgram(LegacyKey key, List<Address> sourceMap, LegacyCost cost, Diagnostics diagnostics)
            implements ScopeDProgram {
        LegacyProgram { sourceMap = List.copyOf(sourceMap); }
    }

    sealed interface Result permits Success, Rejected {}
    record Success(LegacyProgram program) implements Result {}
    record Rejected(Diagnostics diagnostics) implements Result {}

    private static final class Projection {
        private final SkillReference reference;
        private final List<Node> nodes = new ArrayList<>();
        private final List<Address> addresses = new ArrayList<>();
        private int references;
        private int payloadFields;
        private int bytes;

        private Projection(SkillReference reference) {
            this.reference = reference;
            this.bytes = LEGACY_FIXED_BYTES;
        }

        private void addAppearance(RuntimeNeutralAppearance appearance) {
            address(-1, Side.TOP_APPEARANCE, Optional.empty(), -1, Optional.empty(), 1);
            charge(LEGACY_APPEARANCE_BYTES);
            if (appearance instanceof RuntimeNeutralAppearance.Typed typed) {
                var value = typed.definition();
                appearanceBytes(value.soundProfile(), value.particleProfile(), value.trailProfile(),
                        ValidationPath.empty().field("appearance"));
            }
        }

        private void add(Node node) {
            var path = ValidationPath.empty().field("nodes").index(node.index());
            if (!node.action().capabilities().appearanceParameters().integerRanges().isEmpty()) {
                throw new CannotProject(Failure.PAYLOAD_SHAPE, path.field("action"));
            }
            binding(node.index(), Side.TRIGGER, node.trigger().id(), node.trigger().fields(),
                    node.references().trigger().references(), path.field("trigger"));
            binding(node.index(), Side.ACTION, node.action().id(), node.action().fields(),
                    node.references().action().references(), path.field("action"));
            address(node.index(), Side.APPEARANCE_OVERRIDE, Optional.empty(), -1, Optional.empty(), 1);
            charge(LEGACY_APPEARANCE_BYTES);
            if (node.appearance() instanceof RuntimeNeutralAppearanceOverride.Typed typed) {
                var value = typed.override();
                appearanceBytes(value.soundProfile(), value.particleProfile(), value.trailProfile(),
                        path.field("appearance_override"));
            }
            nodes.add(node);
        }

        private void binding(int node, Side side, ResourceLocation id, List<Field> fields,
                List<ValidatedNodeReference> values, ValidationPath path) {
            references = Math.addExact(references, values.size());
            if (references > MAX_LEGACY_REFERENCES) {
                throw new CannotProject(Failure.CAPACITY, path);
            }
            payloadFields = Math.addExact(payloadFields, fields.size());
            charge(LEGACY_BINDING_BYTES);
            symbol(id.toString(), path);
            int length = Math.addExact(1, Math.addExact(fields.size(), values.size()));
            address(node, side, Optional.empty(), -1, Optional.empty(), length);
            for (var field : fields) {
                charge(LEGACY_SCALAR_BYTES);
                symbol(field.name(), path);
                address(node, side, Optional.of(field.name()), -1, Optional.empty(), 1);
            }
            for (int index = 0; index < values.size(); index++) {
                address(node, side, Optional.empty(), index, Optional.of(values.get(index).role()), 1);
                charge(LEGACY_REFERENCE_BYTES);
            }
        }

        private void address(int node, Side side, Optional<String> payloadField, int ordinal,
                Optional<ReferenceRole> role, int length) {
            if (addresses.size() == MAX_LEGACY_SOURCE_MAP_ENTRIES) {
                throw new CannotProject(Failure.CAPACITY, ValidationPath.empty());
            }
            charge(LEGACY_ADDRESS_BYTES);
            addresses.add(new Address(reference, node, side, payloadField, ordinal, role, addresses.size(), length));
        }

        private void appearanceBytes(ProfileSelection sound, ProfileSelection particle,
                ProfileSelection trail, ValidationPath path) {
            for (var selection : List.of(sound, particle, trail)) {
                if (selection instanceof ProfileSelection.Specified specified) {
                    symbol(specified.id().toString(), path);
                }
            }
        }

        private void symbol(String text, ValidationPath path) {
            requireLength(text, path);
            // Four bytes per UTF-16 unit overestimates the UTF-8 material.
            charge(Math.addExact(Integer.BYTES, Math.multiplyExact(text.length(), 4)));
        }

        private void charge(int addition) {
            bytes = Math.addExact(bytes, addition);
            if (bytes > MAX_LEGACY_PROJECTED_BYTES) {
                throw new CannotProject(Failure.CAPACITY, ValidationPath.empty());
            }
        }

        private LegacyCost cost(Context context, Diagnostics diagnostics) {
            for (var profile : context.profiles().keySet()) {
                charge(LEGACY_PROFILE_FACT_BYTES);
                symbol(profile.profile().toString(), ValidationPath.empty());
            }
            for (var finding : diagnostics.findings()) {
                charge(LEGACY_DIAGNOSTIC_BYTES);
                symbol(finding.code().toString(), ValidationPath.empty());
                symbol(finding.path().render(), ValidationPath.empty());
            }
            return new LegacyCost(nodes.size(), references, payloadFields, addresses.size(), bytes,
                    diagnostics.findings().size(), diagnostics.omitted());
        }
    }

    private static final class CannotProject extends RuntimeException {
        private final Failure failure;
        private final ValidationPath path;

        private CannotProject(Failure failure, ValidationPath path) {
            this.failure = failure;
            this.path = path;
        }
    }
}
