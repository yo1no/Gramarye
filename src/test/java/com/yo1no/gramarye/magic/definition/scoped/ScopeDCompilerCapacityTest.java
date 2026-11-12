package com.yo1no.gramarye.magic.definition.scoped;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDCompilerFixtures.OWNER;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeDCompilerCapacityTest {
    private static final String CATEGORY = "gramarye:compiler_fixture_capacity";
    private static final DescriptorKey BACKEND = new DescriptorKey(DescriptorDomain.BACKEND, CATEGORY, 1);
    private static final DescriptorKey EFFECT = new DescriptorKey(DescriptorDomain.EFFECT, CATEGORY, 1);
    private static final DescriptorKey ACTIVATION = new DescriptorKey(DescriptorDomain.ACTIVATION, CATEGORY, 1);
    private static final DescriptorKey APPEARANCE = new DescriptorKey(DescriptorDomain.APPEARANCE, CATEGORY, 1);

    @Test
    void fullExactClosureReachesAllSourceRangesAndAuthoredExpressionMaximum() throws Exception {
        var contracts = new ArrayList<Contract>();
        var eventKeys = new ArrayList<DescriptorKey>();
        for (int index = 0; index < MAX_HANDLERS; index++) {
            var key = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:compiler_fixture_capacity" + index, 1);
            eventKeys.add(key);
            contracts.add(new EventContract(key, Set.of(CATEGORY), Set.of(), Map.of(), EventOrigin.EXTERNAL,
                    Optional.of(new CreateContract(Set.of(), Map.of(), 1)), 1));
        }
        var settings = new ArrayList<Setting>();
        var settingSchema = new HashMap<String, Field>();
        var inputs = new HashMap<String, Field>();
        for (int index = 0; index < MAX_SETTINGS; index++) {
            String name = "setting" + index;
            String input = "input" + index;
            settingSchema.put(name, Field.required(ValueType.INTEGER));
            inputs.put(input, Field.defaulted(new BooleanValue(false)));
            settings.add(new Setting(name, new Select(ValueType.INTEGER, new CreationInput(input, ValueType.BOOLEAN),
                    new Literal(new IntegerValue(1)), new Literal(new IntegerValue(2)))));
        }
        var events = new HashSet<>(eventKeys);
        contracts.add(new BackendContract(BACKEND, CATEGORY, Schema.EMPTY, Set.of(), Set.of(), Set.of(),
                new Schema(settingSchema), new Schema(inputs), events, 1));
        contracts.add(new ActivationContract(ACTIVATION, Schema.EMPTY, Set.of(CATEGORY), Set.of(), Map.of(), 1));
        contracts.add(new AppearanceContract(APPEARANCE, Schema.EMPTY, Set.of(CATEGORY), 1));
        contracts.add(new EffectContract(EFFECT, Schema.EMPTY, Set.of(CATEGORY), events, Set.of(), Map.of(), Map.of(),
                Set.of(), EffectOperation.DESCRIPTOR_EFFECT, 1));
        var frontend = new Snapshot(contracts);
        var context = new Context(frontend, frontend.fullPolicy());
        var routes = new HashMap<Identity, ExactReference>();
        var revisions = new HashMap<ExactReference, FormalDocument>();
        var watermarks = new HashMap<Identity, ScopeDCatalogImage.LocalHighWater>();
        for (int spell = 1; spell <= MAX_ROUTES; spell++) {
            var exact = reference(spell);
            var handlers = new ArrayList<Handler>();
            for (int handler = 0; handler < MAX_HANDLERS; handler++) {
                var blocks = new ArrayList<Block>();
                for (int block = 0; block < MAX_BLOCKS / MAX_HANDLERS; block++) {
                    var id = new BlockId((long) handler * (MAX_BLOCKS / MAX_HANDLERS) + block + 1);
                    blocks.add(handler == 0 && block == 0 && spell < MAX_ROUTES
                            ? new CreateSpell(id, new SpellIdentityReference(reference(spell + 1).identity()))
                            : new EffectBlock(id, descriptor(EFFECT)));
                }
                handlers.add(new Handler(new HandlerId(handler + 1), eventKeys.get(handler).tag(), blocks));
            }
            var definition = new SpellDefinition(exact, body(settings, handlers), Optional.empty());
            routes.put(exact.identity(), exact);
            revisions.put(exact, definition);
            watermarks.put(exact.identity(), new ScopeDCatalogImage.LocalHighWater(MAX_HANDLERS, MAX_BLOCKS, 0));
        }
        var generation = new CatalogGeneration(1);
        var image = new ScopeDCatalogImage(OWNER, generation, routes, revisions, watermarks, Set.of());
        assertTrue(image.encode().length <= MAX_CATALOG_PAYLOAD);
        var result = ScopeDCompiler.compile(new UUID(0, 17), image,
                new ScopeDResolver.StandaloneSpellRoot(reference(1)), generation, context);
        var program = assertInstanceOf(ScopeDCompiler.Compiled.class, result, result::toString).program();
        assertEquals(MAX_COMPILER_EXACT_BINDINGS, program.cost().spells());
        assertEquals(MAX_COMPILER_CREATION_INPUTS, program.cost().units().creationInputs());
        assertEquals(MAX_ROUTES * MAX_EXPRESSIONS, program.cost().units().expressions());
        assertEquals(MAX_COMPILER_SETTINGS, program.cost().units().settings());
        assertEquals(MAX_COMPILER_HANDLERS, program.cost().units().handlers());
        assertEquals(MAX_COMPILER_BLOCKS, program.cost().units().blocks());
        assertEquals(MAX_COMPILER_SOURCE_ENTRIES, program.cost().units().sourceEntries());
        assertEquals(MAX_ROUTES * (MAX_EXPRESSIONS + MAX_BLOCKS), program.cost().units().instructions());

        var oversized = new ArrayList<>(settings);
        oversized.set(0, new Setting("setting0", new Equal(new Literal(new IntegerValue(1)), new Literal(new IntegerValue(2)))));
        oversized.set(1, new Setting("setting1", new Equal(new Literal(new IntegerValue(1)), settings.get(1).expression())));
        assertThrows(IllegalArgumentException.class, () -> body(oversized, List.of()));
        var overflow = reference(MAX_ROUTES + 1);
        routes.put(overflow.identity(), overflow);
        revisions.put(overflow, new SpellDefinition(overflow, body(List.of(), List.of()), Optional.empty()));
        watermarks.put(overflow.identity(), ScopeDCatalogImage.LocalHighWater.EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> new ScopeDCatalogImage(OWNER, generation, routes, revisions, watermarks, Set.of()));
    }

    private static SpellBody body(List<Setting> settings, List<Handler> handlers) {
        return new SpellBody(new Known<>(new Backend(descriptor(BACKEND), CATEGORY, List.of())),
                new Known<>(descriptor(ACTIVATION)), new Known<>(settings), new Known<>(handlers),
                new Known<>(descriptor(APPEARANCE)), new Absent<>());
    }

    private static Descriptor descriptor(DescriptorKey key) { return new Descriptor(key.tag(), key.version(), List.of()); }

    private static SpellReference reference(int id) {
        return new SpellReference(new SpellIdentity(OWNER, new SpellDefinitionId(id)), new SpellRevision(1));
    }
}
