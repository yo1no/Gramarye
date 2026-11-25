package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogTestAuthority;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

final class V0NormalBootstrapTest {
    private static final SkillOwnerId A = new SkillOwnerId(new UUID(0, 901));
    private static final SkillOwnerId B = new SkillOwnerId(new UUID(0, 902));
    @TempDir Path world;

    @Test
    void constructorAndMainThreadRefusalPerformNoFilesystemWork() {
        var authority = V0CatalogTestAuthority.open(4);
        Path source = source();
        var catalog = V0RuntimeCatalog.openNormal(authority, source);
        assertFalse(catalog.ensureNormalBootstrap(authority, A).ready());
        assertFalse(Files.exists(source));
        assertFalse(Files.exists(source.getParent()));
    }

    @Test
    void realAllocatorForkReadbackAndReusePreserveOwnerPrivateIdentity() throws Exception {
        Files.createDirectories(source());
        // Burn an actual allocation before provisioning; no test or production path assumes ID257.
        try (var storage = ScopeDSource.open(source(), A)) {
            assertEquals(1, storage.reserve());
        }
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            var a = cold.call(() -> catalog.ensureNormalBootstrap(authority, A));
            var b = cold.call(() -> catalog.ensureNormalBootstrap(authority, B));
            assertTrue(a.ready());
            assertTrue(b.ready());
            var first = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, a.selection().orElseThrow(), 4)));
            assertEquals(2, first.lease().root().hitDamage().orElseThrow().damageHp());
            assertTrue(first.lease().root().hitCreate().isEmpty());
            assertEquals(Disposition.RELEASED, catalog.discard(authority, first));
            assertFalse(cold.call(() -> catalog.ensureNormalBootstrap(authority, V0NormalBootstrap.SEED)).ready());
            var again = cold.call(() -> catalog.ensureNormalBootstrap(authority, A));
            assertTrue(again.ready());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
        var seed = read(V0NormalBootstrap.SEED);
        var player = read(A);
        var other = read(B);
        var seedSpell = assertInstanceOf(SpellDefinition.class, seed.currentBodies().values().iterator().next());
        var aSpell = assertInstanceOf(SpellDefinition.class, player.currentBodies().values().iterator().next());
        var bSpell = assertInstanceOf(SpellDefinition.class, other.currentBodies().values().iterator().next());
        assertEquals(513, aSpell.reference().identity().id().value());
        assertEquals(2, aSpell.reference().revision().value());
        assertEquals(1, player.generation().value());
        assertEquals(A, aSpell.identity().owner());
        assertEquals(B, bSpell.identity().owner());
        assertNotEquals(aSpell.reference(), bSpell.reference());
        assertEquals(V0NormalBootstrap.body(), aSpell.body());
        assertTrue(seedSpell.provenance().isEmpty());
        assertEquals(seedSpell.reference(), aSpell.provenance().orElseThrow().source());
        assertEquals(seed.generation(), aSpell.provenance().orElseThrow().generation());
        assertEquals(aSpell.provenance(), bSpell.provenance());
        assertNotEquals(V0NormalBootstrap.digest(seedSpell), V0NormalBootstrap.digest(aSpell));
        assertEquals(3, admitted());
    }

    @Test
    void matchingPreparedForkRecoversItsOriginalAllocationWithoutRepublishingSeed() throws Exception {
        Files.createDirectories(source());
        var context = V0NormalBootstrap.context();
        SpellReference original;
        try (var seedStorage = ScopeDSource.open(source(), V0NormalBootstrap.SEED)) {
            var seed = new ScopeDCatalog(V0NormalBootstrap.SEED, seedStorage);
            var reservation = seed.reserve();
            var reference = reservation.newSpell(1);
            var prepared = seed.prepare(reservation, context.catalogContext(), List.of(V0NormalBootstrap.seed(reference)));
            publish(seed, prepared, context);
            var bundle = seed.exportFork(context.catalogContext(), reference);
            try (var storage = ScopeDSource.open(source(), A)) {
                var player = new ScopeDCatalog(A, storage);
                var allocation = player.reserve();
                original = allocation.newSpell(1);
                player.stage(player.prepareFork(allocation, context.catalogContext(), bundle), context.catalogContext());
            }
        }
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            assertTrue(cold.call(() -> catalog.ensureNormalBootstrap(authority, A)).ready());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
        assertEquals(Set.of(original), Set.copyOf(read(A).routes().values()));
        assertEquals(1, read(V0NormalBootstrap.SEED).generation().value());
        assertEquals(1, read(A).generation().value());
    }

    @Test
    void differentBodyAndForeignMaterialArePreservedRatherThanResetOrMerged() throws Exception {
        Files.createDirectories(source());
        var context = V0NormalBootstrap.context();
        try (var storage = ScopeDSource.open(source(), A)) {
            var player = new ScopeDCatalog(A, storage);
            var allocation = player.reserve();
            var spell = V0NormalBootstrap.seed(allocation.newSpell(1));
            // This is legitimate owner content with no seed provenance, not a Bootstrap copy.
            publish(player, player.prepare(allocation, context.catalogContext(), List.of(spell)), context);
        }
        byte[] original = Files.readAllBytes(source().resolve(A.value().toString()).resolve("catalog.current"));
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            var result = cold.call(() -> catalog.ensureNormalBootstrap(authority, A));
            assertFalse(result.ready());
            assertEquals(Refusal.ROOT_UNAVAILABLE, result.refusal().orElseThrow());
            assertArrayEquals(original, Files.readAllBytes(source().resolve(A.value().toString()).resolve("catalog.current")));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void ordinaryFrontendAcceptsAnotherLegalServerSelectedRootWithoutBootstrapProvenance() throws Exception {
        Files.createDirectories(source());
        var context = V0NormalBootstrap.context();
        SpellReference selected;
        try (var storage = ScopeDSource.open(source(), A)) {
            var player = new ScopeDCatalog(A, storage);
            var allocation = player.reserve();
            selected = allocation.newSpell(1);
            publish(player, player.prepare(allocation, context.catalogContext(),
                    List.of(new SpellDefinition(selected, damageBody(3), Optional.empty()))), context);
        }
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            assertTrue(catalog.stopBootstrap(authority));
            var candidate = assertInstanceOf(Candidate.class, cold.call(() -> catalog.prepare(authority, 1,
                    A, Selection.standalone(selected.identity().id().value()), 4)));
            assertEquals(3, candidate.lease().root().hitDamage().orElseThrow().damageHp());
            assertEquals(Disposition.RELEASED, catalog.discard(authority, candidate));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void changedCurrentRouteInvalidatesTheBootstrapHintWithoutRevokingAnAcceptedExactLease() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            var readiness = cold.call(() -> catalog.ensureNormalBootstrap(authority, A));
            var selected = readiness.selection().orElseThrow();
            var accepted = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selected, 4)));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, accepted), accepted));
            var current = cold.call(() -> catalog.engineeringReadback(authority, A));
            var original = (SpellDefinition) current.currentBodies().values().iterator().next();
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, new PublicationPlan() {
                @Override
                List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
                    return List.of(new SpellDefinition(reservation.nextSpell(original.reference().identity()),
                            damageBody(3), original.provenance()));
                }
            })));
            assertTrue(catalog.usable(authority, accepted.lease()));
            assertEquals(2, accepted.lease().root().hitDamage().orElseThrow().damageHp());
            var stale = assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selected, 4)));
            assertEquals(Refusal.ROOT_UNAVAILABLE, stale.reason());
            assertFalse(cold.call(() -> catalog.ensureNormalBootstrap(authority, A)).ready());
            assertTrue(catalog.usable(authority, accepted.lease()));
            assertEquals(Disposition.RELEASED, catalog.release(
                    V0CatalogTestAuthority.release(authority, accepted.lease()), accepted.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void seedConsumesOneRosterSlotAndTheThirtySecondPlayerIsRefusedWithoutEviction() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            for (int index = 1; index <= 31; index++) {
                var owner = new SkillOwnerId(new UUID(0, 10_000 + index));
                assertTrue(cold.call(() -> catalog.ensureNormalBootstrap(authority, owner)).ready());
            }
            var overflow = new SkillOwnerId(new UUID(0, 10_032));
            var result = cold.call(() -> catalog.ensureNormalBootstrap(authority, overflow));
            assertEquals(Refusal.CAPACITY, result.refusal().orElseThrow());
            assertFalse(Files.exists(source().resolve(overflow.value().toString())));
            assertEquals(32, admitted());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void sunsetBarrierRefusesProvisionAndOldBootstrapHintsButKeepsTheGenericFrontend() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = V0RuntimeCatalog.openNormal(authority, source());
        try (var cold = new Cold()) {
            var readiness = cold.call(() -> catalog.ensureNormalBootstrap(authority, A));
            var current = cold.call(() -> catalog.engineeringReadback(authority, A));
            var exact = (SpellReference) current.routes().values().iterator().next();
            assertTrue(catalog.stopBootstrap(authority));
            assertFalse(cold.call(() -> catalog.ensureNormalBootstrap(authority, B)).ready());
            assertEquals(Refusal.INVALID_SELECTION, assertInstanceOf(Rejected.class, cold.call(() ->
                    catalog.prepare(authority, 1, A, readiness.selection().orElseThrow(), 4))).reason());
            assertEquals(Refusal.ROOT_UNAVAILABLE, assertInstanceOf(Rejected.class, cold.call(() ->
                    catalog.prepare(authority, 2, A, Selection.standalone(exact.identity().id().value()), 4))).reason());
            assertEquals(BootstrapCleanup.REFUSED,
                    cold.call(() -> catalog.cleanupNormalBootstrap(authority, A, null)));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    private Path source() { return world.resolve("gramarye").resolve("spell_catalog"); }

    private ScopeDCatalogImage read(SkillOwnerId owner) throws Exception {
        try (var storage = ScopeDSource.open(source(), owner)) {
            return new ScopeDCatalog(owner, storage).current();
        }
    }

    private long admitted() throws Exception {
        try (var entries = Files.list(source())) {
            return entries.filter(path -> path.getFileName().toString().endsWith(".admitted")).count();
        }
    }

    private static void publish(ScopeDCatalog catalog, ScopeDCatalog.Prepared prepared,
                                ScopeDFrontend.Context context) throws Exception {
        catalog.publish(prepared, context, catalog.verifyCompatibility(prepared, context).proof().orElseThrow());
    }

    private static SpellBody damageBody(int hp) {
        var body = V0NormalBootstrap.body();
        var effect = new EffectBlock(new BlockId(1), new Descriptor("gramarye:v0_hit_damage", 1,
                List.of(new Argument("damage_hp", new IntegerValue(hp)))));
        return new SpellBody(body.backend(), body.activation(), body.settings(),
                new Known<>(List.of(new Handler(new HandlerId(1), "gramarye:on_create", 1, List.of()),
                        new Handler(new HandlerId(2), "gramarye:on_hit", 1, List.of(effect)))),
                body.appearance(), body.phenomenon());
    }

    private static final class Cold implements AutoCloseable {
        private final ExecutorService worker = Executors.newSingleThreadExecutor();

        private <T> T call(Callable<T> action) throws Exception { return worker.submit(action).get(5, TimeUnit.SECONDS); }

        @Override
        public void close() { worker.close(); }
    }
}
