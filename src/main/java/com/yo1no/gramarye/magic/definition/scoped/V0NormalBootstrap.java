package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;

/** Fixed temporary server policy. The trusted normal frontend is independent of this selector. */
final class V0NormalBootstrap {
    static final SkillOwnerId SEED = new SkillOwnerId(UUID.fromString("10c92d08-46d6-3e19-87ba-3fe5afabf5d7"));
    private static final String CATEGORY = "gramarye:projectile";
    private static final String CAPABILITY = "gramarye:v0_projectile";
    private static final DescriptorKey BACKEND = new DescriptorKey(DescriptorDomain.BACKEND, CAPABILITY, 1);
    private static final DescriptorKey ACTIVATE = new DescriptorKey(DescriptorDomain.ACTIVATION, "gramarye:normal_cast", 1);
    private static final DescriptorKey CREATED = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_create", 1);
    private static final DescriptorKey HIT = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_hit", 1);
    private static final DescriptorKey DAMAGE = new DescriptorKey(DescriptorDomain.EFFECT, "gramarye:v0_hit_damage", 1);
    private static final DescriptorKey APPEARANCE = new DescriptorKey(DescriptorDomain.APPEARANCE, "gramarye:v0_profile_triple", 1);
    private static final Map<String, ValueType> LAUNCH = Map.of("launch_step_milli", ValueType.INTEGER);

    private V0NormalBootstrap() { }

    static Context context() {
        var snapshot = new Snapshot(List.of(
                new BackendContract(BACKEND, CATEGORY, Schema.EMPTY, Set.of(CAPABILITY), Set.of(CAPABILITY),
                        Set.of(), Schema.EMPTY, new Schema(Map.of("launch_step_milli", Field.required(ValueType.INTEGER))),
                        Set.of(CREATED, HIT), 1),
                new ActivationContract(ACTIVATE, Schema.EMPTY, Set.of(CATEGORY), Set.of(CAPABILITY), LAUNCH, 1),
                new EventContract(CREATED, Set.of(CATEGORY), Set.of(CAPABILITY), Map.of(),
                        EventOrigin.CREATION, Optional.empty(), 1),
                new EventContract(HIT, Set.of(CATEGORY), Set.of(CAPABILITY), LAUNCH, EventOrigin.EXTERNAL,
                        Optional.of(new CreateContract(Set.of(CAPABILITY), LAUNCH, 1)), 1),
                new EffectContract(DAMAGE, new Schema(Map.of("damage_hp", Field.defaulted(new IntegerValue(2)))),
                        Set.of(CATEGORY), Set.of(HIT), Set.of(CAPABILITY), Map.of(), Map.of(), Set.of(),
                        EffectOperation.DESCRIPTOR_EFFECT, 1),
                new AppearanceContract(APPEARANCE, new Schema(Map.of("sound_profile", Field.required(ValueType.TEXT),
                        "particle_profile", Field.required(ValueType.TEXT), "trail_profile", Field.required(ValueType.TEXT))),
                        Set.of(CATEGORY), 1)));
        return new Context(snapshot, snapshot.fullPolicy());
    }

    static SpellDefinition seed(SpellReference reference) {
        return new SpellDefinition(reference, body(), Optional.empty());
    }

    static SpellBody body() {
        var damage = new EffectBlock(new BlockId(1), new Descriptor(DAMAGE.tag(), 1,
                List.of(new Argument("damage_hp", new IntegerValue(2)))));
        var handlers = List.of(new Handler(new HandlerId(1), CREATED.tag(), 1, List.of()),
                new Handler(new HandlerId(2), HIT.tag(), 1, List.of(damage)));
        var appearance = new Descriptor(APPEARANCE.tag(), 1, List.of(
                new Argument("sound_profile", new TextValue("gramarye:default_sound")),
                new Argument("particle_profile", new TextValue("gramarye:default_particle")),
                new Argument("trail_profile", new TextValue("gramarye:default_trail"))));
        return new SpellBody(new Known<>(new Backend(descriptor(BACKEND), CATEGORY, List.of(CAPABILITY))),
                new Known<>(descriptor(ACTIVATE)), new Known<>(List.of()), new Known<>(handlers),
                new Known<>(appearance), new Absent<>());
    }

    static boolean genesis(ScopeDCatalogImage image) {
        return image.generation().value() == 0 && image.routes().isEmpty() && image.revisions().isEmpty()
                && image.externalRoots().isEmpty();
    }

    static SpellDefinition singleton(ScopeDCatalogImage image, Optional<ForkProvenance> provenance) {
        if (image.routes().size() != 1 || image.revisions().size() != 1 || !image.externalRoots().isEmpty()) {
            return null;
        }
        var document = image.currentBodies().values().iterator().next();
        return document instanceof SpellDefinition spell && matches(spell, provenance) ? spell : null;
    }

    static boolean matches(SpellDefinition spell, Optional<ForkProvenance> provenance) {
        return spell.body().equals(body()) && spell.provenance().equals(provenance);
    }

    static String digest(SpellDefinition spell) throws IOException {
        return ScopeDCatalog.sha256(ScopeDDataCodec.encode(spell));
    }

    private static Descriptor descriptor(DescriptorKey key) {
        return new Descriptor(key.tag(), key.version(), List.of());
    }
}
