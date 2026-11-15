package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.V0CatalogTestAuthority;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDFrontend.*;
import static com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

final class V0RuntimeCatalogTest {
    private static final SkillOwnerId A = new SkillOwnerId(new UUID(0, 111));
    private static final SkillOwnerId B = new SkillOwnerId(new UUID(0, 222));
    private static final String CATEGORY = "gramarye:projectile";
    private static final String CAPABILITY = "gramarye:v0_projectile";
    private static final DescriptorKey BACKEND = new DescriptorKey(DescriptorDomain.BACKEND, "gramarye:v0_projectile", 1);
    private static final DescriptorKey ACTIVATE = new DescriptorKey(DescriptorDomain.ACTIVATION, "gramarye:v0_engineering", 1);
    private static final DescriptorKey CREATED = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_create", 1);
    private static final DescriptorKey HIT = new DescriptorKey(DescriptorDomain.EVENT, "gramarye:on_hit", 1);
    private static final DescriptorKey DAMAGE = new DescriptorKey(DescriptorDomain.EFFECT, "gramarye:v0_hit_damage", 1);
    private static final DescriptorKey APPEARANCE = new DescriptorKey(DescriptorDomain.APPEARANCE, "gramarye:v0_profile_triple", 1);
    private static final Map<String, ValueType> LAUNCH = Map.of("launch_step_milli", ValueType.INTEGER);
    private static final Context CONTEXT = context();

    @TempDir Path root;

    @Test
    void publicationAndSweepPreserveOnlyExactLiveRevisionsWithoutSerializingPins() throws Exception {
        var authority = V0CatalogTestAuthority.open(8);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 128);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var manifest = Selection.manifest(fixtures.manifest.identity().id().value(), 1);
            var old = assertInstanceOf(Candidate.class, cold.call(() -> catalog.prepare(authority, 1, A, manifest, 8)));
            assertEquals(1_089, old.lease().programChargeBytes());
            assertEquals(2, childDamage(old));
            assertEquals(Acceptance.COMMITTED, catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, old), old));

            var update = new Update(fixtures.child.identity(), 3);
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, update)));
            assertTrue(catalog.usable(authority, old.lease()));
            assertEquals(2, childDamage(old));
            assertTrue(cold.call(() -> catalog.sweep(authority, A)));
            var retained = readImage(root, A);
            assertTrue(retained.revisions().containsKey(fixtures.child));
            assertTrue(retained.externalRoots().isEmpty());

            var successor = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, manifest, 8)));
            assertEquals(3, childDamage(successor));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, successor));
            var receipt = V0CatalogTestAuthority.release(authority, old.lease());
            assertEquals(Disposition.RELEASED, catalog.release(receipt, old.lease()));
            assertEquals(Disposition.STALE_OR_RELEASED, catalog.release(receipt, old.lease()));
            assertTrue(cold.call(() -> catalog.sweep(authority, A)));
            var reclaimed = readImage(root, A);
            assertFalse(reclaimed.revisions().containsKey(fixtures.child));
            assertTrue(reclaimed.revisions().containsKey(update.revision));
            assertTrue(reclaimed.externalRoots().isEmpty());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void publishFirstRejectsOldCandidateAndExactSuspensionCannotReopenSuccessor() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var wrong = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 20);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            Selection selection = Selection.standalone(fixtures.root.identity().id().value());
            var candidate = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selection, 4)));
            assertEquals(Refusal.CATALOG_BUSY, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selection, 4))).reason());
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, new Update(fixtures.child.identity(), 4))));
            assertEquals(Acceptance.INVALID_RESERVATION,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(wrong, candidate), candidate));
            assertEquals(Acceptance.HEAD_STALE,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, candidate), candidate));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, candidate));
            var first = catalog.suspend(authority);
            var successor = catalog.suspend(authority);
            assertFalse(cold.call(() -> catalog.recoverAndReopen(authority, first)));
            assertTrue(cold.call(() -> catalog.recoverAndReopen(authority, successor)));
            assertEquals(Refusal.TICKET_STALE,
                    assertInstanceOf(Rejected.class, cold.call(() -> catalog.prepare(authority, 2, A, selection, 4))).reason());
            assertEquals(Refusal.ROOT_UNAVAILABLE,
                    assertInstanceOf(Rejected.class, cold.call(() -> catalog.prepare(authority, 3, B, selection, 4))).reason());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void rejectsManifestConfiguredMaximumAndSameSizePhysicalCorruption() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(5, 44);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var manifest = Selection.manifest(fixtures.manifest.identity().id().value(), 1);
            assertEquals(Refusal.CAPACITY,
                    assertInstanceOf(Rejected.class, cold.call(() -> catalog.prepare(authority, 1, A, manifest, 4))).reason());
            var standalone = Selection.standalone(fixtures.root.identity().id().value());
            var candidate = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, standalone, 4)));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, candidate), candidate));
            Path current = root.resolve(A.value().toString()).resolve("catalog.current");
            byte[] changed = Files.readAllBytes(current);
            changed[changed.length - 1] ^= 1;
            Files.write(current, changed);
            assertEquals(changed.length, Files.size(current));
            assertEquals(Refusal.SOURCE_UNPROVED,
                    assertInstanceOf(Rejected.class, cold.call(() -> catalog.prepare(authority, 3, A, standalone, 4))).reason());
            assertFalse(catalog.usable(authority, candidate.lease()));
            assertEquals(Disposition.RELEASED,
                    catalog.release(V0CatalogTestAuthority.release(authority, candidate.lease()), candidate.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void rejectsSelectedThirdSpellAndDepthTwoFromDurableSources() throws Exception {
        var authority = V0CatalogTestAuthority.open(8);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var siblings = new OversizedClosure(false);
        var chain = new OversizedClosure(true);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, siblings)));
            assertTrue(cold.call(() -> catalog.publishFixture(authority, B, chain)));
            var thirdSpell = Selection.standalone(siblings.root.identity().id().value());
            var depthTwo = Selection.standalone(chain.root.identity().id().value());
            assertEquals(Refusal.PROFILE_REJECTED, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, thirdSpell, 8))).reason());
            assertEquals(Refusal.PROFILE_REJECTED, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 2, B, depthTwo, 8))).reason());
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void rejectsHardManifestMaxPlusOneAndMissingEntryIndependently() throws Exception {
        var authority = V0CatalogTestAuthority.open(8);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var maximum = new Initial(8, 128);
        var oversized = new Initial(9, 128);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, maximum)));
            assertTrue(cold.call(() -> catalog.publishFixture(authority, B, oversized)));
            var missing = Selection.manifest(maximum.manifest.identity().id().value(), 9);
            var ninth = Selection.manifest(oversized.manifest.identity().id().value(), 9);
            assertEquals(Refusal.MANIFEST_UNAVAILABLE, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, missing, 8))).reason());
            assertEquals(Refusal.CAPACITY, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 2, B, ninth, 8))).reason());
            var eighth = Selection.manifest(maximum.manifest.identity().id().value(), 8);
            var candidate = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 3, A, eighth, 8)));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, candidate));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void rejectsSyntheticProgramCharge1090NegativeComponentsAndCheckedOverflow() throws Throwable {
        // No valid projection has a 1090-byte charge. Use the production arithmetic without
        // making a test-only entry point visible to other package members or caller code.
        var lookup = MethodHandles.privateLookupIn(ProjectionCheck.class, MethodHandles.lookup());
        var charge = lookup.findStatic(ProjectionCheck.class, "programCharge",
                MethodType.methodType(int.class, boolean.class, int.class, int.class));
        assertEquals(1089, (int) charge.invokeExact(true, 481, 470));
        assertThrows(ArithmeticException.class, () -> {
            int ignored = (int) charge.invokeExact(true, 482, 470);
        });
        assertThrows(ArithmeticException.class, () -> {
            int ignored = (int) charge.invokeExact(false, 10, -1);
        });
        assertThrows(ArithmeticException.class, () -> {
            int ignored = (int) charge.invokeExact(false, Integer.MAX_VALUE, 1);
        });
    }

    @Test
    void acceptedAndProvisionalCapacityRemainIndependentWithSharedExactPins() throws Exception {
        var authority = V0CatalogTestAuthority.open(8);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        try (var cold = new ColdLane()) {
            var fixtures = new Initial(8, 128);
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var selection = Selection.manifest(fixtures.manifest.identity().id().value(), 1);
            var accepted = new ArrayList<Lease>();
            for (long next = 1; next <= 4; next++) {
                long ticket = next;
                var candidate = assertInstanceOf(Candidate.class,
                        cold.call(() -> catalog.prepare(authority, ticket, A, selection, 8)));
                assertEquals(Acceptance.COMMITTED,
                        catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, candidate), candidate));
                accepted.add(candidate.lease());
            }
            var provisional = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 5, A, selection, 8)));
            var permit = V0CatalogTestAuthority.reserve(authority, provisional);
            assertEquals(Acceptance.CATALOG_BUSY, catalog.compareAndAccept(permit, provisional));

            var first = accepted.removeFirst();
            assertEquals(Disposition.RELEASED, catalog.release(V0CatalogTestAuthority.release(authority, first), first));
            assertEquals(Acceptance.COMMITTED, catalog.compareAndAccept(permit, provisional));
            accepted.add(provisional.lease());
            assertEquals(Acceptance.INVALID_RESERVATION, catalog.compareAndAccept(permit, provisional));

            var other = new Initial(2, 129);
            assertTrue(cold.call(() -> catalog.publishFixture(authority, B, other)));
            var secondOwner = Selection.standalone(other.root.identity().id().value());
            var refusal = assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 6, B, secondOwner, 8)));
            assertEquals(Refusal.PROFILE_REJECTED, refusal.reason());
            assertEquals(Disposition.CATALOG_BUSY, cold.call(() -> catalog.retire(authority)));
            for (Lease lease : accepted) {
                assertFalse(catalog.usable(authority, lease));
                assertEquals(Disposition.RELEASED, catalog.release(V0CatalogTestAuthority.release(authority, lease), lease));
            }
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void interruptedStageRecoveryKeepsTheExactCurrentAndRetiredLeasesCannotReappear() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 32);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var selection = Selection.standalone(fixtures.root.identity().id().value());
            Path writerDir = root.resolve(A.value().toString());
            byte[] original = Files.readAllBytes(writerDir.resolve("catalog.current"));
            Path interrupted = writerDir.resolve("catalog.writing");
            Files.write(interrupted, new byte[] { 0x51, 0x52 });
            assertEquals(Refusal.SOURCE_UNPROVED,
                    assertInstanceOf(Rejected.class, cold.call(() -> catalog.prepare(authority, 1, A, selection, 4))).reason());
            assertTrue(Files.exists(interrupted));
            var suspension = catalog.suspend(authority);
            assertTrue(cold.call(() -> catalog.recoverAndReopen(authority, suspension)));
            assertFalse(Files.exists(interrupted));
            assertArrayEquals(original, Files.readAllBytes(writerDir.resolve("catalog.current")));
            var restored = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selection, 4)));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, restored), restored));
            var receipt = V0CatalogTestAuthority.release(authority, restored.lease());
            assertEquals(Disposition.RELEASED, catalog.release(receipt, restored.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));

            var replacement = V0CatalogTestAuthority.open(4);
            var reopened = new V0RuntimeCatalog(replacement, root, CONTEXT);
            var fresh = assertInstanceOf(Candidate.class,
                    cold.call(() -> reopened.prepare(replacement, 1, A, selection, 4)));
            assertEquals(Disposition.STALE_OR_RELEASED, reopened.release(receipt, restored.lease()));
            assertEquals(Acceptance.COMMITTED,
                    reopened.compareAndAccept(V0CatalogTestAuthority.reserve(replacement, fresh), fresh));
            assertTrue(reopened.usable(replacement, fresh.lease()));
            assertEquals(Disposition.RELEASED, reopened.release(
                    V0CatalogTestAuthority.release(replacement, fresh.lease()), fresh.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> reopened.retire(replacement)));
        }
    }

    @Test
    void refusesManifestWhenUnselectedEntryBecomesV0Ineligible() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 128);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var selected = Selection.manifest(fixtures.manifest.identity().id().value(), 1);
            var initial = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selected, 4)));
            assertEquals(2, childDamage(initial));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, initial));

            var unselected = readImage(root, A).routes().values().stream()
                    .filter(SpellReference.class::isInstance).map(SpellReference.class::cast)
                    .filter(spell -> !spell.equals(fixtures.root) && !spell.equals(fixtures.child))
                    .findFirst().orElseThrow();
            var overlongProfile = new PublicationPlan() {
                @Override
                List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
                    return List.of(spell(reservation.nextSpell(unselected.identity()), true, null, 2, 129));
                }
            };
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, overlongProfile)));
            var current = readImage(root, A);
            assertEquals(fixtures.manifest, current.routes().get(fixtures.manifest.identity()));
            assertEquals(fixtures.root, current.routes().get(fixtures.root.identity()));
            assertEquals(fixtures.child, current.routes().get(fixtures.child.identity()));
            assertNotEquals(unselected, current.routes().get(unselected.identity()));

            var unselectedStandalone = Selection.standalone(unselected.identity().id().value());
            assertEquals(Refusal.PROFILE_REJECTED, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, unselectedStandalone, 4))).reason());
            assertEquals(Refusal.MANIFEST_UNAVAILABLE, assertInstanceOf(Rejected.class,
                    cold.call(() -> catalog.prepare(authority, 3, A, selected, 4))).reason());

            var selectedStandalone = Selection.standalone(fixtures.root.identity().id().value());
            var stillEligible = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 4, A, selectedStandalone, 4)));
            assertEquals(2, childDamage(stillEligible));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, stillEligible));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void releaseDuringSameWriterPublicationCannotReleaseSuccessor() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 32);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var selection = Selection.manifest(fixtures.manifest.identity().id().value(), 1);
            var original = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selection, 4)));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, original), original));
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, new Update(fixtures.child.identity(), 3))));
            Path committed = root.resolve(A.value().toString()).resolve("catalog.current");
            byte[] prior = Files.readAllBytes(committed);

            var enteredAfterReserve = new CountDownLatch(1);
            var continueMutation = new CountDownLatch(1);
            var third = new Update(fixtures.child.identity(), 4);
            var plan = new V0RuntimeCatalog.PublicationPlan() {
                @Override
                List<FormalDocument> material(ScopeDCatalog.Reservation reservation) throws IOException {
                    enteredAfterReserve.countDown();
                    awaitPublication(continueMutation);
                    return third.material(reservation);
                }
            };
            var publishing = cold.lane.submit(() -> catalog.publishFixture(authority, A, plan));
            var oldPermit = V0CatalogTestAuthority.release(authority, original.lease());
            try {
                assertTrue(enteredAfterReserve.await(10, TimeUnit.SECONDS));
                assertTrue(catalog.usable(authority, original.lease()));
                assertEquals(2, childDamage(original));
                assertEquals(Disposition.RELEASED, catalog.release(oldPermit, original.lease()));
                assertEquals(Disposition.STALE_OR_RELEASED, catalog.release(oldPermit, original.lease()));
                assertArrayEquals(prior, Files.readAllBytes(committed));
            } finally {
                continueMutation.countDown();
            }
            assertTrue(publishing.get(20, TimeUnit.SECONDS));
            assertTrue(cold.call(() -> catalog.sweep(authority, A)));
            var swept = readImage(root, A);
            assertFalse(swept.revisions().containsKey(fixtures.child));
            assertTrue(swept.revisions().containsKey(third.revision));
            assertTrue(swept.externalRoots().isEmpty());

            var successor = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selection, 4)));
            assertEquals(4, childDamage(successor));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, successor), successor));
            assertEquals(Disposition.STALE_OR_RELEASED, catalog.release(oldPermit, original.lease()));
            assertEquals(Disposition.STALE_OR_RELEASED, catalog.release(oldPermit, successor.lease()));
            assertTrue(catalog.usable(authority, successor.lease()));
            assertEquals(Disposition.RELEASED, catalog.release(
                    V0CatalogTestAuthority.release(authority, successor.lease()), successor.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    @Test
    void reloadFenceBeforePhysicalReplacePreservesOriginalWriterAndCurrentBytes() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 32);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            Path directory = root.resolve(A.value().toString());
            Path committed = directory.resolve("catalog.current");
            byte[] prior = Files.readAllBytes(committed);
            var enteredAfterReserve = new CountDownLatch(1);
            var continueMutation = new CountDownLatch(1);
            var update = new Update(fixtures.child.identity(), 3);
            var plan = new V0RuntimeCatalog.PublicationPlan() {
                @Override
                List<FormalDocument> material(ScopeDCatalog.Reservation reservation) throws IOException {
                    enteredAfterReserve.countDown();
                    awaitPublication(continueMutation);
                    return update.material(reservation);
                }
            };
            var publishing = cold.lane.submit(() -> catalog.publishFixture(authority, A, plan));
            V0RuntimeCatalog.Suspension suspension;
            try {
                assertTrue(enteredAfterReserve.await(10, TimeUnit.SECONDS));
                suspension = catalog.suspend(authority);
                assertNotNull(suspension);
            } finally {
                continueMutation.countDown();
            }
            var refusal = assertThrows(ExecutionException.class, () -> publishing.get(20, TimeUnit.SECONDS));
            var stale = assertInstanceOf(ScopeDStorage.Failure.class, refusal.getCause());
            assertEquals(ScopeDStorage.Reason.STALE, stale.reason());
            assertArrayEquals(prior, Files.readAllBytes(committed));
            assertFalse(Files.exists(directory.resolve("catalog.prepare")));
            assertTrue(cold.call(() -> catalog.recoverAndReopen(authority, suspension)));

            var selection = Selection.standalone(fixtures.root.identity().id().value());
            var recovered = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selection, 4)));
            assertEquals(2, childDamage(recovered));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, recovered));
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, update)));
            var successor = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selection, 4)));
            assertEquals(3, childDamage(successor));
            assertEquals(Disposition.RELEASED, catalog.discard(authority, successor));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
        }
    }

    private static void awaitPublication(CountDownLatch continueMutation) throws IOException {
        try {
            if (!continueMutation.await(10, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting to continue test publication");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted test publication", interrupted);
        }
    }

    @Test
    void terminalWorkerReleasesOnlyUndeliveredProvisionalRowsAndPreservesAcceptedLease() throws Exception {
        var authority = V0CatalogTestAuthority.open(4);
        var catalog = new V0RuntimeCatalog(authority, root, CONTEXT);
        var fixtures = new Initial(2, 32);
        try (var cold = new ColdLane()) {
            assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixtures)));
            var selection = Selection.standalone(fixtures.root.identity().id().value());
            var accepted = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 1, A, selection, 4)));
            assertEquals(Acceptance.COMMITTED,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, accepted), accepted));
            var undelivered = assertInstanceOf(Candidate.class,
                    cold.call(() -> catalog.prepare(authority, 2, A, selection, 4)));

            assertEquals(Disposition.CATALOG_BUSY, cold.call(() -> catalog.retire(authority)));
            assertEquals(Acceptance.HEAD_STALE,
                    catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, undelivered), undelivered));
            // The real terminal protocol fences on P5 before transferring retirement to
            // the worker. An erroneous early close did not discard this provisional row.
            assertNotNull(catalog.suspend(authority));
            assertEquals(Disposition.CATALOG_BUSY, cold.call(() -> catalog.retire(authority)));
            assertEquals(Disposition.STALE_OR_RELEASED, catalog.discard(authority, undelivered));
            assertEquals(Disposition.RELEASED, catalog.release(
                    V0CatalogTestAuthority.release(authority, accepted.lease()), accepted.lease()));
            assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));

            var nextAuthority = V0CatalogTestAuthority.open(4);
            var nextCatalog = new V0RuntimeCatalog(nextAuthority, root, CONTEXT);
            var fromDurableHead = assertInstanceOf(Candidate.class,
                    cold.call(() -> nextCatalog.prepare(nextAuthority, 1, A, selection, 4)));
            assertEquals(2, childDamage(fromDurableHead));
            assertNotNull(nextCatalog.suspend(nextAuthority));
            assertEquals(Disposition.RELEASED, cold.call(() -> nextCatalog.retire(nextAuthority)));
            assertEquals(Disposition.STALE_OR_RELEASED, nextCatalog.discard(nextAuthority, fromDurableHead));
        }
    }

    @Test
    void fixedHeapCatalogSurvivesDefaultAndHardChurnWithPinsAndDrains() throws Exception {
        Path output = root.resolve("catalog-fixed-heap.log");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms48m", "-Xmx48m", "-XX:+UseG1GC", "-XX:+ExitOnOutOfMemoryError",
                "-cp", System.getProperty("java.class.path"), HeapChild.class.getName(), root.toString())
                .directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(90, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            fail("V0 catalog fixed-heap child timed out");
        }
        String log = Files.readString(output);
        System.out.print(log);
        assertEquals(0, child.exitValue(), log);
        assertTrue(log.contains("default iterations=256 accepted=2 rawPins=6 provisionalPins=3"), log);
        assertTrue(log.contains("hard iterations=256 accepted=4 rawPins=12 provisionalPins=3"), log);
        assertTrue(log.contains("default finalPinRows=0"), log);
        assertTrue(log.contains("hard finalPinRows=0"), log);
    }

    public static final class HeapChild {
        public static void main(String[] args) throws Exception {
            assertEquals(48L * 1024 * 1024, Runtime.getRuntime().maxMemory());
            Path root = Path.of(args[0]);
            churn(Files.createDirectory(root.resolve("default")), "default", 4, 2, 16);
            churn(Files.createDirectory(root.resolve("hard")), "hard", 8, 4, 128);
        }

        private static void churn(Path source, String shape, int entries, int acceptedCount, int profileBytes)
                throws Exception {
            var authority = V0CatalogTestAuthority.open(entries);
            var catalog = new V0RuntimeCatalog(authority, source, CONTEXT);
            var fixture = new Initial(entries, profileBytes);
            try (var cold = new ColdLane()) {
                assertTrue(cold.call(() -> catalog.publishFixture(authority, A, fixture)));
                var selection = Selection.manifest(fixture.manifest.identity().id().value(), 1);
                var leases = new ArrayList<Lease>();
                long charge = 0;
                for (int row = 1; row <= acceptedCount; row++) {
                    int ticket = row;
                    var candidate = assertInstanceOf(Candidate.class,
                            cold.call(() -> catalog.prepare(authority, ticket, A, selection, entries)));
                    assertEquals(Acceptance.COMMITTED,
                            catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, candidate), candidate));
                    leases.add(candidate.lease());
                    charge = Math.addExact(charge, candidate.lease().programChargeBytes());
                }
                if (profileBytes == 128) {
                    assertEquals(4L * 1_089, charge);
                }
                var heap = java.lang.management.ManagementFactory.getMemoryMXBean();
                long heapSamplePeak = 0;
                for (int index = 1; index <= 256; index++) {
                    int expectedDamage = 1 + (index + 1) % 4;
                    long ticket = acceptedCount + index;
                    var change = new Update(fixture.child.identity(), expectedDamage, profileBytes);
                    assertTrue(cold.call(() -> catalog.publishFixture(authority, A, change)));
                    var candidate = assertInstanceOf(Candidate.class,
                            cold.call(() -> catalog.prepare(authority, ticket, A, selection, entries)));
                    assertEquals(expectedDamage, childDamage(candidate));
                    assertTrue(cold.call(() -> catalog.sweep(authority, A)));
                    assertEquals(Acceptance.HEAD_STALE,
                            catalog.compareAndAccept(V0CatalogTestAuthority.reserve(authority, candidate), candidate));
                    assertEquals(Disposition.RELEASED, catalog.discard(authority, candidate));
                    var pinned = readImage(source, A);
                    assertTrue(pinned.revisions().containsKey(fixture.child));
                    assertTrue(pinned.externalRoots().isEmpty());
                    for (var lease : leases) {
                        assertTrue(catalog.usable(authority, lease));
                        assertEquals(2, lease.root().hitCreate().orElseThrow().target().hitDamage().orElseThrow().damageHp());
                    }
                    heapSamplePeak = Math.max(heapSamplePeak, heap.getHeapMemoryUsage().getUsed());
                }
                System.out.printf("%s iterations=256 accepted=%d rawPins=%d provisionalPins=3 logicalBytes=%d heapSamplePeak=%d%n",
                        shape, leases.size(), leases.size() * 3, charge, heapSamplePeak);
                for (var lease : leases) {
                    assertEquals(Disposition.RELEASED,
                            catalog.release(V0CatalogTestAuthority.release(authority, lease), lease));
                }
                assertTrue(cold.call(() -> catalog.sweep(authority, A)));
                var drained = readImage(source, A);
                assertFalse(drained.revisions().containsKey(fixture.child));
                assertTrue(drained.externalRoots().isEmpty());
                assertEquals(Disposition.RELEASED, cold.call(() -> catalog.retire(authority)));
                System.out.printf("%s finalPinRows=0 retainedRevisions=%d%n", shape, drained.revisions().size());
            }
        }
    }

    private static ScopeDCatalogImage readImage(Path root, SkillOwnerId owner) throws IOException {
        Path current = root.resolve(owner.value().toString()).resolve("catalog.current");
        // Physical storage remains owned by the facade. The immutable image is read for this
        // deterministic test after durable publication and before the next cold command.
        byte[] frame = Files.readAllBytes(current);
        var header = java.nio.ByteBuffer.wrap(frame);
        header.position(2 * Integer.BYTES + 1 + 4 * Long.BYTES);
        int bodySize = header.getInt();
        header.getLong();
        long generation = header.getLong();
        header.getLong();
        header.position(header.position() + 64);
        int payloadLength = bodySize - (3 * Long.BYTES + 64);
        var payload = new byte[payloadLength];
        header.get(payload);
        return ScopeDCatalogImage.decode(owner, generation, payload);
    }

    private static int childDamage(Candidate candidate) {
        return candidate.lease().root().hitCreate().orElseThrow().target().hitDamage().orElseThrow().damageHp();
    }

    private static Context context() {
        var schema = new Schema(Map.of("launch_step_milli", Field.required(ValueType.INTEGER)));
        var visual = new Schema(Map.of("sound_profile", Field.required(ValueType.TEXT), "particle_profile",
                Field.required(ValueType.TEXT), "trail_profile", Field.required(ValueType.TEXT)));
        var snapshot = new Snapshot(List.of(
                new BackendContract(BACKEND, CATEGORY, Schema.EMPTY, Set.of(CAPABILITY), Set.of(CAPABILITY),
                        Set.of(), Schema.EMPTY, schema, Set.of(CREATED, HIT), 1),
                new ActivationContract(ACTIVATE, Schema.EMPTY, Set.of(CATEGORY), Set.of(CAPABILITY), LAUNCH, 1),
                new EventContract(CREATED, Set.of(CATEGORY), Set.of(CAPABILITY), Map.of(),
                        EventOrigin.CREATION, Optional.empty(), 1),
                new EventContract(HIT, Set.of(CATEGORY), Set.of(CAPABILITY), LAUNCH, EventOrigin.EXTERNAL,
                        Optional.of(new CreateContract(Set.of(CAPABILITY), LAUNCH, 1)), 1),
                new EffectContract(DAMAGE, new Schema(Map.of("damage_hp", Field.defaulted(new IntegerValue(2)))),
                        Set.of(CATEGORY), Set.of(HIT), Set.of(CAPABILITY), Map.of(), Map.of(), Set.of(),
                        EffectOperation.DESCRIPTOR_EFFECT, 1),
                new AppearanceContract(APPEARANCE, visual, Set.of(CATEGORY), 1)));
        return new Context(snapshot, snapshot.fullPolicy());
    }

    private static SpellDefinition spell(SpellReference reference, boolean activated, SpellIdentity child,
                                         int damage, int profileBytes) {
        return spellWithChildren(reference, activated, child == null ? List.of() : List.of(child), damage, profileBytes);
    }

    private static SpellDefinition spellWithChildren(SpellReference reference, boolean activated,
                                                     List<SpellIdentity> children, int damage, int profileBytes) {
        var steps = new ArrayList<Block>();
        steps.add(new EffectBlock(new BlockId(1), new Descriptor(DAMAGE.tag(), 1,
                List.of(new Argument("damage_hp", new IntegerValue(damage))))));
        for (SpellIdentity child : children) {
            steps.add(new CreateSpell(new BlockId(steps.size() + 1L), new SpellIdentityReference(child)));
        }
        String profile = "gramarye:" + "c".repeat(profileBytes - 9);
        var appearance = new Descriptor(APPEARANCE.tag(), 1, List.of(
                new Argument("sound_profile", new TextValue(profile)),
                new Argument("particle_profile", new TextValue(profile)),
                new Argument("trail_profile", new TextValue(profile))));
        var body = new SpellBody(new Known<>(new Backend(descriptor(BACKEND), CATEGORY, List.of(CAPABILITY))),
                activated ? new Known<>(descriptor(ACTIVATE)) : new Absent<>(), new Known<>(List.of()),
                new Known<>(List.of(new Handler(new HandlerId(1), CREATED.tag(), 1, List.of()),
                        new Handler(new HandlerId(2), HIT.tag(), 1, steps))),
                new Known<>(appearance), new Absent<>());
        return new SpellDefinition(reference, body, Optional.empty());
    }

    private static Descriptor descriptor(DescriptorKey key) {
        return new Descriptor(key.tag(), key.version(), List.of());
    }

    private static final class Initial extends V0RuntimeCatalog.PublicationPlan {
        private final int manifestEntries;
        private final int profileBytes;
        private SpellReference root;
        private SpellReference child;
        private ManifestReference manifest;

        private Initial(int manifestEntries, int profileBytes) {
            this.manifestEntries = manifestEntries;
            this.profileBytes = profileBytes;
        }

        @Override
        List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
            root = reservation.newSpell(1);
            child = reservation.newSpell(2);
            manifest = reservation.newManifest(3);
            var entries = new ArrayList<ManifestEntry>();
            var documents = new ArrayList<FormalDocument>();
            documents.add(spell(root, true, child.identity(), 2, profileBytes));
            documents.add(spell(child, false, null, 2, profileBytes));
            entries.add(entry(1, root));
            for (int index = 2; index <= manifestEntries; index++) {
                var independent = reservation.newSpell(index + 2);
                documents.add(spell(independent, true, null, 2, profileBytes));
                entries.add(entry(index, independent));
            }
            documents.add(new SkillManifest(manifest, new ManifestBody(new Known<>(entries), "", false), Optional.empty()));
            return List.copyOf(documents);
        }
    }

    private static ManifestEntry entry(long id, SpellReference root) {
        return new ManifestEntry(new ManifestEntryId(id), new SpellIdentityReference(root.identity()), "", "");
    }

    private static final class OversizedClosure extends V0RuntimeCatalog.PublicationPlan {
        private final boolean chained;
        private SpellReference root;

        private OversizedClosure(boolean chained) {
            this.chained = chained;
        }

        @Override
        List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
            root = reservation.newSpell(1);
            var child = reservation.newSpell(2);
            var third = reservation.newSpell(3);
            var rootChildren = chained ? List.of(child.identity()) : List.of(child.identity(), third.identity());
            return List.of(spellWithChildren(root, true, rootChildren, 2, 16),
                    spell(child, false, chained ? third.identity() : null, 2, 16),
                    spell(third, false, null, 2, 16));
        }
    }

    private static final class Update extends V0RuntimeCatalog.PublicationPlan {
        private final SpellIdentity child;
        private final int damage;
        private final int profileBytes;
        private SpellReference revision;

        private Update(SpellIdentity child, int damage) {
            this(child, damage, 128);
        }

        private Update(SpellIdentity child, int damage, int profileBytes) {
            this.child = child;
            this.damage = damage;
            this.profileBytes = profileBytes;
        }

        @Override
        List<FormalDocument> material(ScopeDCatalog.Reservation reservation) {
            revision = reservation.nextSpell(child);
            return List.of(spell(revision, false, null, damage, profileBytes));
        }
    }

    private static final class ColdLane implements AutoCloseable {
        private final java.util.concurrent.ExecutorService lane = Executors.newSingleThreadExecutor();

        private <T> T call(Callable<T> action) throws Exception {
            return lane.submit(action).get(20, TimeUnit.SECONDS);
        }

        @Override
        public void close() {
            lane.close();
        }
    }
}
