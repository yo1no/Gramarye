package com.yo1no.gramarye.magic.definition.scoped;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;

/** Trusted compiler metadata captured as immutable data. No descriptor executes a callback. */
final class ScopeDFrontend {
    static final int FRONTEND_ABI = 1;
    static final int COMPILER_ABI = 1;
    static final int LEGACY_FRONTEND_ABI = 1;
    private static final Comparator<DescriptorKey> KEY_ORDER = Comparator.comparing(DescriptorKey::domain)
            .thenComparing(DescriptorKey::tag).thenComparingInt(DescriptorKey::version);

    private ScopeDFrontend() {}

    record Field(ValueType type, boolean required, Optional<Value> defaultValue) {
        Field {
            Objects.requireNonNull(type);
            Objects.requireNonNull(defaultValue);
            if (defaultValue.isPresent() && (required || defaultValue.orElseThrow().type() != type)) {
                throw new IllegalArgumentException("Invalid trusted field default");
            }
        }

        static Field required(ValueType type) { return new Field(type, true, Optional.empty()); }

        static Field optional(ValueType type) { return new Field(type, false, Optional.empty()); }

        static Field defaulted(Value value) { return new Field(value.type(), false, Optional.of(value)); }
    }

    /** A closed schema. Bindings are in symbol order, independent of authored argument order. */
    record Schema(Map<String, Field> fields) {
        static final Schema EMPTY = new Schema(Map.of());

        Schema {
            count(fields.size(), MAX_FRONTEND_FIELDS, "schema fields");
            fields = Map.copyOf(fields);
            fields.forEach((name, field) -> new CreationInput(name, field.type()));
        }

        Optional<List<Argument>> bind(List<Argument> arguments) {
            if (arguments.size() > MAX_FRONTEND_ARGUMENTS) {
                return Optional.empty();
            }
            var values = new HashMap<String, Value>();
            for (Argument argument : arguments) {
                Field field = fields.get(argument.name());
                if (field == null || field.type() != argument.value().type()
                        || values.putIfAbsent(argument.name(), argument.value()) != null) {
                    return Optional.empty();
                }
            }
            var normalized = new ArrayList<Argument>();
            for (String name : fields.keySet().stream().sorted().toList()) {
                Field field = fields.get(name);
                Value value = values.get(name);
                if (value != null) {
                    normalized.add(new Argument(name, value));
                } else if (field.required()) {
                    return Optional.empty();
                } else {
                    field.defaultValue().ifPresent(fallback -> normalized.add(new Argument(name, fallback)));
                }
            }
            return Optional.of(List.copyOf(normalized));
        }
    }

    sealed interface Contract permits BackendContract, ActivationContract, EventContract, EffectContract, AppearanceContract {
        DescriptorKey key();

        Schema arguments();

        Set<String> requiredCapabilities();

        int staticCost();
    }

    record CapabilityConflict(String first, String second) {
        CapabilityConflict {
            tag(first);
            tag(second);
            if (first.equals(second)) {
                throw new IllegalArgumentException("A capability cannot conflict with itself");
            }
            if (first.compareTo(second) > 0) {
                String previousFirst = first;
                first = second;
                second = previousFirst;
            }
        }
    }

    record BackendContract(DescriptorKey key, String category, Schema arguments,
                           Set<String> requiredCapabilities, Set<String> providedCapabilities,
                           Set<CapabilityConflict> conflicts, Schema settings, Schema creationInputs,
                           Set<DescriptorKey> events, int staticCost) implements Contract {
        BackendContract {
            header(key, DescriptorDomain.BACKEND, arguments, staticCost);
            tag(category);
            requiredCapabilities = capabilities(requiredCapabilities);
            providedCapabilities = capabilities(providedCapabilities);
            count(conflicts.size(), MAX_FRONTEND_CONFLICTS, "capability conflicts");
            conflicts = Set.copyOf(conflicts);
            Objects.requireNonNull(settings);
            Objects.requireNonNull(creationInputs);
            events = eventKeys(events);
            if (!providedCapabilities.containsAll(requiredCapabilities)) {
                throw new IllegalArgumentException("Backend requires capabilities it cannot provide");
            }
            for (CapabilityConflict conflict : conflicts) {
                if (!providedCapabilities.contains(conflict.first()) || !providedCapabilities.contains(conflict.second())
                        || requiredCapabilities.contains(conflict.first()) && requiredCapabilities.contains(conflict.second())) {
                    throw new IllegalArgumentException("Invalid backend capability conflict");
                }
            }
        }

        boolean supports(List<String> requested) {
            if (!requested.containsAll(requiredCapabilities) || !providedCapabilities.containsAll(requested)) {
                return false;
            }
            return conflicts.stream().noneMatch(conflict ->
                    requested.contains(conflict.first()) && requested.contains(conflict.second()));
        }
    }

    record ActivationContract(DescriptorKey key, Schema arguments, Set<String> categories,
                              Set<String> requiredCapabilities, Map<String, ValueType> creationInputs,
                              int staticCost) implements Contract {
        ActivationContract {
            header(key, DescriptorDomain.ACTIVATION, arguments, staticCost);
            categories = ScopeDFrontend.categories(categories);
            requiredCapabilities = capabilities(requiredCapabilities);
            creationInputs = typedFields(creationInputs);
        }
    }

    /** CREATION may run on creation. EXTERNAL has no automatic creation edge. */
    enum EventOrigin { EXTERNAL, CREATION }

    /** The inherited typed inputs are a subset of the originating event's named facts. */
    record CreateContract(Set<String> requiredCapabilities, Map<String, ValueType> providedInputs, int staticCost) {
        CreateContract {
            requiredCapabilities = capabilities(requiredCapabilities);
            providedInputs = typedFields(providedInputs);
            cost(staticCost);
        }
    }

    record EventContract(DescriptorKey key, Set<String> categories, Set<String> requiredCapabilities,
                         Map<String, ValueType> facts, EventOrigin origin, Optional<CreateContract> creation,
                         int staticCost) implements Contract {
        EventContract {
            header(key, DescriptorDomain.EVENT, Schema.EMPTY, staticCost);
            categories = ScopeDFrontend.categories(categories);
            requiredCapabilities = capabilities(requiredCapabilities);
            facts = typedFields(facts);
            Objects.requireNonNull(origin);
            Objects.requireNonNull(creation);
            if (creation.isPresent() && !facts.entrySet().containsAll(creation.orElseThrow().providedInputs().entrySet())) {
                throw new IllegalArgumentException("CreateSpell inputs must be declared by the event facts schema");
            }
        }

        @Override
        public Schema arguments() { return Schema.EMPTY; }
    }

    /** A typed descriptor reference in derived IR. Its execution ABI requires a later scope. */
    enum EffectOperation { DESCRIPTOR_EFFECT }

    record EffectContract(DescriptorKey key, Schema arguments, Set<String> categories, Set<DescriptorKey> events,
                          Set<String> requiredCapabilities, Map<String, ValueType> facts,
                          Map<String, ValueType> creationInputs, Set<DescriptorKey> emittedEvents,
                          EffectOperation operation, int staticCost) implements Contract {
        EffectContract {
            header(key, DescriptorDomain.EFFECT, arguments, staticCost);
            categories = ScopeDFrontend.categories(categories);
            events = eventKeys(events);
            requiredCapabilities = capabilities(requiredCapabilities);
            facts = typedFields(facts);
            creationInputs = typedFields(creationInputs);
            emittedEvents = eventKeys(emittedEvents);
            Objects.requireNonNull(operation);
        }
    }

    record AppearanceContract(DescriptorKey key, Schema arguments, Set<String> categories,
                              int staticCost) implements Contract {
        AppearanceContract {
            header(key, DescriptorDomain.APPEARANCE, arguments, staticCost);
            categories = ScopeDFrontend.categories(categories);
        }

        @Override
        public Set<String> requiredCapabilities() { return Set.of(); }
    }

    static final class Snapshot {
        private final Map<DescriptorKey, Contract> contracts;
        private final Set<String> capabilities;
        private final EncodedIdentity encoded;

        Snapshot(List<? extends Contract> descriptors) {
            count(descriptors.size(), MAX_FRONTEND_DESCRIPTORS, "frontend descriptors");
            var captured = new HashMap<DescriptorKey, Contract>();
            var vocabulary = new HashSet<String>();
            for (Contract descriptor : descriptors) {
                if (captured.putIfAbsent(descriptor.key(), descriptor) != null) {
                    throw new IllegalArgumentException("Duplicate trusted descriptor key");
                }
                addCapabilities(vocabulary, descriptor.requiredCapabilities());
                switch (descriptor) {
                    case BackendContract backend -> addCapabilities(vocabulary, backend.providedCapabilities());
                    case EventContract event -> event.creation()
                            .ifPresent(creation -> addCapabilities(vocabulary, creation.requiredCapabilities()));
                    default -> { }
                }
            }
            contracts = Map.copyOf(captured);
            capabilities = Set.copyOf(vocabulary);
            for (Contract descriptor : contracts.values()) {
                switch (descriptor) {
                    case BackendContract backend -> knownEvents(backend.events());
                    case EffectContract effect -> {
                        knownEvents(effect.events());
                        knownEvents(effect.emittedEvents());
                    }
                    default -> { }
                }
            }
            encoded = encodeIdentity(out -> {
                out.writeInt(FRONTEND_ABI);
                out.writeInt(contracts.size());
                for (DescriptorKey key : orderedKeys(contracts.keySet())) {
                    writeContract(out, contracts.get(key));
                }
            });
        }

        String digest() { return encoded.digest(); }

        int encodedBytes() { return encoded.bytes(); }

        Policy fullPolicy() { return new Policy(contracts.keySet(), capabilities); }

        private void knownEvents(Set<DescriptorKey> events) {
            if (!contracts.keySet().containsAll(events)) {
                throw new IllegalArgumentException("Trusted descriptor refers to an unregistered event");
            }
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Snapshot snapshot && contracts.equals(snapshot.contracts);
        }

        @Override
        public int hashCode() { return contracts.hashCode(); }
    }

    /** Compiler descriptor/capability admission, separate from catalog and runtime generations. */
    record Policy(Set<DescriptorKey> enabledDescriptors, Set<String> enabledCapabilities) {
        Policy {
            count(enabledDescriptors.size(), MAX_FRONTEND_DESCRIPTORS, "enabled descriptors");
            enabledDescriptors = Set.copyOf(enabledDescriptors);
            enabledCapabilities = capabilities(enabledCapabilities);
        }
    }

    record ContextIdentity(int frontendAbi, int compilerAbi, int legacyFrontendAbi, Snapshot snapshot, Policy policy,
                           String registryDigest, String policyDigest, String catalogFingerprint) {}

    static final class Context {
        private final ScopeDCatalog.Context catalogContext;
        private final ContextIdentity identity;

        Context(Snapshot snapshot, Policy policy) {
            if (!snapshot.contracts.keySet().containsAll(policy.enabledDescriptors())
                    || !snapshot.capabilities.containsAll(policy.enabledCapabilities())) {
                throw new IllegalArgumentException("Policy names unsupported frontend vocabulary");
            }
            String policyDigest = encodeIdentity(out -> {
                writeKeys(out, policy.enabledDescriptors());
                writeStrings(out, policy.enabledCapabilities());
            }).digest();
            String bindingDigest = encodeIdentity(out -> {
                out.writeInt(FRONTEND_ABI);
                out.writeInt(COMPILER_ABI);
                out.writeInt(LEGACY_FRONTEND_ABI);
                out.writeInt(ScopeDProgram.IR_VERSION);
                out.writeUTF(snapshot.digest());
                out.writeUTF(policyDigest);
            }).digest();
            catalogContext = new ScopeDCatalog.Context(
                    new DescriptorContext(policy.enabledDescriptors(), policy.enabledCapabilities()), bindingDigest);
            identity = new ContextIdentity(FRONTEND_ABI, COMPILER_ABI, LEGACY_FRONTEND_ABI, snapshot, policy,
                    snapshot.digest(), policyDigest, catalogContext.fingerprint());
        }

        ContextIdentity identity() { return identity; }

        ScopeDCatalog.Context catalogContext() { return catalogContext; }

        Optional<BackendContract> backend(DescriptorKey key) {
            return lookup(key).filter(BackendContract.class::isInstance).map(BackendContract.class::cast);
        }

        Optional<ActivationContract> activation(DescriptorKey key) {
            return lookup(key).filter(ActivationContract.class::isInstance).map(ActivationContract.class::cast);
        }

        Optional<EventContract> event(DescriptorKey key) {
            return lookup(key).filter(EventContract.class::isInstance).map(EventContract.class::cast);
        }

        Optional<EffectContract> effect(DescriptorKey key) {
            return lookup(key).filter(EffectContract.class::isInstance).map(EffectContract.class::cast);
        }

        Optional<AppearanceContract> appearance(DescriptorKey key) {
            return lookup(key).filter(AppearanceContract.class::isInstance).map(AppearanceContract.class::cast);
        }

        private Optional<Contract> lookup(DescriptorKey key) {
            return identity.policy().enabledDescriptors().contains(key)
                    ? Optional.of(identity.snapshot().contracts.get(key)) : Optional.empty();
        }
    }

    private static void header(DescriptorKey key, DescriptorDomain domain, Schema arguments, int staticCost) {
        if (key.domain() != domain) {
            throw new IllegalArgumentException("Trusted descriptor domain mismatch");
        }
        count(arguments.fields().size(), MAX_FRONTEND_ARGUMENTS, "argument schema fields");
        cost(staticCost);
    }

    private static Set<String> capabilities(Set<String> values) {
        count(values.size(), MAX_FRONTEND_CAPABILITIES, "descriptor capabilities");
        return tags(values);
    }

    private static void addCapabilities(Set<String> vocabulary, Set<String> values) {
        for (String value : values) {
            if (!vocabulary.contains(value)) {
                count(vocabulary.size() + 1, MAX_FRONTEND_CAPABILITIES, "frontend capability vocabulary");
                vocabulary.add(value);
            }
        }
    }

    private static Set<String> categories(Set<String> values) {
        count(values.size(), MAX_FRONTEND_CONTEXT_KEYS, "descriptor categories");
        return tags(values);
    }

    private static Set<String> tags(Set<String> values) {
        Set<String> copy = Set.copyOf(values);
        copy.forEach(ScopeDData::tag);
        return copy;
    }

    private static Set<DescriptorKey> eventKeys(Set<DescriptorKey> values) {
        count(values.size(), MAX_FRONTEND_CONTEXT_KEYS, "descriptor event context");
        Set<DescriptorKey> copy = Set.copyOf(values);
        if (copy.stream().anyMatch(key -> key.domain() != DescriptorDomain.EVENT)) {
            throw new IllegalArgumentException("Non-event key in event context");
        }
        return copy;
    }

    private static Map<String, ValueType> typedFields(Map<String, ValueType> fields) {
        count(fields.size(), MAX_FRONTEND_FIELDS, "typed descriptor fields");
        Map<String, ValueType> copy = Map.copyOf(fields);
        copy.forEach(CreationInput::new);
        return copy;
    }

    private static void cost(int value) {
        count(value, MAX_FRONTEND_COST, "descriptor static cost");
    }

    private static void count(int value, int max, String label) {
        addBounded(0, value, max, label);
    }

    private static void writeContract(DataOutputStream out, Contract descriptor) throws IOException {
        writeKey(out, descriptor.key());
        writeSchema(out, descriptor.arguments());
        writeStrings(out, descriptor.requiredCapabilities());
        out.writeInt(descriptor.staticCost());
        switch (descriptor) {
            case BackendContract backend -> {
                out.writeUTF(backend.category());
                writeStrings(out, backend.providedCapabilities());
                out.writeInt(backend.conflicts().size());
                for (CapabilityConflict conflict : backend.conflicts().stream().sorted(
                        Comparator.comparing(CapabilityConflict::first).thenComparing(CapabilityConflict::second)).toList()) {
                    out.writeUTF(conflict.first());
                    out.writeUTF(conflict.second());
                }
                writeSchema(out, backend.settings());
                writeSchema(out, backend.creationInputs());
                writeKeys(out, backend.events());
            }
            case ActivationContract activation -> {
                writeStrings(out, activation.categories());
                writeFields(out, activation.creationInputs());
            }
            case EventContract event -> {
                writeStrings(out, event.categories());
                writeFields(out, event.facts());
                out.writeUTF(event.origin().name());
                out.writeBoolean(event.creation().isPresent());
                if (event.creation().isPresent()) {
                    CreateContract creation = event.creation().orElseThrow();
                    writeStrings(out, creation.requiredCapabilities());
                    writeFields(out, creation.providedInputs());
                    out.writeInt(creation.staticCost());
                }
            }
            case EffectContract effect -> {
                writeStrings(out, effect.categories());
                writeKeys(out, effect.events());
                writeFields(out, effect.facts());
                writeFields(out, effect.creationInputs());
                writeKeys(out, effect.emittedEvents());
                out.writeUTF(effect.operation().name());
            }
            case AppearanceContract appearance -> writeStrings(out, appearance.categories());
        }
    }

    private static void writeSchema(DataOutputStream out, Schema schema) throws IOException {
        out.writeInt(schema.fields().size());
        for (String name : schema.fields().keySet().stream().sorted().toList()) {
            Field field = schema.fields().get(name);
            out.writeUTF(name);
            out.writeUTF(field.type().name());
            out.writeBoolean(field.required());
            out.writeBoolean(field.defaultValue().isPresent());
            if (field.defaultValue().isPresent()) {
                switch (field.defaultValue().orElseThrow()) {
                    case BooleanValue value -> out.writeBoolean(value.value());
                    case IntegerValue value -> out.writeLong(value.value());
                    case TextValue value -> out.writeUTF(value.value());
                }
            }
        }
    }

    private static void writeFields(DataOutputStream out, Map<String, ValueType> fields) throws IOException {
        out.writeInt(fields.size());
        for (String name : fields.keySet().stream().sorted().toList()) {
            out.writeUTF(name);
            out.writeUTF(fields.get(name).name());
        }
    }

    private static List<DescriptorKey> orderedKeys(Set<DescriptorKey> keys) {
        return keys.stream().sorted(KEY_ORDER).toList();
    }

    private static void writeKey(DataOutputStream out, DescriptorKey key) throws IOException {
        out.writeUTF(key.domain().name());
        out.writeUTF(key.tag());
        out.writeInt(key.version());
    }

    private static void writeKeys(DataOutputStream out, Set<DescriptorKey> keys) throws IOException {
        out.writeInt(keys.size());
        for (DescriptorKey key : orderedKeys(keys)) {
            writeKey(out, key);
        }
    }

    private static void writeStrings(DataOutputStream out, Set<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values.stream().sorted().toList()) {
            out.writeUTF(value);
        }
    }

    private static EncodedIdentity encodeIdentity(Encoding encoding) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = new IdentityOutput(digest);
            try (var out = new DataOutputStream(bytes)) {
                encoding.write(out);
            }
            return new EncodedIdentity(HexFormat.of().formatHex(digest.digest()), bytes.size);
        } catch (IOException malformed) {
            throw new IllegalArgumentException("Could not encode frontend identity", malformed);
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Mandatory SHA-256 unavailable", unavailable);
        }
    }

    private record EncodedIdentity(String digest, int bytes) {}

    private static final class IdentityOutput extends OutputStream {
        private final MessageDigest digest;
        private int size;

        private IdentityOutput(MessageDigest digest) { this.digest = digest; }

        @Override
        public void write(int value) {
            size = addBounded(size, 1, MAX_FRONTEND_BYTES, "frontend identity bytes");
            digest.update((byte) value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            size = addBounded(size, length, MAX_FRONTEND_BYTES, "frontend identity bytes");
            digest.update(bytes, offset, length);
        }
    }

    @FunctionalInterface
    private interface Encoding {
        void write(DataOutputStream out) throws IOException;
    }
}
