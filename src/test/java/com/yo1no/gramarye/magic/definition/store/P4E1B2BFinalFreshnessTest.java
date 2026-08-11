package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P4E1B2BFinalFreshnessTest {
    private static final Path PRODUCTION_STORE_ROOT = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/definition/store");
    private static final List<String> COMPLETE_ORDER = List.of(
            "service",
            "server",
            "call-chain",
            "player-list",
            "reservation",
            "store",
            "journal",
            "inventory",
            "directory",
            "online",
            "integrated",
            "reservation-final");

    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulVerificationUsesTheExactOrderAndIssuesAnOpaqueSeal() {
        var input = new ScriptedInput(Mode.CURRENT);

        var verified = assertInstanceOf(
                P4E1FinalFreshness.VerificationResult.Verified.class,
                P4E1FinalFreshness.verify(input));

        assertNotNull(verified.seal());
        assertEquals(COMPLETE_ORDER, input.calls);
    }

    @Test
    void everyTerminalUsesFixedFirstFailurePrecedence() {
        assertFailure(Mode.WRONG_SERVICE,
                P4E1FinalFreshness.FailureCode.SERVER_FRESHNESS_LOST,
                "service");
        assertFailure(Mode.WRONG_SERVER,
                P4E1FinalFreshness.FailureCode.SERVER_FRESHNESS_LOST,
                "server");
        assertFailures(
                P4E1FinalFreshness.FailureCode.CALL_CHAIN_FRESHNESS_LOST,
                "call-chain",
                Mode.WRONG_THREAD,
                Mode.TICK_DRIFT);
        assertFailure(Mode.PLAYER_LIST_IDENTITY,
                P4E1FinalFreshness.FailureCode.SERVER_FRESHNESS_LOST,
                "player-list");
        assertFailures(
                P4E1FinalFreshness.FailureCode.INDEX_RESERVATION_LOST,
                "reservation",
                Mode.RESERVATION_STATE,
                Mode.RESERVATION_GENERATION);
        assertFailures(
                P4E1FinalFreshness.FailureCode.STORE_SOURCE_FRESHNESS_LOST,
                "store",
                Mode.STORE_SERVICE,
                Mode.STORE_ADAPTER,
                Mode.STORE_READY,
                Mode.STORE_IDENTITY,
                Mode.STORE_CARRIER,
                Mode.STORE_PENDING);
        assertFailures(
                P4E1FinalFreshness.FailureCode.JOURNAL_FRESHNESS_LOST,
                "journal",
                Mode.JOURNAL_READY,
                Mode.JOURNAL_PENDING);
        assertFailures(
                P4E1FinalFreshness.FailureCode.JOURNAL_TARGET_PROOF_LOST,
                "journal",
                Mode.JOURNAL_PROOF_IDENTITY,
                Mode.JOURNAL_PROOF_SATISFACTION);
        assertFailures(
                P4E1FinalFreshness.FailureCode.INVENTORY_PROVIDER_FRESHNESS_LOST,
                "inventory",
                Mode.INVENTORY_COVERAGE,
                Mode.INVENTORY_PLAYER_PROVIDER,
                Mode.INVENTORY_JOURNAL_PROVIDER,
                Mode.INVENTORY_FIXED_V0);
        assertFailure(Mode.DIRECTORY,
                P4E1FinalFreshness.FailureCode.DIRECTORY_RACE_DETECTED,
                "directory");
        assertFailure(Mode.SELECTED_FILE,
                P4E1FinalFreshness.FailureCode.SELECTED_FILE_FRESHNESS_LOST,
                "directory");
        assertFailures(
                P4E1FinalFreshness.FailureCode.ONLINE_SOURCE_FRESHNESS_LOST,
                "online",
                Mode.ONLINE_UUID_SET,
                Mode.ONLINE_PLAYER_IDENTITY,
                Mode.ONLINE_PLAYER_SERVER,
                Mode.ONLINE_ATTACHMENT_PRESENCE,
                Mode.ONLINE_ATTACHMENT_STATE);
        assertFailures(
                P4E1FinalFreshness.FailureCode.INTEGRATED_OWNER_FRESHNESS_LOST,
                "integrated",
                Mode.INTEGRATED_PROFILE,
                Mode.INTEGRATED_SNAPSHOT,
                Mode.ARBITRATION);
        assertFailures(
                P4E1FinalFreshness.FailureCode.INDEX_RESERVATION_LOST,
                "reservation-final",
                Mode.FINAL_RESERVATION_STATE,
                Mode.FINAL_RESERVATION_GENERATION);
    }

    @Test
    void sourceTerminalsDistinguishPreArbitrationFromExactPostArbitrationCounts() {
        var failure = P4E1SourceFailure.simple(
                P4E1SourceFailure.Code.STORE_UNAVAILABLE,
                P4E1AuditStage.STORE_REFERENCE_OWNER_AUDIT);
        var before = new P4E1GlobalSourceCapture.CaptureResult.Incomplete(failure)
                .observedSummary();

        assertEquals(OptionalInt.empty(), before.selectedOwnerCount());
        assertEquals(OptionalInt.empty(), before.sourceCount());

        var after = new P4E1GlobalSourceCapture.ObservedSummary(
                OptionalInt.of(4),
                OptionalInt.of(1),
                OptionalInt.of(1),
                OptionalInt.of(2),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.of(5));
        var terminal = new P4E1GlobalSourceCapture.CaptureResult.Incomplete(failure, after);

        assertEquals(OptionalInt.of(4), terminal.observedSummary().selectedOwnerCount());
        assertEquals(OptionalInt.of(1), terminal.observedSummary().onlineOwnerCount());
        assertEquals(OptionalInt.of(1), terminal.observedSummary().integratedOwnerCount());
        assertEquals(OptionalInt.of(2), terminal.observedSummary().diskOwnerCount());
        assertEquals(OptionalInt.empty(), terminal.observedSummary().playerRootClaimCount());
        assertEquals(OptionalInt.empty(), terminal.observedSummary().journalRootClaimCount());
        assertEquals(OptionalInt.empty(), terminal.observedSummary().totalRawRootClaimCount());
        assertEquals(OptionalInt.of(5), terminal.observedSummary().sourceCount());
    }

    @Test
    void productionFreshnessAdaptersReferenceEveryCapturedTupleCoordinate() throws IOException {
        var transfer = Files.readString(PRODUCTION_STORE_ROOT.resolve("P4E1AuditedCapture.java"));
        var capture = Files.readString(
                PRODUCTION_STORE_ROOT.resolve("P4E1GlobalSourceCapture.java"));
        var journal = Files.readString(
                PRODUCTION_STORE_ROOT.resolve("P4E1PendingJournalObservation.java"));
        var inventory = Files.readString(
                PRODUCTION_STORE_ROOT.resolve("P4E1SourceInventory.java"));
        var service = Files.readString(
                PRODUCTION_STORE_ROOT.resolve("SkillRetentionRootAuditService.java"));

        assertContainsAll(transfer,
                "indexOwnerIdentity == Objects.requireNonNull(candidate, \"candidate\")",
                "serverIdentity == Objects.requireNonNull(candidate, \"candidate\")",
                "Thread.currentThread() == creationThreadIdentity",
                "serverIdentity.isSameThread()",
                "serverIdentity.getTickCount() == capturedTick",
                "serverIdentity.getPlayerList() == playerListIdentity",
                "storeWitness.isCurrent(",
                "journalWitness.currentness(",
                "journalProofsCurrent(",
                "inventoryWitness.isCurrent(",
                "directoryWitness.verifyFinal(selectedFiles)",
                "onlineSourcesCurrent(",
                "integratedAndArbitrationCurrent(");
        assertContainsAll(capture,
                "adapterIdentity == Objects.requireNonNull(adapter, \"adapter\")",
                "adapter.state() == savedDataReadyIdentity",
                "savedDataReadyIdentity.store() == storeIdentity",
                "savedDataReadyIdentity.innerCarrier() == innerCarrierIdentity",
                "savedDataReadyIdentity.storeCarrier() == storeCarrierIdentity",
                "innerCarrierIdentity.pending() == pendingIdentity",
                "server.getPlayerList() != playerListIdentity",
                "player.getServer() != server",
                "current.keySet().equals(expected.keySet())",
                "player != online.playerIdentity()",
                "isOnlineRootWitnessCurrent(",
                "selected.isCurrent(server)",
                "disk.isCurrent(server)",
                "selectedByOwner.isEmpty()",
                "var liveView = playerListIdentity.getPlayers()",
                "var initialLiveSize = liveView.size()",
                "var player = liveView.get(index)",
                "observed.sort(Comparator.comparing(OnlineIdentity::playerId))",
                "var unprocessedOnline = new ArrayList<>(online)",
                "pendingHandle = service.observeOnlineForRootAudit(player)");
        assertContainsAll(journal,
                "adapter != adapterIdentity",
                "adapter.state() != savedDataReadyIdentity",
                "sourcePendingIdentity != innerPendingIdentity",
                "installed.state() != journalReadyIdentity",
                "journalReadyIdentity.targetAuditProof() != proofIdentity",
                "!proofIsSatisfied(proofIdentity)",
                "!proofIdentity.isFor(journalReadyIdentity.journal())");
        assertContainsAll(inventory,
                "definitionIdentity == FIXED_V0",
                "coverage.equals(EnumSet.allOf(P4E1RootSourceFamily.class))",
                "playerProviderIdentity",
                "journalProviderIdentity");
        assertContainsAll(service,
                "return transfer.indexOwnerCurrent(SkillRetentionRootAuditService.this)",
                "transfer.serverIdentityCurrent(SkillRetentionRootAuditService.this, server)",
                "lifecycle.reservationCurrent(",
                "SkillRetentionRootAuditService.this, server, reservation",
                "return reservationCurrent()");
    }

    @Test
    void selectedFileAndIgnoredEntryDriftAreSeparatedOnOneFinalEnumeration()
            throws IOException {
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000081");
        var selectedPath = Files.createFile(temporaryDirectory.resolve(playerId + ".dat"));
        var ignoredPath = Files.createFile(temporaryDirectory.resolve("ignored.txt"));
        var access = new CountingAccess();
        var snapshot = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(
                        temporaryDirectory,
                        P4E1TestBudgets.create(),
                        access,
                        P4E1PlayerDataDirectorySnapshot.Observer.NONE)).snapshot();
        var route = snapshot.records().getFirst();
        var selection = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        P4E1TestBudgets.create(),
                        (input, size, scope) ->
                                new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>("ok"),
                        access,
                        P4E1PlayerDataSourceSelector.Observer.NONE));

        var readsBeforeFinal = access.readOpens;
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.Unchanged.class,
                snapshot.verifyFinal(List.of(selection.witness())));
        assertEquals(2, access.directoryOpens);
        assertEquals(readsBeforeFinal, access.readOpens);

        var selectedSnapshot = captureWithWitness(access, playerId);
        Files.writeString(selectedPath, "selected drift");
        readsBeforeFinal = access.readOpens;
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.SelectedFileLost.class,
                selectedSnapshot.snapshot().verifyFinal(List.of(selectedSnapshot.witness())));
        assertEquals(4, access.directoryOpens);
        assertEquals(readsBeforeFinal, access.readOpens);

        Files.writeString(selectedPath, "");
        var ignoredSnapshot = captureWithWitness(access, playerId);
        Files.writeString(ignoredPath, "ignored drift");
        readsBeforeFinal = access.readOpens;
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.DirectoryRace.class,
                ignoredSnapshot.snapshot().verifyFinal(List.of(ignoredSnapshot.witness())));
        assertEquals(6, access.directoryOpens);
        assertEquals(readsBeforeFinal, access.readOpens);
    }

    @Test
    void addRemoveAndReplaceRemainDirectoryRacesWithoutOpeningContent() throws IOException {
        for (var mode : DirectoryMutation.values()) {
            var directory = Files.createDirectory(
                    temporaryDirectory.resolve("directory-" + mode.name().toLowerCase()));
            var playerId = new UUID(0L, mode.ordinal() + 101L);
            Files.createFile(directory.resolve(playerId + ".dat"));
            var ignored = Files.createFile(directory.resolve("ignored.txt"));
            var access = new CountingAccess();
            var fixture = captureAt(directory, access, playerId);

            switch (mode) {
                case ADD -> Files.createFile(directory.resolve("added.txt"));
                case REMOVE -> Files.delete(ignored);
                case REPLACE -> {
                    Files.delete(ignored);
                    Files.writeString(ignored, "replacement");
                }
            }

            var directoryOpensBefore = access.directoryOpens;
            var readsBefore = access.readOpens;
            assertInstanceOf(
                    P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.DirectoryRace.class,
                    fixture.snapshot().verifyFinal(List.of(fixture.witness())));
            assertEquals(directoryOpensBefore + 1, access.directoryOpens);
            assertEquals(readsBefore, access.readOpens);
        }
    }

    @Test
    void selectedKindFileKeySizeMtimeAndTypeDriftUseTheSelectedFileCode()
            throws IOException {
        for (var drift : AttributeDrift.values()) {
            var directory = Files.createDirectory(
                    temporaryDirectory.resolve("selected-" + drift.name().toLowerCase()));
            var playerId = new UUID(0L, drift.ordinal() + 201L);
            var selected = Files.createFile(directory.resolve(playerId + ".dat"));
            var access = new DriftAccess(selected, drift);
            var fixture = captureAt(directory, access, playerId);
            access.finalPhase = true;

            var directoryOpensBefore = access.directoryOpens;
            var readsBefore = access.readOpens;
            assertInstanceOf(
                    P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.SelectedFileLost.class,
                    fixture.snapshot().verifyFinal(List.of(fixture.witness())));
            assertEquals(directoryOpensBefore + 1, access.directoryOpens);
            assertEquals(readsBefore, access.readOpens);
        }

        var kindDirectory = Files.createDirectory(temporaryDirectory.resolve("selected-kind"));
        var kindPlayer = new UUID(0L, 299L);
        Files.createFile(kindDirectory.resolve(kindPlayer + ".dat"));
        var kindAccess = new CountingAccess();
        var kindFixture = captureAt(kindDirectory, kindAccess, kindPlayer);
        var wrongKind = new P4E1PlayerDataSourceSelector.SelectedFileWitness(
                kindPlayer,
                P4E1PlayerDataSourceSelector.SourceKind.OLD,
                kindFixture.witness().metadata());
        var directoryOpensBefore = kindAccess.directoryOpens;
        var readsBefore = kindAccess.readOpens;
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.SelectedFileLost.class,
                kindFixture.snapshot().verifyFinal(List.of(wrongKind)));
        assertEquals(directoryOpensBefore + 1, kindAccess.directoryOpens);
        assertEquals(readsBefore, kindAccess.readOpens);
    }

    @Test
    void integratedDiskFallbackRetainsExactServerProfileAndSnapshotAbsence() {
        var server = new Object();
        var owner = new UUID(0L, 401L);
        var access = new MutableSnapshotAccess(server, true, owner, null);
        var disk = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Disk.class,
                P4E1IntegratedSnapshotTraversal.captureForGlobal(
                        access, P4E1TestBudgets.create()));

        assertEquals(true, disk.isCurrent(access));
        access.profileId = new UUID(0L, 402L);
        assertEquals(false, disk.isCurrent(access));
        access.profileId = owner;
        access.snapshot = new CompoundTag();
        assertEquals(false, disk.isCurrent(access));
        access.snapshot = null;
        access.serverIdentity = new Object();
        assertEquals(false, disk.isCurrent(access));
        access.serverIdentity = server;
        access.sameThread = false;
        assertEquals(false, disk.isCurrent(access));
    }

    private SnapshotAndWitness captureWithWitness(CountingAccess access, UUID playerId)
            throws IOException {
        return captureAt(temporaryDirectory, access, playerId);
    }

    private static SnapshotAndWitness captureAt(
            Path directory, CountingAccess access, UUID playerId) throws IOException {
        var snapshot = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(
                        directory,
                        P4E1TestBudgets.create(),
                        access,
                        P4E1PlayerDataDirectorySnapshot.Observer.NONE)).snapshot();
        var selection = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        snapshot.records().stream()
                                .filter(record -> record.playerId().equals(playerId))
                                .findFirst()
                                .orElseThrow(),
                        P4E1TestBudgets.create(),
                        (input, size, scope) ->
                                new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>("ok"),
                        access,
                        P4E1PlayerDataSourceSelector.Observer.NONE));
        return new SnapshotAndWitness(snapshot, selection.witness());
    }

    private static void assertFailure(
            Mode mode, P4E1FinalFreshness.FailureCode code, String finalCall) {
        var input = new ScriptedInput(mode);
        var lost = assertInstanceOf(
                P4E1FinalFreshness.VerificationResult.Lost.class,
                P4E1FinalFreshness.verify(input));
        assertEquals(code, lost.code());
        assertEquals(finalCall, input.calls.getLast());
        assertEquals(
                COMPLETE_ORDER.subList(0, COMPLETE_ORDER.indexOf(finalCall) + 1),
                input.calls);
    }

    private static void assertFailures(
            P4E1FinalFreshness.FailureCode code, String finalCall, Mode... modes) {
        for (var mode : modes) {
            assertFailure(mode, code, finalCall);
        }
    }

    private static void assertContainsAll(String source, String... coordinates) {
        for (var coordinate : coordinates) {
            assertTrue(source.contains(coordinate), coordinate);
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private enum Mode {
        CURRENT(null),
        WRONG_SERVICE(Checkpoint.SERVICE),
        WRONG_SERVER(Checkpoint.SERVER),
        WRONG_THREAD(Checkpoint.CALL_CHAIN),
        TICK_DRIFT(Checkpoint.CALL_CHAIN),
        PLAYER_LIST_IDENTITY(Checkpoint.PLAYER_LIST),
        RESERVATION_STATE(Checkpoint.RESERVATION),
        RESERVATION_GENERATION(Checkpoint.RESERVATION),
        STORE_SERVICE(Checkpoint.STORE),
        STORE_ADAPTER(Checkpoint.STORE),
        STORE_READY(Checkpoint.STORE),
        STORE_IDENTITY(Checkpoint.STORE),
        STORE_CARRIER(Checkpoint.STORE),
        STORE_PENDING(Checkpoint.STORE),
        JOURNAL_READY(Checkpoint.JOURNAL_LIFECYCLE),
        JOURNAL_PENDING(Checkpoint.JOURNAL_LIFECYCLE),
        JOURNAL_PROOF_IDENTITY(Checkpoint.JOURNAL_TARGET),
        JOURNAL_PROOF_SATISFACTION(Checkpoint.JOURNAL_TARGET),
        INVENTORY_COVERAGE(Checkpoint.INVENTORY),
        INVENTORY_PLAYER_PROVIDER(Checkpoint.INVENTORY),
        INVENTORY_JOURNAL_PROVIDER(Checkpoint.INVENTORY),
        INVENTORY_FIXED_V0(Checkpoint.INVENTORY),
        DIRECTORY(Checkpoint.DIRECTORY),
        SELECTED_FILE(Checkpoint.SELECTED_FILE),
        ONLINE_UUID_SET(Checkpoint.ONLINE),
        ONLINE_PLAYER_IDENTITY(Checkpoint.ONLINE),
        ONLINE_PLAYER_SERVER(Checkpoint.ONLINE),
        ONLINE_ATTACHMENT_PRESENCE(Checkpoint.ONLINE),
        ONLINE_ATTACHMENT_STATE(Checkpoint.ONLINE),
        INTEGRATED_PROFILE(Checkpoint.INTEGRATED),
        INTEGRATED_SNAPSHOT(Checkpoint.INTEGRATED),
        ARBITRATION(Checkpoint.INTEGRATED),
        FINAL_RESERVATION_STATE(Checkpoint.FINAL_RESERVATION),
        FINAL_RESERVATION_GENERATION(Checkpoint.FINAL_RESERVATION);

        private final Checkpoint failedCheckpoint;

        Mode(Checkpoint failedCheckpoint) {
            this.failedCheckpoint = failedCheckpoint;
        }
    }

    private enum Checkpoint {
        SERVICE,
        SERVER,
        CALL_CHAIN,
        PLAYER_LIST,
        RESERVATION,
        STORE,
        JOURNAL_LIFECYCLE,
        JOURNAL_TARGET,
        INVENTORY,
        DIRECTORY,
        SELECTED_FILE,
        ONLINE,
        INTEGRATED,
        FINAL_RESERVATION
    }

    private enum DirectoryMutation {
        ADD,
        REMOVE,
        REPLACE
    }

    private enum AttributeDrift {
        FILE_KEY,
        SIZE,
        MTIME,
        TYPE
    }

    private static final class ScriptedInput implements P4E1FinalFreshness.Input {
        private final Mode mode;
        private final ArrayList<String> calls = new ArrayList<>();

        private ScriptedInput(Mode mode) {
            this.mode = mode;
        }

        @Override
        public boolean serviceCurrent() {
            return checkpoint("service", Checkpoint.SERVICE);
        }

        @Override
        public boolean serverCurrent() {
            return checkpoint("server", Checkpoint.SERVER);
        }

        @Override
        public boolean callChainCurrent() {
            return checkpoint("call-chain", Checkpoint.CALL_CHAIN);
        }

        @Override
        public boolean playerListCurrent() {
            return checkpoint("player-list", Checkpoint.PLAYER_LIST);
        }

        @Override
        public boolean reservationCurrent() {
            return checkpoint("reservation", Checkpoint.RESERVATION);
        }

        @Override
        public boolean storeCurrent() {
            return checkpoint("store", Checkpoint.STORE);
        }

        @Override
        public P4E1PendingJournalObservation.Currentness journalCurrentness() {
            calls.add("journal");
            if (mode.failedCheckpoint == Checkpoint.JOURNAL_LIFECYCLE) {
                return P4E1PendingJournalObservation.Currentness.LIFECYCLE_UNAVAILABLE;
            }
            if (mode.failedCheckpoint == Checkpoint.JOURNAL_TARGET) {
                return P4E1PendingJournalObservation.Currentness.TARGET_INVALID;
            }
            return P4E1PendingJournalObservation.Currentness.CURRENT;
        }

        @Override
        public boolean inventoryCurrent() {
            return checkpoint("inventory", Checkpoint.INVENTORY);
        }

        @Override
        public P4E1PlayerDataDirectorySnapshot.FinalVerificationResult directoryCurrentness() {
            calls.add("directory");
            if (mode.failedCheckpoint == Checkpoint.DIRECTORY) {
                return P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.DirectoryRace
                        .INSTANCE;
            }
            if (mode.failedCheckpoint == Checkpoint.SELECTED_FILE) {
                return P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.SelectedFileLost
                        .INSTANCE;
            }
            return P4E1PlayerDataDirectorySnapshot.FinalVerificationResult.Unchanged.INSTANCE;
        }

        @Override
        public boolean onlineCurrent() {
            return checkpoint("online", Checkpoint.ONLINE);
        }

        @Override
        public boolean integratedAndArbitrationCurrent() {
            return checkpoint("integrated", Checkpoint.INTEGRATED);
        }

        @Override
        public boolean reservationStillCurrent() {
            return checkpoint("reservation-final", Checkpoint.FINAL_RESERVATION);
        }

        private boolean checkpoint(String name, Checkpoint failedCheckpoint) {
            calls.add(name);
            return mode.failedCheckpoint != failedCheckpoint;
        }
    }

    private record SnapshotAndWitness(
            P4E1PlayerDataDirectorySnapshot snapshot,
            P4E1PlayerDataSourceSelector.SelectedFileWitness witness) {
    }

    private static class CountingAccess extends P4E1FileSystemAccess {
        int directoryOpens;
        int readOpens;

        @Override
        BasicFileAttributes readAttributes(Path path) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.readAttributes(path);
        }

        @Override
        DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            directoryOpens++;
            return P4E1FileSystemAccess.SYSTEM.openDirectory(directory);
        }

        @Override
        FileChannel openRead(Path path) throws IOException {
            readOpens++;
            return P4E1FileSystemAccess.SYSTEM.openRead(path);
        }
    }

    private static final class DriftAccess extends CountingAccess {
        private final Path selected;
        private final AttributeDrift drift;
        private boolean finalPhase;

        private DriftAccess(Path selected, AttributeDrift drift) {
            this.selected = selected;
            this.drift = drift;
        }

        @Override
        BasicFileAttributes readAttributes(Path path) throws IOException {
            var attributes = super.readAttributes(path);
            if (!finalPhase || !selected.equals(path)) {
                return attributes;
            }
            return drifted(attributes, drift);
        }

        private static BasicFileAttributes drifted(
                BasicFileAttributes delegate, AttributeDrift drift) {
            return new BasicFileAttributes() {
                @Override
                public FileTime lastModifiedTime() {
                    return drift == AttributeDrift.MTIME
                            ? FileTime.fromMillis(delegate.lastModifiedTime().toMillis() + 1L)
                            : delegate.lastModifiedTime();
                }

                @Override
                public FileTime lastAccessTime() {
                    return delegate.lastAccessTime();
                }

                @Override
                public FileTime creationTime() {
                    return delegate.creationTime();
                }

                @Override
                public boolean isRegularFile() {
                    return drift == AttributeDrift.TYPE ? false : delegate.isRegularFile();
                }

                @Override
                public boolean isDirectory() {
                    return delegate.isDirectory();
                }

                @Override
                public boolean isSymbolicLink() {
                    return drift == AttributeDrift.TYPE || delegate.isSymbolicLink();
                }

                @Override
                public boolean isOther() {
                    return delegate.isOther();
                }

                @Override
                public long size() {
                    return drift == AttributeDrift.SIZE
                            ? Math.addExact(delegate.size(), 1L)
                            : delegate.size();
                }

                @Override
                public Object fileKey() {
                    return drift == AttributeDrift.FILE_KEY
                            ? new Object()
                            : delegate.fileKey();
                }
            };
        }
    }

    private static final class MutableSnapshotAccess
            implements P4E1IntegratedSnapshotTraversal.SnapshotAccess {
        private Object serverIdentity;
        private boolean sameThread;
        private UUID profileId;
        private CompoundTag snapshot;

        private MutableSnapshotAccess(
                Object serverIdentity,
                boolean sameThread,
                UUID profileId,
                CompoundTag snapshot) {
            this.serverIdentity = serverIdentity;
            this.sameThread = sameThread;
            this.profileId = profileId;
            this.snapshot = snapshot;
        }

        @Override
        public Object serverIdentity() {
            return serverIdentity;
        }

        @Override
        public boolean isSameThread() {
            return sameThread;
        }

        @Override
        public UUID profileId() {
            return profileId;
        }

        @Override
        public CompoundTag loadedPlayerSnapshot() {
            return snapshot;
        }
    }
}
