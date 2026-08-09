package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P4E1PlayerDataSourceSelectorTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("01234567-89ab-cdef-8123-456789abcdef");

    @TempDir
    Path temporaryDirectory;

    @Test
    void validPrimaryWinsWithoutAnyOldPathAccess() throws IOException {
        var route = route();
        Files.writeString(route.primary(), "primary");
        Files.writeString(route.old(), "malformed-old");
        var access = new CountingAccess();

        var ready = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route, P4E1TestBudgets.create(), firstByteReader(), access,
                        P4E1PlayerDataSourceSelector.Observer.NONE));

        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.PRIMARY, ready.source());
        assertEquals((byte) 'p', ready.value());
        assertEquals(1, access.openCount(route.primary()));
        assertEquals(0, access.openCount(route.old()));
        assertEquals(0, access.attributeCount(route.old()));
    }

    @Test
    void stableMissingPrimarySelectsOldAndBothMissingProduceZero() throws IOException {
        var route = route();
        Files.writeString(route.old(), "old");

        var ready = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route, P4E1TestBudgets.create(), firstByteReader()));
        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.OLD, ready.source());
        assertEquals((byte) 'o', ready.value());

        Files.delete(route.old());
        var zero = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Zero.class,
                P4E1PlayerDataSourceSelector.select(
                        route, P4E1TestBudgets.create(), firstByteReader()));
        assertEquals(PLAYER_ID, zero.playerId());
    }

    @Test
    void disappearanceOfASnapshottedActiveRouteIsRace() throws IOException {
        var ordinary = route();
        Files.writeString(ordinary.old(), "old");
        var snapshotted = new P4E1PlayerDataDirectorySnapshot.RouteRecord(
                PLAYER_ID,
                ordinary.primary(),
                ordinary.old(),
                true,
                true);

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                snapshotted, P4E1TestBudgets.create(), firstByteReader()));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED, failure.code());
    }

    @Test
    void fileAppearingBetweenAbsentChecksIsRaceAndDoesNotFallBack() throws IOException {
        var route = route();
        Files.writeString(route.old(), "old");
        var observer = new P4E1PlayerDataSourceSelector.Observer() {
            @Override
            public void afterFirstAbsentCheck(P4E1PlayerDataSourceSelector.SourceKind source) {
                if (source == P4E1PlayerDataSourceSelector.SourceKind.PRIMARY) {
                    try {
                        Files.writeString(route.primary(), "appeared");
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }
            }
        };

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                firstByteReader(),
                P4E1FileSystemAccess.SYSTEM,
                observer));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED, failure.code());
    }

    @Test
    void provenPlatformFailureFallsBackAndKeepsAggregateWork() throws IOException {
        var route = route();
        Files.writeString(route.primary(), "platform-invalid");
        Files.writeString(route.old(), "old-valid");
        var budget = P4E1TestBudgets.create();

        var result = P4E1PlayerDataSourceSelector.select(
                route,
                budget,
                (input, size, scope) -> {
                    var first = readFirst(input.channel());
                    var delta = first == (byte) 'p' ? 5L : 3L;
                    var exceeded = scope.checkpointFileAndAggregate(
                            P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                            P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                            P4E1AuditStage.PER_FILE_COMPRESSED,
                            P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                            delta);
                    if (exceeded.isPresent()) {
                        return new P4E1PlayerDataSourceSelector.SourceReadResult.Failure<>(
                                P4E1PlayerDataSourceSelector.FailureCategory
                                        .STRICT_ONLY_REJECTION,
                                P4E1SourceFailure.capacity(exceeded.orElseThrow()));
                    }
                    return first == (byte) 'p'
                            ? readFailure(
                                    P4E1PlayerDataSourceSelector.FailureCategory
                                            .PLATFORM_READ_FAILURE_PROVEN,
                                    P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN)
                            : new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(first);
                });

        var ready = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class, result);
        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.OLD, ready.source());
        assertEquals(8L, budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL));
    }

    @Test
    void provenFailureWithUnusableOldIsIncomplete() throws IOException {
        var route = route();
        Files.writeString(route.primary(), "primary");
        Files.createDirectory(route.old());

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                (input, size, scope) -> readFailure(
                        P4E1PlayerDataSourceSelector.FailureCategory
                                .PLATFORM_READ_FAILURE_PROVEN,
                        P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN)));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_TYPE_UNSUPPORTED, failure.code());
    }

    @Test
    void strictSemanticAndCounterFailuresNeverOpenOld() throws IOException {
        var categories = new P4E1PlayerDataSourceSelector.FailureCategory[] {
            P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION,
            P4E1PlayerDataSourceSelector.FailureCategory.POST_NBT_SEMANTIC_FAILURE
        };
        for (var index = 0; index < categories.length; index++) {
            var category = categories[index];
            var directory = Files.createDirectory(temporaryDirectory.resolve("strict-" + index));
            var route = route(directory);
            Files.writeString(route.primary(), "primary");
            Files.writeString(route.old(), "old");
            var access = new CountingAccess();

            failed(P4E1PlayerDataSourceSelector.select(
                    route,
                    P4E1TestBudgets.create(),
                    (input, size, scope) -> readFailure(
                            category,
                            category
                                            == P4E1PlayerDataSourceSelector.FailureCategory
                                                    .STRICT_ONLY_REJECTION
                                    ? P4E1SourceFailure.Code.STRICT_GZIP_REJECTED
                                    : P4E1SourceFailure.Code.DATA_VERSION_NOT_CURRENT),
                    access,
                    P4E1PlayerDataSourceSelector.Observer.NONE));
            assertEquals(0, access.openCount(route.old()));
            assertEquals(0, access.attributeCount(route.old()));
        }
    }

    @Test
    void directorySymlinkAndNullFileKeyNeverFallBack() throws IOException {
        var route = route();
        Files.createDirectory(route.primary());
        Files.writeString(route.old(), "old");
        var access = new CountingAccess();

        var typeFailure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                firstByteReader(),
                access,
                P4E1PlayerDataSourceSelector.Observer.NONE));
        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_TYPE_UNSUPPORTED,
                typeFailure.code());
        assertEquals(0, access.attributeCount(route.old()));

        Files.delete(route.primary());
        Files.writeString(route.primary(), "primary");
        var nullKeyAccess = new CountingAccess() {
            @Override
            public BasicFileAttributes readAttributes(Path path) throws IOException {
                var attributes = super.readAttributes(path);
                return path.equals(route.primary()) ? withoutFileKey(attributes) : attributes;
            }
        };
        var identityFailure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                firstByteReader(),
                nullKeyAccess,
                P4E1PlayerDataSourceSelector.Observer.NONE));
        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_IDENTITY_UNAVAILABLE,
                identityFailure.code());
        assertEquals(0, nullKeyAccess.attributeCount(route.old()));
    }

    @Test
    void selectedSymlinkIsUnsupportedAndDoesNotOpenOld() throws IOException {
        var route = route();
        var target = Files.writeString(temporaryDirectory.resolve("target.bin"), "target");
        Files.createSymbolicLink(route.primary(), target);
        Files.writeString(route.old(), "old");
        var access = new CountingAccess();

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                firstByteReader(),
                access,
                P4E1PlayerDataSourceSelector.Observer.NONE));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_TYPE_UNSUPPORTED, failure.code());
        assertEquals(0, access.openCount(route.primary()));
        assertEquals(0, access.attributeCount(route.old()));
    }

    @Test
    void exactCompressedMaximumIsAcceptedAndMaximumPlusOneIsRejected()
            throws IOException {
        var exactDirectory = Files.createDirectory(temporaryDirectory.resolve("exact"));
        var exact = route(exactDirectory);
        setSparseLength(exact.primary(), 33_559_514L);
        var accepted = P4E1PlayerDataSourceSelector.select(
                exact,
                P4E1TestBudgets.create(),
                (input, size, scope) ->
                        new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(size));
        assertEquals(
                33_559_514L,
                assertInstanceOf(
                        P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                        accepted).value());

        var overDirectory = Files.createDirectory(temporaryDirectory.resolve("over"));
        var over = route(overDirectory);
        setSparseLength(over.primary(), 33_559_515L);
        Files.writeString(over.old(), "old");
        var access = new CountingAccess();
        var failure = failed(P4E1PlayerDataSourceSelector.select(
                over,
                P4E1TestBudgets.create(),
                firstByteReader(),
                access,
                P4E1PlayerDataSourceSelector.Observer.NONE));
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED, failure.code());
        assertEquals(33_559_515L, failure.observedAtLeast());
        assertEquals(0, access.attributeCount(over.old()));
        assertEquals(0, access.openCount(over.old()));
    }

    @Test
    void growShrinkAndReplacementAfterOpenAreRaces() throws IOException {
        assertMutationRace("grow", (route, source) -> Files.writeString(
                route.primary(),
                "grew",
                java.nio.file.StandardOpenOption.APPEND));
        assertMutationRace("shrink", (route, source) -> {
            try (var channel = FileChannel.open(
                    route.primary(), java.nio.file.StandardOpenOption.WRITE)) {
                channel.truncate(1L);
            }
        });
        assertMutationRace("replace", (route, source) -> {
            Files.delete(route.primary());
            Files.writeString(route.primary(), "other--content!");
        });
    }

    @Test
    void preOpenGrowthIsRaceBeforeReaderAndNeverFallsBack() throws IOException {
        var route = route(Files.createDirectory(temporaryDirectory.resolve("pre-open")));
        Files.writeString(route.primary(), "initial-content");
        Files.writeString(route.old(), "old");
        var access = new CountingAccess();
        var readerCalls = new AtomicInteger();
        var observer = new P4E1PlayerDataSourceSelector.Observer() {
            @Override
            public void afterInitialMetadata(P4E1PlayerDataSourceSelector.SourceKind source) {
                try {
                    Files.writeString(
                            route.primary(),
                            "growth",
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                (input, size, scope) -> {
                    readerCalls.incrementAndGet();
                    return new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(size);
                },
                access,
                observer));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED, failure.code());
        assertEquals(0, readerCalls.get());
        assertEquals(0, access.attributeCount(route.old()));
        assertEquals(0, access.openCount(route.old()));
    }

    @Test
    void postReadGrowthOverridesReadyAndNeverFallsBack() throws IOException {
        var route = route(Files.createDirectory(temporaryDirectory.resolve("post-read")));
        Files.writeString(route.primary(), "initial-content");
        Files.writeString(route.old(), "old");
        var access = new CountingAccess();
        var observer = new P4E1PlayerDataSourceSelector.Observer() {
            @Override
            public void afterRead(P4E1PlayerDataSourceSelector.SourceKind source) {
                try {
                    Files.writeString(
                            route.primary(),
                            "growth",
                            java.nio.file.StandardOpenOption.APPEND);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                (input, size, scope) ->
                        new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(size),
                access,
                observer));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED, failure.code());
        assertEquals(0, access.attributeCount(route.old()));
        assertEquals(0, access.openCount(route.old()));
    }

    @Test
    void selectedOpenFailureIsUnreadableAndNeverFallsBack() throws IOException {
        var route = route(Files.createDirectory(temporaryDirectory.resolve("open-failure")));
        Files.writeString(route.primary(), "primary");
        Files.writeString(route.old(), "old");
        var access = new CountingAccess() {
            @Override
            public FileChannel openRead(Path path) throws IOException {
                if (path.equals(route.primary())) {
                    throw new IOException("fixture open failure");
                }
                return super.openRead(path);
            }
        };

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                firstByteReader(),
                access,
                P4E1PlayerDataSourceSelector.Observer.NONE));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_UNREADABLE, failure.code());
        assertEquals(0, access.attributeCount(route.old()));
        assertEquals(0, access.openCount(route.old()));
    }

    private void assertMutationRace(String name, Mutation mutation) throws IOException {
        var directory = Files.createDirectory(temporaryDirectory.resolve(name));
        var route = route(directory);
        Files.writeString(route.primary(), "initial-content");
        Files.writeString(route.old(), "old");
        var access = new CountingAccess();
        var observer = new P4E1PlayerDataSourceSelector.Observer() {
            @Override
            public void afterPostOpenChecks(P4E1PlayerDataSourceSelector.SourceKind source) {
                try {
                    mutation.apply(route, source);
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };

        var failure = failed(P4E1PlayerDataSourceSelector.select(
                route,
                P4E1TestBudgets.create(),
                (input, size, scope) ->
                        new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(size),
                access,
                observer));

        assertEquals(P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED, failure.code());
        assertEquals(0, access.attributeCount(route.old()));
        assertEquals(0, access.openCount(route.old()));
    }

    private P4E1PlayerDataDirectorySnapshot.RouteRecord route() {
        return route(temporaryDirectory);
    }

    private static P4E1PlayerDataDirectorySnapshot.RouteRecord route(Path directory) {
        return new P4E1PlayerDataDirectorySnapshot.RouteRecord(
                PLAYER_ID,
                directory.resolve(PLAYER_ID + ".dat"),
                directory.resolve(PLAYER_ID + ".dat_old"));
    }

    private static P4E1PlayerDataSourceSelector.SourceReader<Byte> firstByteReader() {
        return (input, size, scope) ->
                new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(
                        readFirst(input.channel()));
    }

    private static byte readFirst(FileChannel channel) throws IOException {
        var buffer = ByteBuffer.allocate(1);
        if (channel.read(buffer) != 1) {
            throw new IOException("fixture contains no byte");
        }
        return buffer.array()[0];
    }

    private static <T> P4E1PlayerDataSourceSelector.SourceReadResult<T> readFailure(
            P4E1PlayerDataSourceSelector.FailureCategory category,
            P4E1SourceFailure.Code code) {
        return new P4E1PlayerDataSourceSelector.SourceReadResult.Failure<>(
                category,
                P4E1SourceFailure.simple(code, P4E1AuditStage.SOURCE_SELECTION));
    }

    private static P4E1SourceFailure failed(
            P4E1PlayerDataSourceSelector.SelectionResult<?> result) {
        return assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Failure.class,
                result).failure();
    }

    private static void setSparseLength(Path path, long length) throws IOException {
        try (var channel = FileChannel.open(
                path,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(length - 1L);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
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

    @FunctionalInterface
    private interface Mutation {
        void apply(
                P4E1PlayerDataDirectorySnapshot.RouteRecord route,
                P4E1PlayerDataSourceSelector.SourceKind source) throws IOException;
    }

    private static class CountingAccess extends P4E1FileSystemAccess {
        private final Map<Path, Integer> attributes = new HashMap<>();
        private final Map<Path, Integer> opens = new HashMap<>();

        @Override
        public BasicFileAttributes readAttributes(Path path) throws IOException {
            attributes.merge(path, 1, Integer::sum);
            return P4E1FileSystemAccess.SYSTEM.readAttributes(path);
        }

        @Override
        public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.openDirectory(directory);
        }

        @Override
        public FileChannel openRead(Path path) throws IOException {
            opens.merge(path, 1, Integer::sum);
            return P4E1FileSystemAccess.SYSTEM.openRead(path);
        }

        final int attributeCount(Path path) {
            return attributes.getOrDefault(path, 0);
        }

        final int openCount(Path path) {
            return opens.getOrDefault(path, 0);
        }
    }
}
