package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;

final class ScopeDCompilerFixtures {
    static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(0, 103));
    static final String CATEGORY = "gramarye:compiler_fixture_category";
    static final String CAPABILITY = "gramarye:compiler_fixture_capability";
    static final DescriptorKey BACKEND = key(DescriptorDomain.BACKEND, "backend");
    static final DescriptorKey ACTIVATE = key(DescriptorDomain.ACTIVATION, "activate");
    static final DescriptorKey EXTERNAL = key(DescriptorDomain.EVENT, "external");
    static final DescriptorKey CREATED = key(DescriptorDomain.EVENT, "created");
    static final DescriptorKey EFFECT = key(DescriptorDomain.EFFECT, "effect");
    static final DescriptorKey EMIT = key(DescriptorDomain.EFFECT, "emit");
    static final DescriptorKey APPEAR = key(DescriptorDomain.APPEARANCE, "appearance");
    static final Context DEFAULT = context(EventOrigin.EXTERNAL, 1);

    static Context context(EventOrigin externalOrigin, int backendCost) {
        var schema = new Schema(Map.of("flag", Field.optional(ValueType.BOOLEAN), "value", Field.optional(ValueType.INTEGER)));
        var inputs = new Schema(Map.of("seed", Field.defaulted(new IntegerValue(3)),
                "optional", Field.optional(ValueType.INTEGER)));
        var eventFacts = Map.of("seed", ValueType.INTEGER);
        var creation = Optional.of(new CreateContract(Set.of(), eventFacts, 1));
        var events = Set.of(EXTERNAL, CREATED);
        var contracts = List.of(
                new BackendContract(BACKEND, CATEGORY, Schema.EMPTY, Set.of(), Set.of(CAPABILITY), Set.of(),
                        schema, inputs, events, backendCost),
                new ActivationContract(ACTIVATE, Schema.EMPTY, Set.of(CATEGORY), Set.of(), eventFacts, 2),
                new EventContract(EXTERNAL, Set.of(CATEGORY), Set.of(), eventFacts, externalOrigin, creation, 3),
                new EventContract(CREATED, Set.of(CATEGORY), Set.of(), eventFacts, EventOrigin.CREATION, creation, 4),
                new EffectContract(EFFECT, new Schema(Map.of("amount", Field.required(ValueType.INTEGER))),
                        Set.of(CATEGORY), events, Set.of(CAPABILITY), eventFacts, eventFacts, Set.of(),
                        EffectOperation.DESCRIPTOR_EFFECT, 5),
                new EffectContract(EMIT, Schema.EMPTY, Set.of(CATEGORY), events, Set.of(), Map.of(), Map.of(),
                        Set.of(EXTERNAL), EffectOperation.DESCRIPTOR_EFFECT, 6),
                new AppearanceContract(APPEAR, Schema.EMPTY, Set.of(CATEGORY), 7));
        var snapshot = new Snapshot(contracts);
        return new Context(snapshot, snapshot.fullPolicy());
    }

    static SpellDefinition spell(SpellReference reference, List<Handler> handlers) {
        return spell(reference, List.of(), handlers, new Absent<>(), new Absent<>());
    }

    static SpellDefinition spell(SpellReference reference, List<Setting> settings, List<Handler> handlers,
                                 Slot<Descriptor> activation, Slot<Descriptor> appearance) {
        var body = new SpellBody(new Known<>(new Backend(descriptor(BACKEND), CATEGORY, List.of(CAPABILITY))),
                activation, new Known<>(settings), new Known<>(handlers), appearance, new Absent<>());
        return new SpellDefinition(reference, body, Optional.empty());
    }

    static Handler handler(long id, List<Block> blocks) { return handler(id, EXTERNAL, blocks); }

    static Handler handler(long id, DescriptorKey event, List<Block> blocks) {
        return new Handler(new HandlerId(id), event.tag(), event.version(), blocks);
    }

    static EffectBlock effect(long id) {
        return new EffectBlock(new BlockId(id), new Descriptor(EFFECT.tag(), EFFECT.version(),
                List.of(new Argument("amount", new IntegerValue(id)))));
    }

    static CreateSpell create(long id, SpellIdentity target) {
        return new CreateSpell(new BlockId(id), new SpellIdentityReference(target));
    }

    static SkillManifest manifest(ManifestReference reference, List<SpellReference> targets) {
        var entries = new ArrayList<ManifestEntry>();
        for (var target : targets) {
            entries.add(new ManifestEntry(new ManifestEntryId(entries.size() + 1L),
                    new SpellIdentityReference(target.identity()), "", ""));
        }
        return new SkillManifest(reference, new ManifestBody(new Known<>(entries), "", false), Optional.empty());
    }

    static Descriptor descriptor(DescriptorKey key) { return new Descriptor(key.tag(), key.version(), List.of()); }

    private static DescriptorKey key(DescriptorDomain domain, String suffix) {
        return new DescriptorKey(domain, "gramarye:compiler_fixture_" + suffix, 1);
    }

    private ScopeDCompilerFixtures() {}
}
