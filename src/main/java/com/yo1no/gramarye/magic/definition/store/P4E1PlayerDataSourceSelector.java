package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Exact read-only primary/{@code .dat_old} selector for one canonical player route. */
final class P4E1PlayerDataSourceSelector {
    private P4E1PlayerDataSourceSelector() {
    }

    static <T> SelectionResult<T> select(
            P4E1PlayerDataDirectorySnapshot.RouteRecord route,
            P4E1AuditBudget budget,
            SourceReader<T> reader) {
        return select(
                route,
                budget,
                reader,
                P4E1FileSystemAccess.SYSTEM,
                Observer.NONE);
    }

    static <T> SelectionResult<T> select(
            P4E1PlayerDataDirectorySnapshot.RouteRecord route,
            P4E1AuditBudget budget,
            SourceReader<T> reader,
            P4E1FileSystemAccess fileSystem,
            Observer observer) {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(reader, "reader");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(observer, "observer");

        var primary = attempt(
                route.playerId(),
                route.primary(),
                SourceKind.PRIMARY,
                route.primaryObserved(),
                budget,
                reader,
                fileSystem,
                observer);
        if (primary instanceof AttemptResult.Ready<T> ready) {
            return new SelectionResult.Ready<>(
                    route.playerId(), SourceKind.PRIMARY, ready.value());
        }
        if (primary instanceof AttemptResult.Failure<T> failed
                && failed.category() != FailureCategory.PLATFORM_READ_FAILURE_PROVEN) {
            return new SelectionResult.Failure<>(failed.failure());
        }

        var old = attempt(
                route.playerId(),
                route.old(),
                SourceKind.OLD,
                route.oldObserved(),
                budget,
                reader,
                fileSystem,
                observer);
        if (old instanceof AttemptResult.Ready<T> ready) {
            return new SelectionResult.Ready<>(
                    route.playerId(), SourceKind.OLD, ready.value());
        }
        if (old instanceof AttemptResult.Failure<T> failed) {
            return new SelectionResult.Failure<>(failed.failure());
        }
        if (primary instanceof AttemptResult.Failure<T> failed) {
            return new SelectionResult.Failure<>(failed.failure());
        }
        return new SelectionResult.Zero<>(route.playerId());
    }

    private static <T> AttemptResult<T> attempt(
            UUID playerId,
            Path path,
            SourceKind source,
            boolean observedInDirectorySnapshot,
            P4E1AuditBudget budget,
            SourceReader<T> reader,
            P4E1FileSystemAccess fileSystem,
            Observer observer) {
        final P4E1FileMetadata baseline;
        try {
            baseline = P4E1FileMetadata.capture(fileSystem.readAttributes(path));
        } catch (NoSuchFileException firstAbsent) {
            if (observedInDirectorySnapshot) {
                return race(playerId);
            }
            observer.afterFirstAbsentCheck(source);
            try {
                fileSystem.readAttributes(path);
                return race(playerId);
            } catch (NoSuchFileException secondAbsent) {
                return new AttemptResult.Absent<>();
            } catch (IOException exception) {
                return unreadable(playerId);
            }
        } catch (IOException exception) {
            return unreadable(playerId);
        }

        if (baseline.symbolicLink() || !baseline.regularFile()) {
            return failure(
                    FailureCategory.FILESYSTEM_OR_RACE_FAILURE,
                    P4E1SourceFailure.forRoute(
                            P4E1SourceFailure.Code.PRIMARY_FILE_TYPE_UNSUPPORTED,
                            P4E1AuditStage.SOURCE_SELECTION,
                            playerId));
        }
        if (baseline.fileKey() == null) {
            return failure(
                    FailureCategory.FILESYSTEM_OR_RACE_FAILURE,
                    P4E1SourceFailure.forRoute(
                            P4E1SourceFailure.Code.PRIMARY_FILE_IDENTITY_UNAVAILABLE,
                            P4E1AuditStage.SOURCE_SELECTION,
                            playerId));
        }

        var compressedMaximum = budget.maximum(P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE);
        if (baseline.size() > compressedMaximum) {
            return failure(
                    FailureCategory.STRICT_ONLY_REJECTION,
                    P4E1SourceFailure.capacity(
                            P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                            P4E1AuditStage.PER_FILE_COMPRESSED,
                            compressedMaximum + 1L,
                            compressedMaximum,
                            playerId));
        }
        observer.afterInitialMetadata(source);

        final FileChannel channel;
        try {
            channel = fileSystem.openRead(path);
        } catch (NoSuchFileException exception) {
            return race(playerId);
        } catch (IOException exception) {
            return unreadable(playerId);
        }

        AttemptResult<T> result;
        try (channel) {
            var postOpen = readMetadata(path, fileSystem);
            if (postOpen instanceof MetadataResult.Failure failed) {
                return failed.race() ? race(playerId) : unreadable(playerId);
            }
            final long postOpenChannelSize;
            try {
                postOpenChannelSize = channel.size();
            } catch (IOException exception) {
                return unreadable(playerId);
            }
            if (!stable(baseline, ((MetadataResult.Ready) postOpen).metadata(),
                    postOpenChannelSize)) {
                return race(playerId);
            }
            observer.afterPostOpenChecks(source);

            var scope = budget.newFileScope();
            var read = Objects.requireNonNull(
                    reader.read(new SourceInput(channel), baseline.size(), scope),
                    "source read result");
            result = switch (read) {
                case SourceReadResult.Ready<T> ready ->
                        new AttemptResult.Ready<>(ready.value());
                case SourceReadResult.Failure<T> failed ->
                        new AttemptResult.Failure<>(failed.category(), failed.failure());
            };
            observer.afterRead(source);

            var finalMetadata = readMetadata(path, fileSystem);
            if (finalMetadata instanceof MetadataResult.Failure failed) {
                return failed.race() ? race(playerId) : unreadable(playerId);
            }
            final long finalChannelSize;
            try {
                finalChannelSize = channel.size();
            } catch (IOException exception) {
                return unreadable(playerId);
            }
            if (!stable(baseline, ((MetadataResult.Ready) finalMetadata).metadata(),
                    finalChannelSize)) {
                return race(playerId);
            }
            observer.afterFinalChecks(source);
        } catch (IOException exception) {
            return unreadable(playerId);
        }
        return result;
    }

    private static MetadataResult readMetadata(
            Path path, P4E1FileSystemAccess fileSystem) {
        try {
            return new MetadataResult.Ready(
                    P4E1FileMetadata.capture(fileSystem.readAttributes(path)));
        } catch (NoSuchFileException exception) {
            return new MetadataResult.Failure(true);
        } catch (IOException exception) {
            return new MetadataResult.Failure(false);
        }
    }

    private static boolean stable(
            P4E1FileMetadata baseline,
            P4E1FileMetadata observed,
            long channelSize) {
        return channelSize == baseline.size() && baseline.sameIdentityAndShape(observed);
    }

    private static <T> AttemptResult.Failure<T> unreadable(UUID playerId) {
        return failure(
                FailureCategory.FILESYSTEM_OR_RACE_FAILURE,
                P4E1SourceFailure.forRoute(
                        P4E1SourceFailure.Code.PRIMARY_FILE_UNREADABLE,
                        P4E1AuditStage.SOURCE_SELECTION,
                        playerId));
    }

    private static <T> AttemptResult.Failure<T> race(UUID playerId) {
        return failure(
                FailureCategory.FILESYSTEM_OR_RACE_FAILURE,
                P4E1SourceFailure.forRoute(
                        P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED,
                        P4E1AuditStage.SOURCE_SELECTION,
                        playerId));
    }

    private static <T> AttemptResult.Failure<T> failure(
            FailureCategory category, P4E1SourceFailure failure) {
        return new AttemptResult.Failure<>(category, failure);
    }

    @FunctionalInterface
    interface SourceReader<T> {
        SourceReadResult<T> read(
                SourceInput input,
                long stableCompressedSize,
                P4E1AuditBudget.FileScope fileScope) throws IOException;
    }

    static final class SourceInput {
        private final FileChannel channel;

        private SourceInput(FileChannel channel) {
            this.channel = Objects.requireNonNull(channel, "channel");
        }

        FileChannel channel() {
            return channel;
        }
    }

    sealed interface SourceReadResult<T> {
        record Ready<T>(T value) implements SourceReadResult<T> {
            public Ready {
                Objects.requireNonNull(value, "value");
            }
        }

        record Failure<T>(
                FailureCategory category,
                P4E1SourceFailure failure) implements SourceReadResult<T> {
            public Failure {
                Objects.requireNonNull(category, "category");
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface SelectionResult<T> {
        record Ready<T>(UUID playerId, SourceKind source, T value)
                implements SelectionResult<T> {
            public Ready {
                Objects.requireNonNull(playerId, "playerId");
                Objects.requireNonNull(source, "source");
                Objects.requireNonNull(value, "value");
            }
        }

        record Zero<T>(UUID playerId) implements SelectionResult<T> {
            public Zero {
                Objects.requireNonNull(playerId, "playerId");
            }
        }

        record Failure<T>(P4E1SourceFailure failure) implements SelectionResult<T> {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    enum SourceKind {
        PRIMARY,
        OLD
    }

    enum FailureCategory {
        PLATFORM_READ_FAILURE_PROVEN,
        STRICT_ONLY_REJECTION,
        POST_NBT_SEMANTIC_FAILURE,
        FILESYSTEM_OR_RACE_FAILURE
    }

    interface Observer {
        Observer NONE = new Observer() {
        };

        default void afterFirstAbsentCheck(SourceKind source) {
        }

        default void afterInitialMetadata(SourceKind source) {
        }

        default void afterPostOpenChecks(SourceKind source) {
        }

        default void afterRead(SourceKind source) {
        }

        default void afterFinalChecks(SourceKind source) {
        }
    }

    private sealed interface AttemptResult<T> {
        record Ready<T>(T value) implements AttemptResult<T> {
            public Ready {
                Objects.requireNonNull(value, "value");
            }
        }

        record Failure<T>(FailureCategory category, P4E1SourceFailure failure)
                implements AttemptResult<T> {
            public Failure {
                Objects.requireNonNull(category, "category");
                Objects.requireNonNull(failure, "failure");
            }
        }

        record Absent<T>() implements AttemptResult<T> {
        }
    }

    private sealed interface MetadataResult {
        record Ready(P4E1FileMetadata metadata) implements MetadataResult {
            public Ready {
                Objects.requireNonNull(metadata, "metadata");
            }
        }

        record Failure(boolean race) implements MetadataResult {
        }
    }
}
