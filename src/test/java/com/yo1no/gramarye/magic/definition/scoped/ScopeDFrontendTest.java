package com.yo1no.gramarye.magic.definition.scoped;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

class ScopeDFrontendTest {
    private static final String CATEGORY = "test:category";
    private static final DescriptorKey BACKEND = key(DescriptorDomain.BACKEND, "backend");
    private static final DescriptorKey EVENT = key(DescriptorDomain.EVENT, "event");
    private static final DescriptorKey EFFECT = key(DescriptorDomain.EFFECT, "effect");

    @Test
    void argumentsBindOnlyClosedTypedSchemaInSymbolOrder() {
        var fields = new LinkedHashMap<String, Field>();
        fields.put("third", Field.optional(ValueType.TEXT));
        fields.put("first", Field.required(ValueType.INTEGER));
        fields.put("second", Field.defaulted(new BooleanValue(true)));
        var schema = new Schema(fields);
        fields.clear();
        var first = new Argument("first", new IntegerValue(5));
        var second = new Argument("second", new BooleanValue(false));
        assertEquals(List.of(first, second), schema.bind(List.of(second, first)).orElseThrow());
        assertEquals(List.of(first, new Argument("second", new BooleanValue(true))),
                schema.bind(List.of(first)).orElseThrow());
        assertTrue(schema.bind(List.of(second)).isEmpty());
        assertTrue(schema.bind(List.of(first, first)).isEmpty());
        assertTrue(schema.bind(List.of(first, new Argument("extra", new IntegerValue(5)))).isEmpty());
        assertTrue(schema.bind(List.of(new Argument("first", new BooleanValue(true)))).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> schema.fields().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new Field(ValueType.INTEGER, false, Optional.of(new BooleanValue(true))));
        assertThrows(IllegalArgumentException.class,
                () -> new Field(ValueType.INTEGER, true, Optional.of(new IntegerValue(5))));
    }

    @Test
    void contextBindsFullRegistryPolicyAndAbiWithIndependentExactMaterial() {
        var external = new EventContract(EVENT, Set.of(CATEGORY), Set.of(), Map.of(), EventOrigin.EXTERNAL,
                Optional.empty(), 2);
        var backend = backend(BACKEND, Set.of(), Set.of("test:enabled"), Set.of(), Set.of(EVENT), 1);
        var authored = new ArrayList<Contract>(List.of(backend, external));
        var registry = new Snapshot(authored);
        authored.clear();
        var reordered = new Snapshot(List.of(external, backend));
        var context = new Context(registry, registry.fullPolicy());
        var repeated = new Context(reordered, reordered.fullPolicy());
        assertEquals(context.identity(), repeated.identity());
        assertEquals(context.catalogContext(), repeated.catalogContext());
        assertEquals(Set.of(BACKEND, EVENT), context.catalogContext().descriptors().descriptors());
        assertEquals(Set.of("test:enabled"), context.catalogContext().descriptors().capabilities());
        assertEquals(context.catalogContext().fingerprint(), context.identity().catalogFingerprint());
        assertEquals(FRONTEND_ABI, context.identity().frontendAbi());
        assertEquals(COMPILER_ABI, context.identity().compilerAbi());
        assertEquals(LEGACY_FRONTEND_ABI, context.identity().legacyFrontendAbi());
        assertEquals(Optional.of(backend), context.backend(BACKEND));
        assertTrue(context.event(BACKEND).isEmpty());
        assertTrue(context.event(new DescriptorKey(EVENT.domain(), EVENT.tag(), 2)).isEmpty());

        var restricted = new Context(registry, new Policy(Set.of(BACKEND), Set.of()));
        assertEquals(context.identity().registryDigest(), restricted.identity().registryDigest());
        assertNotEquals(context.identity().policyDigest(), restricted.identity().policyDigest());
        assertNotEquals(context.identity().catalogFingerprint(), restricted.identity().catalogFingerprint());
        assertTrue(restricted.event(EVENT).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new Context(registry, new Policy(Set.of(EFFECT), Set.of())));
        assertThrows(IllegalArgumentException.class,
                () -> new Context(registry, new Policy(Set.of(), Set.of("test:unknown"))));

        var altered = new Snapshot(List.of(backend, new EventContract(EVENT, Set.of(CATEGORY), Set.of(),
                Map.of(), EventOrigin.CREATION, Optional.empty(), 2)));
        var alteredContext = new Context(altered, altered.fullPolicy());
        assertEquals(context.catalogContext().descriptors(), alteredContext.catalogContext().descriptors());
        assertNotEquals(context.identity(), alteredContext.identity());
        assertNotEquals(context.identity().registryDigest(), alteredContext.identity().registryDigest());
        assertNotEquals(context.identity().catalogFingerprint(), alteredContext.identity().catalogFingerprint());
    }

    @Test
    void backendCapabilityConflictAndCreateInputsRequireDeclaredFacts() {
        var conflict = new CapabilityConflict("test:second", "test:first");
        var backend = backend(BACKEND, Set.of("test:first"), Set.of("test:first", "test:second"),
                Set.of(conflict), Set.of(), 0);
        assertEquals(new CapabilityConflict("test:first", "test:second"), conflict);
        assertTrue(backend.supports(List.of("test:first")));
        assertFalse(backend.supports(List.of()));
        assertFalse(backend.supports(List.of("test:first", "test:unknown")));
        assertFalse(backend.supports(List.of("test:first", "test:second")));
        assertThrows(IllegalArgumentException.class, () -> backend(BACKEND, Set.of("test:unknown"),
                Set.of("test:first"), Set.of(), Set.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> backend(BACKEND, Set.of("test:first", "test:second"),
                Set.of("test:first", "test:second"), Set.of(conflict), Set.of(), 0));
        var create = new CreateContract(Set.of(), Map.of("seed", ValueType.INTEGER), 1);
        assertThrows(IllegalArgumentException.class, () -> new EventContract(EVENT, Set.of(CATEGORY), Set.of(),
                Map.of("seed", ValueType.TEXT), EventOrigin.EXTERNAL, Optional.of(create), 1));
        var event = new EventContract(EVENT, Set.of(CATEGORY), Set.of(), Map.of("seed", ValueType.INTEGER),
                EventOrigin.EXTERNAL, Optional.of(create), 1);
        assertEquals(create, event.creation().orElseThrow());
    }

    @Test
    void snapshotRejectsDuplicateWrongDomainAndUnresolvedMetadataEdges() {
        var backend = backend(BACKEND, Set.of(), Set.of(), Set.of(), Set.of(EVENT), 0);
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(backend)));
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(backend, backend)));
        assertThrows(IllegalArgumentException.class,
                () -> backend(BACKEND, Set.of(), Set.of(), Set.of(), Set.of(EFFECT), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AppearanceContract(EVENT, Schema.EMPTY, Set.of(CATEGORY), 0));
        var effect = new EffectContract(EFFECT, Schema.EMPTY, Set.of(CATEGORY), Set.of(), Set.of(), Map.of(),
                Map.of(), Set.of(EVENT), EffectOperation.DESCRIPTOR_EFFECT, 0);
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(effect)));
    }

    @Test
    void snapshotAndContractCountBoundaries() {
        List<Contract> descriptors = IntStream.rangeClosed(0, MAX_FRONTEND_DESCRIPTORS)
                .mapToObj(index -> (Contract) new AppearanceContract(key(DescriptorDomain.APPEARANCE, "a" + index),
                        Schema.EMPTY, Set.of(CATEGORY), MAX_FRONTEND_COST)).toList();
        assertEquals(MAX_FRONTEND_DESCRIPTORS,
                new Snapshot(descriptors.subList(0, MAX_FRONTEND_DESCRIPTORS)).fullPolicy().enabledDescriptors().size());
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(descriptors));
        assertThrows(IllegalArgumentException.class,
                () -> new AppearanceContract(key(DescriptorDomain.APPEARANCE, "a"), Schema.EMPTY,
                        Set.of(CATEGORY), MAX_FRONTEND_COST + 1));

        assertEquals(MAX_FRONTEND_FIELDS, new Schema(fields(MAX_FRONTEND_FIELDS)).fields().size());
        assertThrows(IllegalArgumentException.class, () -> new Schema(fields(MAX_FRONTEND_FIELDS + 1)));
        var activation = key(DescriptorDomain.ACTIVATION, "a");
        assertEquals(MAX_FRONTEND_ARGUMENTS, new ActivationContract(activation,
                new Schema(fields(MAX_FRONTEND_ARGUMENTS)), Set.of(CATEGORY), Set.of(), Map.of(), 0)
                .arguments().fields().size());
        assertThrows(IllegalArgumentException.class, () -> new ActivationContract(activation,
                new Schema(fields(MAX_FRONTEND_ARGUMENTS + 1)), Set.of(CATEGORY), Set.of(), Map.of(), 0));

        Set<String> capabilities = tags("cap", MAX_FRONTEND_CAPABILITIES);
        var maximum = backend(BACKEND, Set.of(), capabilities, Set.of(), Set.of(), 0);
        assertEquals(MAX_FRONTEND_CAPABILITIES, new Snapshot(List.of(maximum)).fullPolicy().enabledCapabilities().size());
        assertThrows(IllegalArgumentException.class, () -> new Snapshot(List.of(maximum,
                backend(key(DescriptorDomain.BACKEND, "other"), Set.of(), Set.of("test:extra"), Set.of(), Set.of(), 0))));
        assertThrows(IllegalArgumentException.class, () -> backend(BACKEND, Set.of(),
                tags("cap", MAX_FRONTEND_CAPABILITIES + 1), Set.of(), Set.of(), 0));

        assertEquals(MAX_FRONTEND_CONTEXT_KEYS, new ActivationContract(activation, Schema.EMPTY,
                tags("category", MAX_FRONTEND_CONTEXT_KEYS), Set.of(), Map.of(), 0).categories().size());
        assertThrows(IllegalArgumentException.class, () -> new ActivationContract(activation, Schema.EMPTY,
                tags("category", MAX_FRONTEND_CONTEXT_KEYS + 1), Set.of(), Map.of(), 0));

        Set<CapabilityConflict> conflicts = IntStream.range(0, MAX_FRONTEND_CONFLICTS + 1)
                .mapToObj(index -> new CapabilityConflict("test:cap" + (index / (MAX_FRONTEND_CAPABILITIES - 1)),
                        "test:cap" + (1 + index % (MAX_FRONTEND_CAPABILITIES - 1) + index / (MAX_FRONTEND_CAPABILITIES - 1))))
                .collect(Collectors.toSet());
        // Use pairs inside the existing vocabulary; a missing pair may not mask the count guard.
        var excess = assertThrows(IllegalArgumentException.class,
                () -> backend(BACKEND, Set.of(), capabilities, conflicts, Set.of(), 0));
        assertEquals("capability conflicts exceeded", excess.getMessage());
        var accepted = conflicts.stream().limit(MAX_FRONTEND_CONFLICTS).collect(Collectors.toSet());
        assertEquals(MAX_FRONTEND_CONFLICTS,
                backend(BACKEND, Set.of(), capabilities, accepted, Set.of(), 0).conflicts().size());
    }

    @Test
    void snapshotIdentityBytesRejectFirstByteBeyondCap() {
        int emptyBytes = new Snapshot(List.of()).encodedBytes();
        int shellBytes = new Snapshot(List.of(wideBackend(0, 0))).encodedBytes() - emptyBytes;
        int maxValueBytes = 2 * MAX_FRONTEND_FIELDS * MAX_TEXT_BYTES;
        assertEquals(shellBytes + maxValueBytes,
                new Snapshot(List.of(wideBackend(0, maxValueBytes))).encodedBytes() - emptyBytes);
        int descriptors = Math.ceilDiv(MAX_FRONTEND_BYTES + 1 - emptyBytes, shellBytes + maxValueBytes);
        int max = MAX_FRONTEND_BYTES - emptyBytes - descriptors * shellBytes;
        assertEquals(MAX_FRONTEND_BYTES, new Snapshot(wideBackends(descriptors, max)).encodedBytes());
        var exceeded = assertThrows(IllegalArgumentException.class,
                () -> new Snapshot(wideBackends(descriptors, max + 1)));
        assertEquals("frontend identity bytes exceeded", exceeded.getMessage());
    }

    private static List<BackendContract> wideBackends(int descriptors, int payloadBytes) {
        var values = new ArrayList<BackendContract>(descriptors);
        for (int index = 0; index < descriptors; index++) {
            int bodyBytes = Math.min(payloadBytes, 2 * MAX_FRONTEND_FIELDS * MAX_TEXT_BYTES);
            values.add(wideBackend(index, bodyBytes));
            payloadBytes -= bodyBytes;
        }
        assertEquals(0, payloadBytes);
        return values;
    }

    private static BackendContract wideBackend(int index, int bytes) {
        int settings = Math.min(bytes, MAX_FRONTEND_FIELDS * MAX_TEXT_BYTES);
        var key = key(DescriptorDomain.BACKEND, String.format(Locale.ROOT, "wide%02d", index));
        return new BackendContract(key, CATEGORY, Schema.EMPTY, Set.of(), Set.of(), Set.of(),
                wideFields(settings), wideFields(bytes - settings), Set.of(), 0);
    }

    private static Schema wideFields(int bytes) {
        var fields = new LinkedHashMap<String, Field>();
        for (int index = 0; index < MAX_FRONTEND_FIELDS; index++) {
            String symbol = ("field" + index + "_".repeat(MAX_SYMBOL_BYTES)).substring(0, MAX_SYMBOL_BYTES);
            int valueBytes = Math.min(bytes, MAX_TEXT_BYTES);
            fields.put(symbol, Field.defaulted(new TextValue("v".repeat(valueBytes))));
            bytes -= valueBytes;
        }
        assertEquals(0, bytes);
        return new Schema(fields);
    }

    private static Map<String, Field> fields(int count) {
        return IntStream.range(0, count).boxed().collect(Collectors.toMap(
                index -> "field" + index, index -> Field.required(ValueType.INTEGER)));
    }

    private static Set<String> tags(String stem, int count) {
        return IntStream.range(0, count).mapToObj(index -> "test:" + stem + index).collect(Collectors.toSet());
    }

    private static BackendContract backend(DescriptorKey key, Set<String> required, Set<String> provided,
                                            Set<CapabilityConflict> conflicts, Set<DescriptorKey> events, int cost) {
        return new BackendContract(key, CATEGORY, Schema.EMPTY, required, provided, conflicts,
                Schema.EMPTY, Schema.EMPTY, events, cost);
    }

    private static DescriptorKey key(DescriptorDomain domain, String tag) {
        return new DescriptorKey(domain, "test:" + tag, 1);
    }
}
