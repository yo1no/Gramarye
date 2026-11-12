package com.yo1no.gramarye.magic.definition.scoped;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Immutable Scope D source data. Structural eligibility carries no execution or publication authority. */
final class ScopeDData {
    private static final Pattern TAG = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern SYMBOL = Pattern.compile("[a-z][a-z0-9_]*");

    private ScopeDData() {}

    record SpellDefinitionId(long value) {
        SpellDefinitionId { positive(value); }
    }

    record SkillManifestId(long value) {
        SkillManifestId { positive(value); }
    }

    record ManifestEntryId(long value) {
        ManifestEntryId { positive(value); }
    }

    record HandlerId(long value) {
        HandlerId { positive(value); }
    }

    record BlockId(long value) {
        BlockId { positive(value); }
    }

    record PublicationId(long value) {
        PublicationId { positive(value); }
    }

    record SpellRevision(long value) {
        SpellRevision { nonnegative(value); }

        SpellRevision successor() { return new SpellRevision(Math.incrementExact(value)); }
    }

    record ManifestRevision(long value) {
        ManifestRevision { nonnegative(value); }

        ManifestRevision successor() { return new ManifestRevision(Math.incrementExact(value)); }
    }

    record CatalogGeneration(long value) {
        CatalogGeneration { nonnegative(value); }

        CatalogGeneration successor() { return new CatalogGeneration(Math.incrementExact(value)); }
    }

    sealed interface Identity permits SpellIdentity, ManifestIdentity {
        SkillOwnerId owner();
    }

    record SpellIdentity(SkillOwnerId owner, SpellDefinitionId id) implements Identity {
        SpellIdentity {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(id);
        }
    }

    record ManifestIdentity(SkillOwnerId owner, SkillManifestId id) implements Identity {
        ManifestIdentity {
            Objects.requireNonNull(owner);
            Objects.requireNonNull(id);
        }
    }

    sealed interface ExactReference permits SpellReference, ManifestReference {
        Identity identity();

        long revisionNumber();
    }

    record SpellReference(SpellIdentity identity, SpellRevision revision) implements ExactReference {
        SpellReference {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(revision);
        }

        @Override
        public long revisionNumber() { return revision.value(); }
    }

    record ManifestReference(ManifestIdentity identity, ManifestRevision revision) implements ExactReference {
        ManifestReference {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(revision);
        }

        @Override
        public long revisionNumber() { return revision.value(); }
    }

    record SpellIdentityReference(SpellIdentity target) {
        SpellIdentityReference { Objects.requireNonNull(target); }
    }

    record ForkProvenance(UUID sourceId, ExactReference source, CatalogGeneration generation) {
        ForkProvenance {
            Objects.requireNonNull(sourceId);
            Objects.requireNonNull(source);
            Objects.requireNonNull(generation);
        }
    }

    sealed interface ScopedDocument permits FormalDocument, Draft {
        Identity identity();
    }

    sealed interface FormalDocument extends ScopedDocument permits SpellDefinition, SkillManifest {
        ExactReference reference();

        Optional<ForkProvenance> provenance();

        List<SpellIdentityReference> dependencies();

        boolean hasOpaqueRaw();

        FormalDocument remap(
                ExactReference target, Map<SpellIdentity, SpellIdentity> remap, ForkProvenance provenance);

        @Override
        default Identity identity() { return reference().identity(); }
    }

    record SpellDefinition(SpellReference reference, SpellBody body, Optional<ForkProvenance> provenance)
            implements FormalDocument {
        SpellDefinition {
            Objects.requireNonNull(reference);
            Objects.requireNonNull(body);
            checkProvenance(reference, provenance);
            checkStorage(reference.identity(), body);
        }

        @Override
        public List<SpellIdentityReference> dependencies() {
            var result = new ArrayList<SpellIdentityReference>();
            for (var handler : knownList(body.handlers())) {
                for (var block : handler.blocks()) {
                    if (block instanceof CreateSpell create) {
                        result.add(create.target());
                    }
                }
            }
            return List.copyOf(result);
        }

        List<HandlerId> handlerIds() {
            return knownList(body.handlers()).stream().map(Handler::id).toList();
        }

        List<BlockId> blockIds() {
            return knownList(body.handlers()).stream().flatMap(handler -> handler.blocks().stream())
                    .map(Block::id).toList();
        }

        @Override
        public boolean hasOpaqueRaw() { return containsRaw(body); }

        @Override
        public SpellDefinition remap(
                ExactReference target, Map<SpellIdentity, SpellIdentity> remap, ForkProvenance provenance) {
            if (!(target instanceof SpellReference spell) || hasOpaqueRaw()) {
                throw new IllegalArgumentException("unsafe Spell fork remap");
            }
            return new SpellDefinition(spell, remapSpellBody(body, remap), Optional.of(provenance));
        }
    }

    record SkillManifest(ManifestReference reference, ManifestBody body, Optional<ForkProvenance> provenance)
            implements FormalDocument {
        SkillManifest {
            Objects.requireNonNull(reference);
            Objects.requireNonNull(body);
            checkProvenance(reference, provenance);
            checkStorage(reference.identity(), body);
        }

        @Override
        public List<SpellIdentityReference> dependencies() {
            return knownList(body.entries()).stream().map(ManifestEntry::target).toList();
        }

        List<ManifestEntryId> entryIds() {
            return knownList(body.entries()).stream().map(ManifestEntry::id).toList();
        }

        @Override
        public boolean hasOpaqueRaw() { return body.entries() instanceof Unknown<?>; }

        @Override
        public SkillManifest remap(
                ExactReference target, Map<SpellIdentity, SpellIdentity> remap, ForkProvenance provenance) {
            if (!(target instanceof ManifestReference manifest) || hasOpaqueRaw()) {
                throw new IllegalArgumentException("unsafe Manifest fork remap");
            }
            var entries = knownList(body.entries()).stream().map(entry -> new ManifestEntry(entry.id(),
                    remapReference(entry.target(), remap), entry.label(), entry.group())).toList();
            return new SkillManifest(manifest, new ManifestBody(new Known<>(entries), body.label(),
                    body.exportIntent()), Optional.of(provenance));
        }
    }

    static SpellBody remapSpellBody(SpellBody body, Map<SpellIdentity, SpellIdentity> remap) {
        if (containsRaw(body) || !(body.handlers() instanceof Known<List<Handler>> known)) {
            throw new IllegalArgumentException("unsafe or incomplete Spell body remap");
        }
        var handlers = known.value().stream().map(handler -> new Handler(
                handler.id(), handler.event(), handler.eventVersion(), handler.blocks().stream().<Block>map(block -> switch (block) {
                    case CreateSpell create -> new CreateSpell(create.id(), remapReference(create.target(), remap));
                    case EffectBlock effect -> effect;
                    case UnknownBlock ignored -> throw new IllegalArgumentException("unsafe block remap");
                }).toList())).toList();
        return new SpellBody(body.backend(), body.activation(), body.settings(), new Known<>(handlers),
                body.appearance(), body.phenomenon());
    }

    private static SpellIdentityReference remapReference(
            SpellIdentityReference source, Map<SpellIdentity, SpellIdentity> remap) {
        var target = remap.get(source.target());
        if (target == null) {
            throw new IllegalArgumentException("incomplete Spell identity remap");
        }
        return new SpellIdentityReference(target);
    }

    sealed interface Draft extends ScopedDocument permits SpellDraft, ManifestDraft {
        CatalogGeneration baseGeneration();
    }

    record SpellDraft(SpellIdentity identity, Optional<SpellRevision> baseRevision,
                      CatalogGeneration baseGeneration, SpellBody body) implements Draft {
        SpellDraft {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(baseRevision);
            Objects.requireNonNull(baseGeneration);
            checkStorage(identity, Objects.requireNonNull(body));
        }
    }

    record ManifestDraft(ManifestIdentity identity, Optional<ManifestRevision> baseRevision,
                         CatalogGeneration baseGeneration, ManifestBody body) implements Draft {
        ManifestDraft {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(baseRevision);
            Objects.requireNonNull(baseGeneration);
            checkStorage(identity, Objects.requireNonNull(body));
        }
    }

    static SpellDefinition formalize(SpellDraft draft, SpellReference reference) {
        requireSameIdentity(draft.identity(), reference);
        var result = new SpellDefinition(reference, draft.body(), Optional.empty());
        requireFormalShape(result);
        return result;
    }

    static SkillManifest formalize(ManifestDraft draft, ManifestReference reference) {
        requireSameIdentity(draft.identity(), reference);
        var result = new SkillManifest(reference, draft.body(), Optional.empty());
        requireFormalShape(result);
        return result;
    }

    private static void requireSameIdentity(Identity identity, ExactReference reference) {
        if (!identity.equals(reference.identity())) {
            throw new IllegalArgumentException("Draft identity differs from candidate revision");
        }
    }

    private static void checkProvenance(ExactReference reference, Optional<ForkProvenance> provenance) {
        Objects.requireNonNull(provenance);
        provenance.ifPresent(value -> {
            if (reference.getClass() != value.source().getClass()
                    || reference.identity().owner().equals(value.source().identity().owner())) {
                throw new IllegalArgumentException("invalid fork provenance");
            }
        });
    }

    sealed interface Slot<T> permits Missing, Absent, Known, Unknown {}

    record Missing<T>() implements Slot<T> {}

    record Absent<T>() implements Slot<T> {}

    record Known<T>(T value) implements Slot<T> {
        Known { Objects.requireNonNull(value); }
    }

    record Unknown<T>(RawEnvelope raw) implements Slot<T> {
        Unknown { Objects.requireNonNull(raw); }
    }

    record SpellBody(Slot<Backend> backend, Slot<Descriptor> activation, Slot<List<Setting>> settings,
                     Slot<List<Handler>> handlers, Slot<Descriptor> appearance, Slot<Descriptor> phenomenon) {
        SpellBody {
            Objects.requireNonNull(backend);
            Objects.requireNonNull(activation);
            settings = copySlot(settings, MAX_SETTINGS, "settings");
            handlers = copySlot(handlers, MAX_HANDLERS, "handlers");
            Objects.requireNonNull(appearance);
            Objects.requireNonNull(phenomenon);
            int blocks = 0;
            for (var handler : knownList(handlers)) {
                blocks = addBounded(blocks, handler.blocks().size(), MAX_BLOCKS, "blocks");
            }
            var expressions = new ExpressionBudget();
            for (var setting : knownList(settings)) {
                expressions.visit(setting.expression(), 1);
            }
        }
    }

    record Backend(Descriptor descriptor, String category, List<String> capabilities) {
        Backend {
            Objects.requireNonNull(descriptor);
            tag(category);
            capabilities = copyBounded(capabilities, MAX_CAPABILITIES, "capabilities");
            capabilities.forEach(ScopeDData::tag);
            if (new HashSet<>(capabilities).size() != capabilities.size()) {
                throw new IllegalArgumentException("duplicate capabilities");
            }
        }
    }

    record Descriptor(String tag, int version, List<Argument> arguments) {
        Descriptor {
            ScopeDData.tag(tag);
            nonnegative(version);
            arguments = copyBounded(arguments, MAX_ARGUMENTS, "descriptor arguments");
            var names = new HashSet<String>();
            for (var argument : arguments) {
                if (!names.add(argument.name())) {
                    throw new IllegalArgumentException("duplicate descriptor argument");
                }
            }
        }
    }

    record Argument(String name, Value value) {
        Argument {
            symbol(name);
            Objects.requireNonNull(value);
        }
    }

    enum ValueType { BOOLEAN, INTEGER, TEXT }

    sealed interface Value permits BooleanValue, IntegerValue, TextValue {
        ValueType type();
    }

    record BooleanValue(boolean value) implements Value {
        @Override
        public ValueType type() { return ValueType.BOOLEAN; }
    }

    record IntegerValue(long value) implements Value {
        @Override
        public ValueType type() { return ValueType.INTEGER; }
    }

    record TextValue(String value) implements Value {
        TextValue { text(value, MAX_TEXT_BYTES); }

        @Override
        public ValueType type() { return ValueType.TEXT; }
    }

    record Setting(String name, Expression expression) {
        Setting {
            symbol(name);
            Objects.requireNonNull(expression);
        }
    }

    sealed interface Expression permits Literal, CreationInput, Equal, Select {
        ValueType type();
    }

    record Literal(Value value) implements Expression {
        Literal { Objects.requireNonNull(value); }

        @Override
        public ValueType type() { return value.type(); }
    }

    record CreationInput(String name, ValueType type) implements Expression {
        CreationInput {
            symbol(name);
            Objects.requireNonNull(type);
        }
    }

    record Equal(Expression left, Expression right) implements Expression {
        Equal {
            if (left.type() != right.type()) {
                throw new IllegalArgumentException("equality operand type mismatch");
            }
        }

        @Override
        public ValueType type() { return ValueType.BOOLEAN; }
    }

    record Select(ValueType type, Expression condition, Expression whenTrue, Expression whenFalse)
            implements Expression {
        Select {
            if (condition.type() != ValueType.BOOLEAN || whenTrue.type() != type || whenFalse.type() != type) {
                throw new IllegalArgumentException("select operand type mismatch");
            }
        }
    }

    record Handler(HandlerId id, String event, int eventVersion, List<Block> blocks) {
        Handler {
            Objects.requireNonNull(id);
            tag(event);
            nonnegative(eventVersion);
            blocks = copyBounded(blocks, MAX_BLOCKS, "handler blocks");
        }

        Handler(HandlerId id, String event, List<Block> blocks) {
            this(id, event, 1, blocks);
        }
    }

    sealed interface Block permits CreateSpell, EffectBlock, UnknownBlock {
        BlockId id();
    }

    record CreateSpell(BlockId id, SpellIdentityReference target) implements Block {
        CreateSpell {
            Objects.requireNonNull(id);
            Objects.requireNonNull(target);
        }
    }

    record EffectBlock(BlockId id, Descriptor descriptor) implements Block {
        EffectBlock {
            Objects.requireNonNull(id);
            Objects.requireNonNull(descriptor);
        }
    }

    record UnknownBlock(BlockId id, RawEnvelope raw) implements Block {
        UnknownBlock {
            Objects.requireNonNull(id);
            Objects.requireNonNull(raw);
        }
    }

    record ManifestEntry(ManifestEntryId id, SpellIdentityReference target, String label, String group) {
        ManifestEntry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(target);
            text(label, MAX_TEXT_BYTES);
            text(group, MAX_SYMBOL_BYTES);
        }
    }

    record ManifestBody(Slot<List<ManifestEntry>> entries, String label, boolean exportIntent) {
        ManifestBody {
            entries = copySlot(entries, MAX_MANIFEST_ENTRIES, "Manifest entries");
            text(label, MAX_TEXT_BYTES);
        }
    }

    sealed interface RawPosition permits RawField, BlockPayload {}

    enum RawField implements RawPosition { BACKEND, ACTIVATION, SETTINGS, HANDLERS, APPEARANCE, PHENOMENON, ENTRIES }

    record BlockPayload(HandlerId handler, BlockId block) implements RawPosition {
        BlockPayload {
            Objects.requireNonNull(handler);
            Objects.requireNonNull(block);
        }
    }

    record RawLocation(Identity identity, RawPosition position) {
        RawLocation {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(position);
            if ((identity instanceof ManifestIdentity) != (position == RawField.ENTRIES)) {
                throw new IllegalArgumentException("raw location family mismatch");
            }
        }
    }

    enum DescriptorDomain { BACKEND, ACTIVATION, EVENT, EFFECT, APPEARANCE }

    record DescriptorKey(DescriptorDomain domain, String tag, int version) {
        DescriptorKey {
            Objects.requireNonNull(domain);
            ScopeDData.tag(tag);
            nonnegative(version);
        }
    }

    record DescriptorContext(Set<DescriptorKey> descriptors, Set<String> capabilities) {
        DescriptorContext {
            Objects.requireNonNull(descriptors);
            Objects.requireNonNull(capabilities);
            // Capture is bounded before Set.copyOf, independently of per-document vocabulary.
            if (descriptors.size() > MAX_DESCRIPTOR_CONTEXT_ENTRIES
                    || capabilities.size() > MAX_DESCRIPTOR_CONTEXT_ENTRIES) {
                throw new IllegalArgumentException("descriptor context capacity exceeded");
            }
            descriptors = Set.copyOf(descriptors);
            capabilities = Set.copyOf(capabilities);
            capabilities.forEach(ScopeDData::tag);
        }
    }

    enum Code {
        MISSING_REQUIRED, UNKNOWN_GAMEPLAY, UNKNOWN_VISUAL_FALLBACK, HOOK_ONLY,
        UNSUPPORTED_DESCRIPTOR, UNSUPPORTED_CAPABILITY, DUPLICATE_SETTING,
        DUPLICATE_EVENT, DUPLICATE_HANDLER_ID, DUPLICATE_BLOCK_ID,
        DUPLICATE_MANIFEST_ENTRY_ID, DUPLICATE_MANIFEST_SPELL, FOREIGN_OWNER
    }

    record Issue(Code code, String location) {}

    record Validation(List<Issue> issues, boolean truncated, boolean valid) {
        Validation { issues = List.copyOf(issues); }
    }

    static Validation validate(ScopedDocument document, DescriptorContext context) {
        return validation(document, Optional.of(context));
    }

    static void requireFormalShape(FormalDocument document) {
        if (!validation(document, Optional.empty()).valid()) {
            throw new IllegalArgumentException("formal document shape rejected");
        }
    }

    private static Validation validation(ScopedDocument document, Optional<DescriptorContext> context) {
        var issues = new Issues(context);
        switch (document) {
            case SpellDefinition spell -> issues.spell(spell.identity().owner(), spell.body());
            case SpellDraft draft -> issues.spell(draft.identity().owner(), draft.body());
            case SkillManifest manifest -> issues.manifest(manifest.identity().owner(), manifest.body());
            case ManifestDraft draft -> issues.manifest(draft.identity().owner(), draft.body());
        }
        return new Validation(issues.retained, issues.truncated, !issues.blocked);
    }

    private static final class Issues {
        private final Optional<DescriptorContext> context;
        private final List<Issue> retained = new ArrayList<>();
        private boolean truncated;
        private boolean blocked;

        private Issues(Optional<DescriptorContext> context) { this.context = context; }

        private void spell(SkillOwnerId owner, SpellBody body) {
            required(body.backend(), "backend");
            if (body.backend() instanceof Known<Backend> known) {
                var backend = known.value();
                descriptor(DescriptorDomain.BACKEND, backend.descriptor(), "backend");
                if (context.isPresent() && !context.orElseThrow().capabilities().containsAll(backend.capabilities())) {
                    add(Code.UNSUPPORTED_CAPABILITY, "backend");
                }
            }
            optionalDescriptor(body.activation(), DescriptorDomain.ACTIVATION, "activation");
            optionalDescriptor(body.appearance(), DescriptorDomain.APPEARANCE, "appearance");
            if (!(body.phenomenon() instanceof Absent<?>)) {
                add(body.phenomenon() instanceof Missing<?> ? Code.MISSING_REQUIRED : Code.HOOK_ONLY, "phenomenon");
            }
            required(body.settings(), "settings");
            var names = new HashSet<String>();
            var settings = knownList(body.settings());
            for (int i = 0; i < settings.size(); i++) {
                if (!names.add(settings.get(i).name())) {
                    add(Code.DUPLICATE_SETTING, "settings[" + i + "]");
                }
            }
            required(body.handlers(), "handlers");
            var handlerIds = new HashSet<HandlerId>();
            var events = new HashSet<String>();
            var blocks = new HashSet<BlockId>();
            var handlers = knownList(body.handlers());
            for (int i = 0; i < handlers.size(); i++) {
                var handler = handlers.get(i);
                var path = "handlers[" + i + "]";
                if (!handlerIds.add(handler.id())) {
                    add(Code.DUPLICATE_HANDLER_ID, path);
                }
                if (!events.add(handler.event())) {
                    add(Code.DUPLICATE_EVENT, path);
                }
                descriptor(DescriptorDomain.EVENT, handler.event(), handler.eventVersion(), path);
                for (int j = 0; j < handler.blocks().size(); j++) {
                    var block = handler.blocks().get(j);
                    var blockPath = path + ".blocks[" + j + "]";
                    if (!blocks.add(block.id())) {
                        add(Code.DUPLICATE_BLOCK_ID, blockPath);
                    }
                    switch (block) {
                        case CreateSpell create -> owner(owner, create.target(), blockPath);
                        case EffectBlock effect -> descriptor(DescriptorDomain.EFFECT, effect.descriptor(), blockPath);
                        case UnknownBlock ignored -> add(Code.UNKNOWN_GAMEPLAY, blockPath);
                    }
                }
            }
        }

        private void manifest(SkillOwnerId owner, ManifestBody body) {
            required(body.entries(), "entries");
            var ids = new HashSet<ManifestEntryId>();
            var targets = new HashSet<SpellIdentity>();
            var entries = knownList(body.entries());
            for (int i = 0; i < entries.size(); i++) {
                var entry = entries.get(i);
                var path = "entries[" + i + "]";
                if (!ids.add(entry.id())) {
                    add(Code.DUPLICATE_MANIFEST_ENTRY_ID, path);
                }
                if (!targets.add(entry.target().target())) {
                    add(Code.DUPLICATE_MANIFEST_SPELL, path);
                }
                owner(owner, entry.target(), path);
            }
        }

        private void owner(SkillOwnerId expected, SpellIdentityReference target, String path) {
            if (!expected.equals(target.target().owner())) {
                add(Code.FOREIGN_OWNER, path);
            }
        }

        private void required(Slot<?> slot, String path) {
            if (slot instanceof Unknown<?>) {
                add(Code.UNKNOWN_GAMEPLAY, path);
            } else if (!(slot instanceof Known<?>)) {
                add(Code.MISSING_REQUIRED, path);
            }
        }

        private void optionalDescriptor(Slot<Descriptor> slot, DescriptorDomain domain, String path) {
            switch (slot) {
                case Known<Descriptor> known -> descriptor(domain, known.value(), path);
                case Unknown<Descriptor> ignored -> add(domain == DescriptorDomain.APPEARANCE
                        ? Code.UNKNOWN_VISUAL_FALLBACK : Code.UNKNOWN_GAMEPLAY, path);
                case Missing<Descriptor> ignored -> add(Code.MISSING_REQUIRED, path);
                case Absent<Descriptor> ignored -> { }
            }
        }

        private void descriptor(DescriptorDomain domain, Descriptor descriptor, String path) {
            descriptor(domain, descriptor.tag(), descriptor.version(), path);
        }

        private void descriptor(DescriptorDomain domain, String tag, int version, String path) {
            if (context.isPresent() && !context.orElseThrow().descriptors().contains(new DescriptorKey(domain, tag, version))) {
                add(domain == DescriptorDomain.APPEARANCE ? Code.UNKNOWN_VISUAL_FALLBACK : Code.UNSUPPORTED_DESCRIPTOR, path);
            }
        }

        private void add(Code code, String location) {
            blocked |= code != Code.UNKNOWN_VISUAL_FALLBACK;
            if (retained.size() < MAX_DIAGNOSTICS) {
                retained.add(new Issue(code, location));
            } else {
                truncated = true;
            }
        }
    }

    private static void checkStorage(SpellIdentity identity, SpellBody body) {
        var budget = new RawBudget();
        budget.slot(identity, RawField.BACKEND, body.backend());
        budget.slot(identity, RawField.ACTIVATION, body.activation());
        budget.slot(identity, RawField.SETTINGS, body.settings());
        budget.slot(identity, RawField.HANDLERS, body.handlers());
        budget.slot(identity, RawField.APPEARANCE, body.appearance());
        budget.slot(identity, RawField.PHENOMENON, body.phenomenon());
        for (var handler : knownList(body.handlers())) {
            for (var block : handler.blocks()) {
                if (block instanceof UnknownBlock unknown) {
                    budget.raw(identity, new BlockPayload(handler.id(), unknown.id()), unknown.raw());
                }
            }
        }
    }

    private static void checkStorage(ManifestIdentity identity, ManifestBody body) {
        new RawBudget().slot(identity, RawField.ENTRIES, body.entries());
    }

    private static boolean containsRaw(SpellBody body) {
        return body.backend() instanceof Unknown<?> || body.activation() instanceof Unknown<?>
                || body.settings() instanceof Unknown<?> || body.handlers() instanceof Unknown<?>
                || body.appearance() instanceof Unknown<?> || body.phenomenon() instanceof Unknown<?>
                || knownList(body.handlers()).stream().flatMap(handler -> handler.blocks().stream())
                        .anyMatch(UnknownBlock.class::isInstance);
    }

    private static final class RawBudget {
        private int slots;
        private int bytes;

        private void slot(Identity identity, RawPosition position, Slot<?> slot) {
            if (slot instanceof Unknown<?> unknown) {
                raw(identity, position, unknown.raw());
            }
        }

        private void raw(Identity identity, RawPosition position, RawEnvelope raw) {
            if (!identity.equals(raw.location().identity()) || !position.equals(raw.location().position())) {
                throw new IllegalArgumentException("raw slot moved from its bound location");
            }
            slots = addBounded(slots, 1, MAX_RAW_SLOTS, "raw slots");
            bytes = addBounded(bytes, raw.byteCount(), MAX_RAW_BYTES, "aggregate raw bytes");
        }
    }

    static final class ExpressionBudget {
        private int count;

        void accept(int depth) {
            if (depth > MAX_EXPRESSION_DEPTH) {
                throw new IllegalArgumentException("expression depth exceeded");
            }
            count = addBounded(count, 1, MAX_EXPRESSIONS, "expression nodes");
        }

        private void visit(Expression expression, int depth) {
            accept(depth);
            switch (expression) {
                case Equal equal -> {
                    visit(equal.left(), depth + 1);
                    visit(equal.right(), depth + 1);
                }
                case Select select -> {
                    visit(select.condition(), depth + 1);
                    visit(select.whenTrue(), depth + 1);
                    visit(select.whenFalse(), depth + 1);
                }
                case Literal ignored -> { }
                case CreationInput ignored -> { }
            }
        }
    }

    private static <T> Slot<List<T>> copySlot(Slot<List<T>> slot, int max, String label) {
        Objects.requireNonNull(slot);
        return slot instanceof Known<List<T>> known ? new Known<>(copyBounded(known.value(), max, label)) : slot;
    }

    static <T> List<T> knownList(Slot<List<T>> slot) {
        return slot instanceof Known<List<T>> known ? known.value() : List.of();
    }

    private static <T> List<T> copyBounded(List<T> source, int max, String label) {
        if (source.size() > max) {
            throw new IllegalArgumentException(label + " count exceeded");
        }
        return List.copyOf(source);
    }

    static int addBounded(int total, int next, int max, String label) {
        if (next < 0 || total < 0 || next > max - total) {
            throw new IllegalArgumentException(label + " exceeded");
        }
        return total + next;
    }

    static void tag(String value) {
        text(value, MAX_SYMBOL_BYTES);
        if (!TAG.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid descriptor tag");
        }
    }

    private static void symbol(String value) {
        text(value, MAX_SYMBOL_BYTES);
        if (!SYMBOL.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid symbol");
        }
    }

    static void text(String value, int max) {
        if (value.length() > max || !StandardCharsets.UTF_8.newEncoder().canEncode(value)
                || value.getBytes(StandardCharsets.UTF_8).length > max) {
            throw new IllegalArgumentException("invalid or over-capacity UTF-8 field");
        }
    }

    private static void positive(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("identity must be positive");
        }
    }

    private static void nonnegative(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be nonnegative");
        }
    }
}
