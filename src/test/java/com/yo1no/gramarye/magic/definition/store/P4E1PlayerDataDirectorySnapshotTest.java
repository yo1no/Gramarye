package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P4E1PlayerDataDirectorySnapshotTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exactDirectoryLimitIsAcceptedAndFinalWitnessIsStable() throws IOException {
        for (var index = 0; index < 4_096; index++) {
            Files.createFile(temporaryDirectory.resolve("ignored-" + index));
        }

        var budget = P4E1TestBudgets.create();
        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, budget));

        assertEquals(4_096, snapshot.entryCount());
        assertEquals(4_096, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.VerificationResult.Unchanged.class,
                snapshot.verifyUnchanged());
    }

    @Test
    void directoryLimitPlusOneStopsBeforeClassification() throws IOException {
        for (var index = 0; index < 4_097; index++) {
            Files.createFile(temporaryDirectory.resolve("ignored-" + index));
        }

        var budget = P4E1TestBudgets.create();
        var failure = failed(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, budget));

        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED, failure.code());
        assertEquals(
                P4E1AuditCounter.DIRECTORY_ENTRIES,
                failure.counter().orElseThrow());
        assertEquals(4_097L, failure.observedAtLeast());
        assertEquals(4_096L, failure.maximum());
        assertEquals(4_096L, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
    }

    @Test
    void exactRelevantLimitPairsPrimaryAndOldAndSortsNaturally() throws IOException {
        for (var index = 0; index < 2_048; index++) {
            var playerId = new UUID(index - 1_024L, 4_096L - index);
            Files.createFile(temporaryDirectory.resolve(playerId + ".dat"));
            Files.createFile(temporaryDirectory.resolve(playerId + ".dat_old"));
        }

        var budget = P4E1TestBudgets.create();
        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, budget));
        var records = selected(snapshot.selectRecords(Optional.empty(), budget));
        var actual = new ArrayList<UUID>();
        for (var record : records) {
            actual.add(record.playerId());
        }
        var sorted = new ArrayList<>(actual);
        sorted.sort(UUID::compareTo);

        assertEquals(2_048, records.size());
        assertEquals(2_048L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
        assertEquals(sorted, actual);
    }

    @Test
    void relevantLimitPlusOneFailsWithoutCommittingTheExtraRoute() throws IOException {
        for (var index = 0; index < 2_049; index++) {
            var playerId = new UUID(0L, index + 1L);
            Files.createFile(temporaryDirectory.resolve(playerId + ".dat"));
        }

        var budget = P4E1TestBudgets.create();
        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, budget));
        var failure = selectionFailed(snapshot.selectRecords(Optional.empty(), budget));

        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED, failure.code());
        assertEquals(P4E1AuditCounter.RELEVANT_RECORDS, failure.counter().orElseThrow());
        assertEquals(2_049L, failure.observedAtLeast());
        assertEquals(2_048L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
    }

    @Test
    void canonicalPrimaryAndOldAreOneRoute() throws IOException {
        var playerId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        Files.createFile(temporaryDirectory.resolve(playerId + ".dat"));
        Files.createFile(temporaryDirectory.resolve(playerId + ".dat_old"));

        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create()));
        var records = selected(snapshot.selectRecords(
                Optional.empty(), P4E1TestBudgets.create()));

        assertEquals(1, records.size());
        assertEquals(playerId, records.getFirst().playerId());
    }

    @Test
    void uppercaseUuidCaseAliasParseableNoncanonicalAndMalformedUuidShapeFailClosed()
            throws IOException {
        var playerId = UUID.fromString("01234567-89ab-cdef-8123-456789abcdef");
        var names = new String[] {
            playerId.toString().toUpperCase(java.util.Locale.ROOT) + ".dat",
            playerId + ".DAT",
            "1-1-1-1-1.dat",
            "gggggggg-gggg-gggg-gggg-gggggggggggg.dat"
        };

        for (var index = 0; index < names.length; index++) {
            var directory = Files.createDirectory(temporaryDirectory.resolve("case-" + index));
            Files.createFile(directory.resolve(names[index]));
            var failure = failed(P4E1PlayerDataDirectorySnapshot.capture(
                    directory, P4E1TestBudgets.create()));
            assertEquals(
                    P4E1SourceFailure.Code.PLAYERDATA_NAME_NONCANONICAL,
                    failure.code());
            assertTrue(failure.exceptionClassName().isEmpty());
        }
    }

    @Test
    void hiddenTemporaryCorruptedAndIrrelevantNamesRemainIgnoredButCounted()
            throws IOException {
        var playerId = UUID.fromString("01234567-89ab-cdef-8123-456789abcdef");
        var names = new String[] {
            ".hidden",
            playerId + ".dat.tmp",
            playerId + ".dat_corrupted_1234",
            "not-a-uuid.dat",
            "notes.txt"
        };
        for (var name : names) {
            Files.createFile(temporaryDirectory.resolve(name));
        }

        var budget = P4E1TestBudgets.create();
        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, budget));
        var records = selected(snapshot.selectRecords(Optional.empty(), budget));

        assertEquals(0, records.size());
        assertEquals(names.length, snapshot.entryCount());
        assertEquals(names.length, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
    }

    @Test
    void symlinkAndRegularFileDirectoryRootsAreUnsupported() throws IOException {
        var actualDirectory = Files.createDirectory(temporaryDirectory.resolve("actual"));
        var link = temporaryDirectory.resolve("link");
        Files.createSymbolicLink(link, actualDirectory);
        var file = Files.createFile(temporaryDirectory.resolve("file"));

        assertEquals(
                P4E1SourceFailure.Code.DIRECTORY_TYPE_UNSUPPORTED,
                failed(P4E1PlayerDataDirectorySnapshot.capture(
                        link, P4E1TestBudgets.create())).code());
        assertEquals(
                P4E1SourceFailure.Code.DIRECTORY_TYPE_UNSUPPORTED,
                failed(P4E1PlayerDataDirectorySnapshot.capture(
                        file, P4E1TestBudgets.create())).code());
    }

    @Test
    void nullDirectoryFileKeyFailsClosed() {
        var access = new DelegatingAccess() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                return withoutFileKey(super.readAttributes(path));
            }
        };

        var failure = failed(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create(), access,
                P4E1PlayerDataDirectorySnapshot.Observer.NONE));

        assertEquals(
                P4E1SourceFailure.Code.DIRECTORY_IDENTITY_UNAVAILABLE,
                failure.code());
    }

    @Test
    void iteratorFailureDuringInitialEnumerationIsBoundedUnreadable() {
        var access = new DelegatingAccess() {
            @Override
            public DirectoryStream<Path> openDirectory(Path directory) {
                return failingDirectoryStream();
            }
        };

        var failure = failed(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create(), access,
                P4E1PlayerDataDirectorySnapshot.Observer.NONE));

        assertEquals(P4E1SourceFailure.Code.DIRECTORY_UNREADABLE, failure.code());
    }

    @Test
    void iteratorFailureDuringFinalEnumerationIsDirectoryRace() {
        var access = new DelegatingAccess() {
            private int directoryOpens;

            @Override
            public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
                directoryOpens++;
                return directoryOpens == 1
                        ? super.openDirectory(directory)
                        : failingDirectoryStream();
            }
        };
        var snapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create(), access,
                P4E1PlayerDataDirectorySnapshot.Observer.NONE));

        assertRace(snapshot.verifyUnchanged());
    }

    @Test
    void addRemoveReplaceAndIgnoredEntryMutationAreDirectoryRaces() throws IOException {
        var original = Files.createFile(temporaryDirectory.resolve("original.txt"));
        var ignored = Files.createFile(temporaryDirectory.resolve("ignored.txt"));
        var addedSnapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create()));
        Files.createFile(temporaryDirectory.resolve("added.txt"));
        assertRace(addedSnapshot.verifyUnchanged());

        Files.delete(temporaryDirectory.resolve("added.txt"));
        var removedSnapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create()));
        Files.delete(original);
        assertRace(removedSnapshot.verifyUnchanged());

        Files.createFile(original);
        var replacedSnapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create()));
        Files.delete(original);
        Files.writeString(original, "replacement");
        assertRace(replacedSnapshot.verifyUnchanged());

        var ignoredSnapshot = ready(P4E1PlayerDataDirectorySnapshot.capture(
                temporaryDirectory, P4E1TestBudgets.create()));
        Files.writeString(ignored, "changed ignored entry");
        assertRace(ignoredSnapshot.verifyUnchanged());
    }

    private static void assertRace(
            P4E1PlayerDataDirectorySnapshot.VerificationResult result) {
        var failure = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.VerificationResult.Failure.class,
                result);
        assertEquals(P4E1SourceFailure.Code.DIRECTORY_RACE_DETECTED,
                failure.failure().code());
    }

    private static P4E1PlayerDataDirectorySnapshot ready(
            P4E1PlayerDataDirectorySnapshot.CaptureResult result) {
        return assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                result).snapshot();
    }

    private static P4E1SourceFailure failed(
            P4E1PlayerDataDirectorySnapshot.CaptureResult result) {
        return assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Failure.class,
                result).failure();
    }

    private static List<P4E1PlayerDataDirectorySnapshot.RouteRecord> selected(
            P4E1PlayerDataDirectorySnapshot.RecordSelection result) {
        return assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Ready.class,
                result).records();
    }

    private static P4E1SourceFailure selectionFailed(
            P4E1PlayerDataDirectorySnapshot.RecordSelection result) {
        return assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Failure.class,
                result).failure();
    }

    private static BasicFileAttributes withoutFileKey(BasicFileAttributes delegate) {
        return new BasicFileAttributes() {
            @Override
            public FileTime lastModifiedTime() {
                return delegate.lastModifiedTime();
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
                return delegate.isRegularFile();
            }

            @Override
            public boolean isDirectory() {
                return delegate.isDirectory();
            }

            @Override
            public boolean isSymbolicLink() {
                return delegate.isSymbolicLink();
            }

            @Override
            public boolean isOther() {
                return delegate.isOther();
            }

            @Override
            public long size() {
                return delegate.size();
            }

            @Override
            public Object fileKey() {
                return null;
            }
        };
    }

    private static DirectoryStream<Path> failingDirectoryStream() {
        return new DirectoryStream<>() {
            @Override
            public Iterator<Path> iterator() {
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        throw new DirectoryIteratorException(
                                new IOException("synthetic directory iteration failure"));
                    }

                    @Override
                    public Path next() {
                        throw new AssertionError("next must not be called");
                    }
                };
            }

            @Override
            public void close() {}
        };
    }

    private static class DelegatingAccess extends P4E1FileSystemAccess {
        @Override
        public BasicFileAttributes readAttributes(Path path) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.readAttributes(path);
        }

        @Override
        public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.openDirectory(directory);
        }

        @Override
        public FileChannel openRead(Path path) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.openRead(path);
        }
    }
}
