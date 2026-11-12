package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDData.*;
import static org.junit.jupiter.api.Assertions.*;

final class ScopeDCatalogTest {
    static final SkillOwnerId OWNER = new SkillOwnerId(new UUID(0, 41));
    static final ScopeDCatalog.Context CONTEXT = new ScopeDCatalog.Context(new DescriptorContext(Set.of(
            new DescriptorKey(DescriptorDomain.BACKEND, "gramarye:isolated", 1),
            new DescriptorKey(DescriptorDomain.EVENT, "gramarye:test_event", 1)), Set.of()), "a".repeat(64));

    @TempDir Path directory;

    @Test
    void isolatedProofAndReopenUseOneCompleteGenerationWithoutRevisingDependents() throws Exception {
        SpellReference original;
        SpellReference caller;
        ManifestReference manifest;
        ScopeDCatalog.ProofBinding binding;
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var allocation = catalog.reserve();
            original = allocation.newSpell(1);
            caller = allocation.newSpell(2);
            manifest = allocation.newManifest(3);
            var batch = catalog.prepare(allocation, CONTEXT, List.of(spell(original, List.of()),
                    spell(caller, List.of(handler(1, original.identity()))), manifest(manifest, caller.identity())));
            binding = batch.binding();
            var emptyFrontend = new ScopeDFrontend.Snapshot(List.of());
            assertThrows(IOException.class, () -> catalog.publish(batch,
                    new ScopeDFrontend.Context(emptyFrontend, emptyFrontend.fullPolicy()), null));
            assertFalse(storage.hasPendingMaterial());
            catalog.stage(batch, CONTEXT);
            assertEquals(Set.of(), catalog.current().routes().keySet());
            assertThrows(IOException.class, catalog::reserve);
        }
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var changedContext = new ScopeDCatalog.Context(CONTEXT.descriptors(), "b".repeat(64));
            assertThrows(IOException.class, () -> catalog.revalidatePending(changedContext));
            var recovered = catalog.revalidatePending(CONTEXT).orElseThrow();
            assertEquals(binding, recovered.binding());
            completeInIsolation(catalog, storage, recovered);
            var base = catalog.current();
            assertEquals(Set.of(original, caller, manifest), Set.copyOf(base.routes().values()));

            var allocation = catalog.reserve();
            var replacement = allocation.nextSpell(original.identity());
            var candidate = catalog.prepare(allocation, CONTEXT,
                    List.of(spell(replacement, List.of(handler(5, original.identity())))));
            completeInIsolation(catalog, storage, candidate);
            assertEquals(Set.of(original, caller, manifest), Set.copyOf(base.routes().values()));
            assertEquals(Set.of(replacement, caller, manifest), Set.copyOf(catalog.current().routes().values()));
            assertThrows(IOException.class, () -> catalog.stage(candidate, CONTEXT));
        }
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            assertEquals(new CatalogGeneration(2), new ScopeDCatalog(OWNER, storage).current().generation());
        }
    }

    @Test
    void reclaimProtectsExactClosureAndRefusesStaleInventoryOrRetiredIds() throws Exception {
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var first = catalog.reserve();
            var spell = first.newSpell(1);
            var companion = first.newSpell(2);
            var manifest = first.newManifest(3);
            completeInIsolation(catalog, storage, catalog.prepare(first, CONTEXT, List.of(
                    spell(spell, List.of(handler(1, companion.identity()))), spell(companion, List.of()),
                    manifest(manifest, spell.identity()))));
            var oldInventory = catalog.captureRoots(CONTEXT, List.of(manifest));
            var second = catalog.reserve();
            var updated = second.nextSpell(spell.identity());
            completeInIsolation(catalog, storage, catalog.prepare(second, CONTEXT,
                    List.of(spell(updated, List.of())), Set.of(), oldInventory));
            assertEquals(Set.of(manifest, spell, companion), catalog.current().externalRoots());
            assertTrue(catalog.current().revisions().containsKey(spell));
            assertThrows(IOException.class, () -> catalog.prepareSweep(catalog.reserve(), CONTEXT, oldInventory));

            var third = catalog.reserve();
            var reusedHandler = third.nextSpell(spell.identity());
            assertThrows(IOException.class, () -> catalog.prepare(third, CONTEXT,
                    List.of(spell(reusedHandler, List.of(handler(1, companion.identity()))))));
            var fourth = catalog.reserve();
            var replacement = fourth.nextSpell(spell.identity());
            completeInIsolation(catalog, storage, catalog.prepare(fourth, CONTEXT,
                    List.of(spell(replacement, List.of(handler(2, companion.identity())))),
                    Set.of(manifest.identity()), catalog.captureRoots(CONTEXT, List.of())));
            assertFalse(catalog.current().revisions().containsKey(updated));
            assertTrue(catalog.current().revisions().containsKey(manifest));
            assertTrue(catalog.current().routes().containsKey(companion.identity()));
        }
    }

    @Test
    void draftSnapshotIsIndependentOwnerBoundAndNeverBecomesARoute() throws Exception {
        SpellDraft draft;
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            var catalog = new ScopeDCatalog(OWNER, storage);
            var drafts = new ScopeDDrafts(OWNER, storage);
            var empty = drafts.read();
            var candidate = catalog.reserve().newSpell(1);
            draft = new SpellDraft(candidate.identity(), Optional.empty(), catalog.current().generation(),
                    new SpellBody(new Missing<>(), new Absent<>(), new Known<>(List.of()),
                            new Known<>(List.of()), new Absent<>(), new Absent<>()));
            var saved = drafts.replace(empty, List.of(draft));
            assertEquals(draft, saved.drafts().get(draft.identity()));
            assertThrows(IOException.class, () -> drafts.replace(empty, List.of()));
            var emptyAgain = drafts.replace(saved, List.of());
            assertThrows(IOException.class, () -> drafts.replace(empty, List.of(draft)));
            drafts.replace(emptyAgain, List.of(draft));
            assertEquals(Set.of(), catalog.current().routes().keySet());
            assertThrows(IllegalArgumentException.class, () -> new ScopeDDrafts(new SkillOwnerId(new UUID(0, 42)), storage));
        }
        try (ScopeDStorage storage = ScopeDStorage.open(directory, OWNER)) {
            assertEquals(draft, new ScopeDDrafts(OWNER, storage).read().drafts().get(draft.identity()));
            assertEquals(new CatalogGeneration(0), new ScopeDCatalog(OWNER, storage).current().generation());
        }
    }

    @Test
    void admittedOwnerInventoryStopsAtMaximumAndDoesNotRecreateMissingSource() throws Exception {
        Path root = Files.createDirectory(directory.resolve("owners")).toRealPath();
        for (int index = 0; index < ScopeDLimits.MAX_OWNERS; index++) {
            try (ScopeDStorage ignored = ScopeDSource.open(root, new SkillOwnerId(new UUID(0, index)))) {
                assertEquals(0, ignored.current().generation());
            }
        }
        assertThrows(IOException.class, () -> ScopeDSource.open(root, OWNER));
        var first = new SkillOwnerId(new UUID(0, 0));
        Path firstDirectory = root.resolve(first.value().toString());
        Files.move(firstDirectory, directory.resolve("missing-owner"));
        assertThrows(IOException.class, () -> ScopeDSource.open(root, first));
    }

    @Test
    void persistedNewDraftGetsFreshIdentityAndForkUsesExactAuthorizedSource() throws Exception {
        Path senderDirectory = Files.createDirectory(directory.resolve("sender"));
        Path receiverDirectory = Files.createDirectory(directory.resolve("receiver"));
        var receiver = new SkillOwnerId(new UUID(0, 42));
        var optimistic = new SpellIdentity(OWNER, new SpellDefinitionId(3));
        var draft = new SpellDraft(optimistic, Optional.empty(), new CatalogGeneration(0),
                spell(new SpellReference(optimistic, new SpellRevision(0)), List.of(handler(1, optimistic))).body());
        try (ScopeDStorage storage = ScopeDStorage.open(senderDirectory, OWNER)) {
            var drafts = new ScopeDDrafts(OWNER, storage);
            drafts.replace(drafts.read(), List.of(draft));
        }
        try (ScopeDStorage sender = ScopeDStorage.open(senderDirectory, OWNER);
             ScopeDStorage destination = ScopeDStorage.open(receiverDirectory, receiver)) {
            var catalog = new ScopeDCatalog(OWNER, sender);
            var saved = (SpellDraft) new ScopeDDrafts(OWNER, sender).read().drafts().get(optimistic);
            var reservation = catalog.reserve();
            completeInIsolation(catalog, sender, catalog.prepareDraft(reservation, CONTEXT, saved));
            SpellReference root = reservation.newSpell(1);
            var source = catalog.current().revisions().get(root);
            assertNotEquals(optimistic, root.identity());
            assertEquals(List.of(new SpellIdentityReference(root.identity())), source.dependencies());
            var bundle = catalog.exportFork(CONTEXT, root);

            var destinationCatalog = new ScopeDCatalog(receiver, destination);
            var receiverReservation = destinationCatalog.reserve();
            completeInIsolation(destinationCatalog, destination,
                    destinationCatalog.prepareFork(receiverReservation, CONTEXT, bundle));
            var importedRoot = receiverReservation.newSpell(1);
            var imported = destinationCatalog.current().revisions().get(importedRoot);
            assertEquals(List.of(new SpellIdentityReference(importedRoot.identity())), imported.dependencies());
            assertEquals(sender.sourceId(), imported.provenance().orElseThrow().sourceId());
            assertEquals(root, imported.provenance().orElseThrow().source());
        }
    }

    static SpellDefinition spell(SpellReference reference, List<Handler> handlers) {
        var backend = new Backend(new Descriptor("gramarye:isolated", 1, List.of()), "gramarye:test", List.of());
        return new SpellDefinition(reference, new SpellBody(new Known<>(backend), new Absent<>(),
                new Known<>(List.of()), new Known<>(handlers), new Absent<>(), new Absent<>()), Optional.empty());
    }

    private static Handler handler(int id, SpellIdentity target) {
        return new Handler(new HandlerId(id), "gramarye:test_event",
                List.of(new CreateSpell(new BlockId(id), new SpellIdentityReference(target))));
    }

    private static SkillManifest manifest(ManifestReference reference, SpellIdentity target) {
        return new SkillManifest(reference, new ManifestBody(new Known<>(List.of(new ManifestEntry(
                new ManifestEntryId(1), new SpellIdentityReference(target), "spell", "group"))), "test", false),
                Optional.empty());
    }

    /** Exercises the M1 physical layer directly, without issuing a compatibility proof. */
    static void completeInIsolation(ScopeDCatalog catalog, ScopeDStorage storage, ScopeDCatalog.Prepared candidate)
            throws Exception {
        var expected = storage.current();
        var staged = catalog.stage(candidate, CONTEXT);
        var binding = candidate.binding();
        assertEquals(storage.owner(), binding.owner());
        assertEquals(storage.sourceId(), binding.source());
        assertEquals(expected.digest(), binding.predecessorDigest());
        assertEquals(expected.generation(), binding.base().value());
        assertEquals(staged.generation(), binding.candidate().value());
        assertEquals(staged.transaction(), binding.transaction().value());
        assertEquals(staged.contextDigest(), binding.contextDigest());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(staged.payload())),
                binding.contentDigest());
        assertEquals(2, binding.verificationVersion());
        storage.commitPreparedImage(expected, staged);
    }
}
