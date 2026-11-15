package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogAuthority;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;

/** The alternate distribution's closed compiler context and publication recipe. */
public final class V0EngineeringCatalogAssembly {
    private static final String CATEGORY = "gramarye:projectile";
    private static final String CAPABILITY = "gramarye:v0_projectile";
    private static final DescriptorKey BACKEND = new DescriptorKey(DescriptorDomain.BACKEND, CAPABILITY, 1);
    private static final DescriptorKey ACTIVATE = new DescriptorKey(DescriptorDomain.ACTIVATION, "gramarye:v0_engineering", 1);
    private static final DescriptorKey CREATED = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_create", 1);
    private static final DescriptorKey HIT = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_hit", 1);
    private static final DescriptorKey DAMAGE = new DescriptorKey(DescriptorDomain.EFFECT, "gramarye:v0_hit_damage", 1);
    private static final DescriptorKey APPEARANCE = new DescriptorKey(DescriptorDomain.APPEARANCE, "gramarye:v0_profile_triple", 1);
    private static final Map<String, ValueType> LAUNCH = Map.of("launch_step_milli", ValueType.INTEGER);

    private V0EngineeringCatalogAssembly() {}

    public static V0RuntimeCatalog open(Path qualifiedSource, V0CatalogAuthority authority) {
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
        return new V0RuntimeCatalog(authority, qualifiedSource, new Context(snapshot, snapshot.fullPolicy()));
    }

    public static FixturePublication publishFixture(V0RuntimeCatalog catalog, V0CatalogAuthority authority,
            SkillOwnerId owner, FixtureRevision revision) {
        if (catalog == null || authority == null || owner == null || revision == null) {
            return FixturePublication.REJECTED;
        }
        try {
            var current = catalog.engineeringReadback(authority, owner);
            if (current == null) {
                return FixturePublication.BUSY;
            }
            var fixture = new Fixture(owner);
            boolean updated = revision == FixtureRevision.UPDATED_CHILD;
            if (!current.routes().isEmpty()) {
                if (fixture.matches(current, true) || !updated && fixture.matches(current, false)) {
                    return FixturePublication.PUBLISHED;
                }
                if (!updated || !fixture.matches(current, false)) {
                    return FixturePublication.REJECTED;
                }
            } else if (updated) {
                return FixturePublication.REJECTED;
            }
            if (!catalog.publishFixture(authority, owner, new Material(fixture, updated))) {
                return FixturePublication.BUSY;
            }
            var published = catalog.engineeringReadback(authority, owner);
            return published != null && fixture.matches(published, updated)
                    ? FixturePublication.PUBLISHED : FixturePublication.SOURCE_UNPROVED;
        } catch (IOException failure) {
            return FixturePublication.SOURCE_UNPROVED;
        }
    }

    public enum FixtureRevision { BASE, UPDATED_CHILD }

    public enum FixturePublication { PUBLISHED, SOURCE_UNPROVED, BUSY, REJECTED }

    private static final class Fixture {
        private final SpellIdentity root;
        private final SpellIdentity child;
        private final ManifestIdentity manifest;
        private final SpellIdentity second;

        private Fixture(SkillOwnerId owner) {
            root = spellIdentity(owner, 1);
            child = spellIdentity(owner, 2);
            manifest = new ManifestIdentity(owner, new SkillManifestId(ScopeDLimits.ID_STRIDE + 3));
            second = spellIdentity(owner, 4);
        }

        private static SpellIdentity spellIdentity(SkillOwnerId owner, int ordinal) {
            return new SpellIdentity(owner, new SpellDefinitionId(ScopeDLimits.ID_STRIDE + ordinal));
        }

        private boolean matches(ScopeDCatalogImage image, boolean updated) {
            var rows = image.currentBodies();
            return rows.size() == 4 && image.externalRoots().isEmpty()
                    && rows.get(root) instanceof SpellDefinition a && a.equals(spell(a.reference(), true, child, false))
                    && rows.get(child) instanceof SpellDefinition b && b.equals(spell(b.reference(), false, null, updated))
                    && rows.get(second) instanceof SpellDefinition c && c.equals(spell(c.reference(), true, null, false))
                    && rows.get(manifest) instanceof SkillManifest d && d.equals(manifest(d.reference(), root, second));
        }
    }

    private static final class Material extends V0RuntimeCatalog.PublicationPlan {
        private final Fixture fixture;
        private final boolean update;

        private Material(Fixture fixture, boolean update) {
            this.fixture = fixture;
            this.update = update;
        }

        @Override
        List<FormalDocument> material(ScopeDCatalog.Reservation reservation) throws IOException {
            if (update) {
                return List.of(spell(reservation.nextSpell(fixture.child), false, null, true));
            }
            var root = reservation.newSpell(1);
            var child = reservation.newSpell(2);
            var manifest = reservation.newManifest(3);
            var second = reservation.newSpell(4);
            if (!root.identity().equals(fixture.root) || !child.identity().equals(fixture.child)
                    || !manifest.identity().equals(fixture.manifest) || !second.identity().equals(fixture.second)) {
                throw new IOException("Engineering seed does not match its reserved fixture coordinates");
            }
            return List.of(spell(root, true, child.identity(), false), spell(child, false, null, false),
                    spell(second, true, null, false), manifest(manifest, root.identity(), second.identity()));
        }
    }

    private static SkillManifest manifest(ManifestReference reference, SpellIdentity root, SpellIdentity second) {
        return new SkillManifest(reference, new ManifestBody(new Known<>(List.of(
                new ManifestEntry(new ManifestEntryId(1), new SpellIdentityReference(root), "", ""),
                new ManifestEntry(new ManifestEntryId(2), new SpellIdentityReference(second), "", ""))), "", false),
                Optional.empty());
    }

    private static SpellDefinition spell(SpellReference reference, boolean activated, SpellIdentity child, boolean updated) {
        var statements = new ArrayList<Block>();
        var damage = new Descriptor(DAMAGE.tag(), 1,
                List.of(new Argument("damage_hp", new IntegerValue(updated ? 3 : 2))));
        statements.add(new EffectBlock(new BlockId(1), damage));
        if (child != null) {
            statements.add(new CreateSpell(new BlockId(2), new SpellIdentityReference(child)));
        }
        var profile = new Descriptor(APPEARANCE.tag(), 1, List.of(
                new Argument("sound_profile", new TextValue(updated ? "gramarye:v0_fixture_missing" : "gramarye:default_sound")),
                new Argument("particle_profile", new TextValue("gramarye:default_particle")),
                new Argument("trail_profile", new TextValue("gramarye:default_trail"))));
        return new SpellDefinition(reference, new SpellBody(
                new Known<>(new Backend(descriptor(BACKEND), CATEGORY, List.of(CAPABILITY))),
                activated ? new Known<>(descriptor(ACTIVATE)) : new Absent<>(), new Known<>(List.of()),
                new Known<>(List.of(new Handler(new HandlerId(1), CREATED.tag(), 1, List.of()),
                        new Handler(new HandlerId(2), HIT.tag(), 1, statements))),
                new Known<>(profile), new Absent<>()), Optional.empty());
    }

    private static Descriptor descriptor(DescriptorKey key) {
        return new Descriptor(key.tag(), key.version(), List.of());
    }
}
