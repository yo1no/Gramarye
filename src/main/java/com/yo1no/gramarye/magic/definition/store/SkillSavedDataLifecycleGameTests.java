package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.Gramarye;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentGameTests;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Normal GameTest coverage for the production P4-B2-A startup/cache path. */
@GameTestHolder(Gramarye.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillSavedDataLifecycleGameTests {
    private SkillSavedDataLifecycleGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void startupInstalledExactReadyAdapterInOverworldCache(
            GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.assertTrue(server.isSameThread(), "GameTest must run on the server thread");
        helper.assertTrue(
                SkillSavedDataPrimaryIngress.resolvePrimaryPath(server).equals(
                        server.getWorldPath(LevelResource.ROOT)
                                .resolve("data")
                                .resolve("gramarye_skill_definitions.dat")),
                "primary resolver must match the platform Overworld data path");

        var constructorCalls = new AtomicInteger();
        var deserializerCalls = new AtomicInteger();
        SavedData.Factory<SavedData> throwingFactory = new SavedData.Factory<>(
                () -> {
                    constructorCalls.incrementAndGet();
                    throw new AssertionError("cache-hit constructor must not run");
                },
                (tag, provider) -> {
                    deserializerCalls.incrementAndGet();
                    throw new AssertionError("cache-hit deserializer must not run");
                });
        var storage = server.overworld().getDataStorage();
        var first = storage.get(
                throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);
        var second = storage.get(
                throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);

        helper.assertTrue(first instanceof GramaryeSkillSavedData,
                "ServerStarting must install the Gramarye adapter before GameTest execution");
        helper.assertTrue(first == second,
                "Overworld cache must return the exact installed adapter identity");
        helper.assertTrue(constructorCalls.get() == 0 && deserializerCalls.get() == 0,
                "cache-hit verification must not invoke either SavedData factory branch");
        var adapter = (GramaryeSkillSavedData) first;
        helper.assertTrue(adapter.state() instanceof SkillSavedDataState.Ready,
                "fresh normal GameTest world must install Ready state");
        helper.assertTrue(
                ((SkillSavedDataState.Ready) adapter.state()).journalLifecycle()
                                instanceof PendingAttachmentJournalLifecycle.Installed installed
                        && installed.state()
                                instanceof PendingAttachmentJournalState.Ready journalReady
                        && journalReady.journal().entryCount() == 0,
                "production ServerStarting must install then bootstrap the empty journal");
        helper.assertTrue(!adapter.isDirty(),
                "absent primary must install a non-dirty empty Ready state");
        var missing = adapter.latestReference(
                new com.yo1no.gramarye.magic.api.id.SkillId(
                        new java.util.UUID(0L, 0xB2A)));
        helper.assertTrue(
                missing instanceof SkillSubsystemResult.Available<?> available
                        && available.value().equals(Optional.empty()),
                "controlled missing lookup must remain available rather than unavailable");

        var isolated = new SkillDefinitionStoreService();
        SkillSubsystemLifecycleException beforeInstall = null;
        try {
            isolated.latestReference(
                    server,
                    new com.yo1no.gramarye.magic.api.id.SkillId(
                            new java.util.UUID(0L, 0xB2A + 2L)));
        } catch (SkillSubsystemLifecycleException exception) {
            beforeInstall = exception;
        }
        helper.assertTrue(
                beforeInstall != null
                        && beforeInstall.code()
                                == SkillSubsystemLifecycleException.Code.BOOTSTRAP_NOT_INSTALLED,
                "controlled access before install must fail with the lifecycle code");

        var wrongThreadFailure = runOffThread(
                "gramarye-p4-b2-a-wrong-thread-gate",
                () -> isolated.latestReference(
                        server,
                        new com.yo1no.gramarye.magic.api.id.SkillId(
                                new java.util.UUID(0L, 0xB2A + 3L))));
        helper.assertTrue(
                wrongThreadFailure instanceof SkillSubsystemLifecycleException exception
                        && exception.code()
                                == SkillSubsystemLifecycleException.Code.WRONG_THREAD,
                "controlled wrong-thread access must fail before marker/cache inspection");

        var quarantined = GramaryeSkillSavedData.quarantined(
                new SkillSavedDataPrimaryFailure.MalformedGzip(
                        SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID));
        try {
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, quarantined);
            var fixtureCacheResult = storage.get(
                    throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);
            helper.assertTrue(fixtureCacheResult == quarantined,
                    "controlled quarantine fixture must retain exact cache identity");
            helper.assertTrue(
                    quarantined.state() instanceof SkillSavedDataState.Quarantined,
                    "controlled quarantine fixture must retain only Quarantined state");
            helper.assertTrue(!quarantined.isDirty(),
                    "Quarantined fixture must remain non-dirty");
            var unavailable = quarantined.latestReference(
                    new com.yo1no.gramarye.magic.api.id.SkillId(
                            new java.util.UUID(0L, 0xB2A + 1L)));
            helper.assertTrue(
                    unavailable instanceof SkillSubsystemResult.Unavailable<?> result
                            && result.reason().state()
                                    == SkillSubsystemUnavailableReason.State.QUARANTINED,
                    "controlled reads must distinguish Quarantined from missing");
        } finally {
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        }
        helper.assertTrue(
                storage.get(throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME)
                        == adapter,
                "controlled quarantine fixture must restore the startup adapter");
        try {
            isolated.install(server);
            var port = isolated.submissionPort();
            helper.assertTrue(port == isolated.submissionPort(),
                    "service must own one exact submission-port identity");
            helper.assertTrue(
                    port.journalStatus(server)
                            instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Unavailable,
                    "journal must be unavailable before explicit D1 bootstrap");
            var bootstrapped = port.bootstrapJournal(server);
            helper.assertTrue(
                    bootstrapped
                            instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready ready
                            && ready.entryCount() == 0
                            && !ready.rewritePublished(),
                    "zero sentinel must bootstrap as canonical empty Ready");
            helper.assertTrue(
                    port.journalStatus(server)
                            instanceof SkillDefinitionStoreSubmissionPort.JournalStatus.Ready ready
                            && ready.entryCount() == 0,
                    "bootstrapped journal status must be Ready and empty");
            helper.assertTrue(
                    port.journalRoots(server)
                            instanceof SkillDefinitionStoreSubmissionPort.JournalRootProjection.Available roots
                            && roots.references().isEmpty(),
                    "zero journal must project available empty roots");
            helper.assertTrue(
                    port.observeSubmissionAuthority(
                                    server,
                                    new SkillId(new UUID(0L, 0xD1L)),
                                    new SkillOwnerId(new UUID(0L, 0xD101L)))
                            instanceof SkillDefinitionStoreSubmissionPort.AuthoritySnapshot.Absent,
                    "empty Store must report absent submission authority");
            var installedAdapter = storage.get(
                    throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME);
            helper.assertTrue(
                    installedAdapter instanceof GramaryeSkillSavedData installedGramarye
                            && !installedGramarye.isDirty(),
                    "canonical zero bootstrap must not mark SavedData dirty");
        } finally {
            isolated.uninstall(server);
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, adapter);
        }
        helper.assertTrue(
                storage.get(throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME)
                        == adapter,
                "journal bootstrap fixture must restore the startup adapter");
        helper.runAfterDelay(1, () -> exerciseB2AGroupedAuditLifecycle(
                helper, server, storage, adapter, throwingFactory));
    }

    private static void exerciseB2AGroupedAuditLifecycle(
            GameTestHelper helper,
            net.minecraft.server.MinecraftServer server,
            net.minecraft.world.level.storage.DimensionDataStorage storage,
            GramaryeSkillSavedData productionAdapter,
            SavedData.Factory<SavedData> throwingFactory) {
        helper.assertTrue(server.isSameThread(),
                "B2-A lifecycle evidence must start on the server thread");
        helper.assertTrue(server.getPlayerList().getPlayers().isEmpty(),
                "B2-A empty-Store lifecycle fixture requires no live player sources");

        var isolated = new SkillDefinitionStoreService();
        P4E1GlobalSourceCapture.Captured crossTickCapture = null;
        P4E1GroupedStoreAudit crossTickOwner = null;
        try {
            isolated.install(server);
            var port = isolated.submissionPort();
            helper.assertTrue(
                    port.bootstrapJournal(server)
                            instanceof SkillDefinitionStoreSubmissionPort.BootstrapResult.Ready,
                    "B2-A lifecycle fixture must bootstrap an empty Ready journal");
            var fixtureAdapter = isolated.installedAdapter(server);
            var fixtureReady = (SkillSavedDataState.Ready) fixtureAdapter.state();
            var fixtureStore = fixtureReady.store();
            var fixtureInnerCarrier = fixtureReady.innerCarrier();
            var fixtureStoreCarrier = fixtureReady.storeCarrier();
            var fixturePending = fixtureInnerCarrier.pending();
            var fixtureJournalLifecycle = fixtureReady.journalLifecycle();
            var fixtureJournalReady = (PendingAttachmentJournalState.Ready)
                    ((PendingAttachmentJournalLifecycle.Installed) fixtureJournalLifecycle)
                            .state();
            var fixtureJournal = fixtureJournalReady.journal();
            var fixtureJournalProof = fixtureJournalReady.targetAuditProof();
            var fixtureDirty = fixtureAdapter.isDirty();
            var attachments =
                    PlayerSkillAttachmentGameTests.newServiceForSubmissionGameTests();

            assertJournalCurrentnessClassification(helper, server, isolated);

            var successfulOwner = new P4E1GroupedStoreAudit(server);
            var successfulCapture = requireCaptured(
                    helper, server, isolated, attachments, successfulOwner, "successful");
            var successfulSummary = successfulCapture.summary();
            var successful = successfulOwner.audit(successfulCapture);
            helper.assertTrue(
                    successful instanceof P4E1GroupedStoreAudit.Result.Audited,
                    "same-owner same-server same-thread same-tick audit must succeed");
            var audited = ((P4E1GroupedStoreAudit.Result.Audited) successful).capture();
            var transfer = audited.claim(successfulOwner);
            helper.assertTrue(transfer.summary(successfulOwner) == successfulSummary,
                    "AuditedCapture must transfer the exact B1 summary identity");
            helper.assertTrue(transfer.distinctSkillIdCount(successfulOwner) == 0,
                    "empty source fixture must retain zero distinct SkillIds");
            transfer.discard();
            transfer.discard();
            expectFailure(
                    "P4E1_AUDITED_TRANSFER_DISCARDED",
                    () -> transfer.summary(successfulOwner));
            expectFailure(
                    "P4E1_AUDITED_CAPTURE_ALREADY_CONSUMED",
                    () -> audited.discard(successfulOwner));

            var exactOwner = new P4E1GroupedStoreAudit(server);
            var wrongOwnerCapture = requireCaptured(
                    helper, server, isolated, attachments, exactOwner, "wrong-owner");
            var wrongOwner = new P4E1GroupedStoreAudit(server);
            expectFailure(
                    "P4E1_CLAIMED_CAPTURE_OWNER_MISMATCH",
                    () -> wrongOwner.audit(wrongOwnerCapture));
            expectFailure(
                    "P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED",
                    () -> exactOwner.audit(wrongOwnerCapture));

            var exactThreadOwner = new P4E1GroupedStoreAudit(server);
            var wrongThreadCapture = requireCaptured(
                    helper,
                    server,
                    isolated,
                    attachments,
                    exactThreadOwner,
                    "wrong-thread");
            var wrongThreadFailure = runOffThread(
                    "gramarye-p4-e1-b2-a-wrong-thread-gate",
                    () -> exactThreadOwner.audit(wrongThreadCapture));
            helper.assertTrue(
                    wrongThreadFailure
                                    instanceof P4E1GroupedStoreAudit.BindingException failure
                            && "P4E1_GROUPED_AUDIT_THREAD_MISMATCH".equals(
                                    failure.getMessage()),
                    "wrong-thread consume must fail with the fixed binding code");
            expectFailure(
                    "P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED",
                    () -> exactThreadOwner.audit(wrongThreadCapture));

            crossTickOwner = new P4E1GroupedStoreAudit(server);
            crossTickCapture = requireCaptured(
                    helper,
                    server,
                    isolated,
                    attachments,
                    crossTickOwner,
                    "cross-tick");

            helper.assertTrue(isolated.installedAdapter(server) == fixtureAdapter,
                    "B2-A audit must retain the exact installed adapter identity");
            helper.assertTrue(fixtureAdapter.state() == fixtureReady,
                    "B2-A audit must retain the exact SavedData Ready identity");
            helper.assertTrue(fixtureReady.store() == fixtureStore,
                    "B2-A audit must not replace or mutate the Store identity");
            helper.assertTrue(fixtureReady.innerCarrier() == fixtureInnerCarrier
                            && fixtureReady.storeCarrier() == fixtureStoreCarrier
                            && fixtureInnerCarrier.pending() == fixturePending,
                    "B2-A audit must retain exact Store and pending carrier identities");
            helper.assertTrue(fixtureReady.journalLifecycle() == fixtureJournalLifecycle
                            && fixtureJournalReady.journal() == fixtureJournal
                            && fixtureJournalReady.targetAuditProof() == fixtureJournalProof
                            && fixtureJournal.entryCount() == 0,
                    "B2-A audit must not append, clear, recover, or replace the journal");
            helper.assertTrue(fixtureStoreCarrier.historyCount() == 0
                            && fixtureStoreCarrier.revisionCount() == 0,
                    "B2-A audit must leave the empty Store carrier unchanged");
            helper.assertTrue(fixtureAdapter.isDirty() == fixtureDirty,
                    "B2-A audit must not publish SavedData dirty state");
        } finally {
            isolated.uninstall(server);
            storage.set(SkillDefinitionStoreService.SAVED_DATA_NAME, productionAdapter);
        }
        helper.assertTrue(
                storage.get(throwingFactory, SkillDefinitionStoreService.SAVED_DATA_NAME)
                        == productionAdapter,
                "B2-A lifecycle fixture must restore the production adapter before yielding");
        if (crossTickCapture == null || crossTickOwner == null) {
            throw new AssertionError("B2-A cross-tick capture was not created");
        }
        var delayedCapture = crossTickCapture;
        var delayedOwner = crossTickOwner;
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(server.isSameThread(),
                    "B2-A cross-tick evidence must remain on the server thread");
            var failure = expectFailure(
                    "P4E1_GROUPED_AUDIT_TICK_MISMATCH",
                    () -> delayedOwner.audit(delayedCapture));
            helper.assertTrue(failure instanceof P4E1GroupedStoreAudit.BindingException,
                    "cross-tick consume must remain a programming binding failure");
            expectFailure(
                    "P4E1_GLOBAL_CAPTURE_ALREADY_CONSUMED",
                    () -> delayedOwner.audit(delayedCapture));
            helper.assertTrue(!productionAdapter.isDirty(),
                    "cross-tick rejection must not dirty the restored production adapter");
            helper.succeed();
        });
    }

    private static P4E1GlobalSourceCapture.Captured requireCaptured(
            GameTestHelper helper,
            net.minecraft.server.MinecraftServer server,
            SkillDefinitionStoreService storeService,
            com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService
                    attachments,
            P4E1GroupedStoreAudit owner,
            String label) {
        var result = P4E1GlobalSourceCapture.capture(
                server, storeService, attachments, owner);
        helper.assertTrue(result instanceof P4E1GlobalSourceCapture.CaptureResult.Captured,
                "B2-A " + label + " fixture must produce a Captured result: " + result);
        return ((P4E1GlobalSourceCapture.CaptureResult.Captured) result).capture();
    }

    private static void assertJournalCurrentnessClassification(
            GameTestHelper helper,
            net.minecraft.server.MinecraftServer server,
            SkillDefinitionStoreService service) {
        var observedStore = service.observeP4E1StoreReady(server);
        helper.assertTrue(observedStore instanceof P4E1GlobalSourceCapture.StoreObservation.Ready,
                "B2-A journal currentness fixture requires a Ready Store witness");
        var storeWitness =
                ((P4E1GlobalSourceCapture.StoreObservation.Ready) observedStore).witness();
        var port = service.submissionPort();
        try {
            var observedJournal = port.observeP4E1Journal(server, storeWitness);
            helper.assertTrue(
                    observedJournal
                            instanceof P4E1PendingJournalObservation.Result.Available,
                    "B2-A journal currentness fixture requires a Ready journal witness");
            var current = ((P4E1PendingJournalObservation.Result.Available) observedJournal)
                    .observation();
            var adapter = service.installedAdapter(server);
            helper.assertTrue(
                    current.currentness(port, server, storeWitness, adapter)
                            == P4E1PendingJournalObservation.Currentness.CURRENT,
                    "exact lifecycle and proof identities must remain current");
            current.discardForFailure(port, server, storeWitness);

            var savedDataReady = storeWitness.savedDataReadyIdentity();
            var installed = (PendingAttachmentJournalLifecycle.Installed)
                    savedDataReady.journalLifecycle();
            var journalReady = (PendingAttachmentJournalState.Ready) installed.state();
            var journal = journalReady.journal();

            var lifecycleMismatch = new P4E1PendingJournalObservation.Ready(
                    port,
                    server,
                    storeWitness,
                    adapter,
                    savedDataReady,
                    journalReady,
                    journalReady.sourcePending(),
                    savedDataReady.innerCarrier().pending(),
                    journalReady.targetAuditProof(),
                    journal);
            var otherAdapter = GramaryeSkillSavedData.quarantined(
                    new SkillSavedDataPrimaryFailure.MalformedGzip(
                            SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID));
            helper.assertTrue(
                    lifecycleMismatch.currentness(port, server, storeWitness, otherAdapter)
                            == P4E1PendingJournalObservation.Currentness
                                    .LIFECYCLE_UNAVAILABLE,
                    "adapter lifecycle drift must not be classified as proof invalidity");
            lifecycleMismatch.discardForFailure(port, server, storeWitness);

            var mismatchedProof = new JournalTargetAuditProof.AuditedExisting(journal);
            var proofMismatch = new P4E1PendingJournalObservation.Ready(
                    port,
                    server,
                    storeWitness,
                    adapter,
                    savedDataReady,
                    journalReady,
                    journalReady.sourcePending(),
                    savedDataReady.innerCarrier().pending(),
                    mismatchedProof,
                    journal);
            helper.assertTrue(
                    proofMismatch.currentness(port, server, storeWitness, adapter)
                            == P4E1PendingJournalObservation.Currentness.TARGET_INVALID,
                    "proof identity drift must remain distinct from lifecycle drift");
            proofMismatch.discardForFailure(port, server, storeWitness);
        } finally {
            storeWitness.discard();
        }
    }

    private static RuntimeException expectFailure(String code, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException failure) {
            if (code.equals(failure.getMessage())) {
                return failure;
            }
            throw new AssertionError(
                    "expected " + code + " but received " + failure.getMessage(), failure);
        }
        throw new AssertionError("expected failure " + code);
    }

    private static Throwable runOffThread(String name, Runnable operation) {
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                operation.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        }, name);
        thread.start();
        try {
            thread.join(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("wrong-thread gate was interrupted", exception);
        }
        if (thread.isAlive()) {
            throw new AssertionError("wrong-thread gate must terminate promptly");
        }
        return failure.get();
    }
}
