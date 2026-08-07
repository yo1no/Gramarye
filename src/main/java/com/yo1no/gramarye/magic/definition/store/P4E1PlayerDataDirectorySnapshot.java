package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Bounded, race-verifiable snapshot of the canonical playerdata source directory. */
final class P4E1PlayerDataDirectorySnapshot {
    private static final String PRIMARY_SUFFIX = ".dat";
    private static final String OLD_SUFFIX = ".dat_old";

    private final Path directory;
    private final P4E1FileMetadata directoryMetadata;
    private final Map<String, EntryWitness> entries;
    private final List<RouteRecord> records;
    private final P4E1FileSystemAccess fileSystem;

    private P4E1PlayerDataDirectorySnapshot(
            Path directory,
            P4E1FileMetadata directoryMetadata,
            Map<String, EntryWitness> entries,
            List<RouteRecord> records,
            P4E1FileSystemAccess fileSystem) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.directoryMetadata = Objects.requireNonNull(directoryMetadata, "directoryMetadata");
        this.entries = Map.copyOf(entries);
        this.records = List.copyOf(records);
        this.fileSystem = Objects.requireNonNull(fileSystem, "fileSystem");
    }

    static Path resolveDirectory(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
    }

    static CaptureResult capture(Path directory, P4E1AuditBudget budget) {
        return capture(directory, budget, P4E1FileSystemAccess.SYSTEM, Observer.NONE);
    }

    static CaptureResult capture(
            Path directory,
            P4E1AuditBudget budget,
            P4E1FileSystemAccess fileSystem,
            Observer observer) {
        Objects.requireNonNull(directory, "directory");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(fileSystem, "fileSystem");
        Objects.requireNonNull(observer, "observer");

        final P4E1FileMetadata directoryMetadata;
        try {
            directoryMetadata = P4E1FileMetadata.capture(fileSystem.readAttributes(directory));
        } catch (IOException exception) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_UNREADABLE);
        }
        if (directoryMetadata.symbolicLink() || !directoryMetadata.directory()) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_TYPE_UNSUPPORTED);
        }
        if (directoryMetadata.fileKey() == null) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_IDENTITY_UNAVAILABLE);
        }

        var entries = new HashMap<String, EntryWitness>();
        var routeBuilders = new TreeMap<UUID, RouteBuilder>();
        try (var stream = fileSystem.openDirectory(directory)) {
            var ordinal = 0;
            for (var entry : stream) {
                ordinal++;
                var exceeded = budget.checkpointSingle(
                        P4E1AuditCounter.DIRECTORY_ENTRIES,
                        P4E1AuditStage.DIRECTORY_ENTRIES,
                        1L);
                if (exceeded.isPresent()) {
                    return new CaptureResult.Failure(
                            P4E1SourceFailure.capacity(exceeded.orElseThrow()));
                }
                observer.afterDirectoryEntryCounted(ordinal);

                var name = entry.getFileName().toString();
                var classification = classifyName(name);
                final P4E1FileMetadata metadata;
                try {
                    metadata = P4E1FileMetadata.capture(fileSystem.readAttributes(entry));
                } catch (NoSuchFileException exception) {
                    return failure(P4E1SourceFailure.Code.DIRECTORY_RACE_DETECTED);
                }
                if (entries.put(name, new EntryWitness(classification.kind(), metadata)) != null) {
                    return failure(P4E1SourceFailure.Code.DIRECTORY_RACE_DETECTED);
                }

                if (classification.kind() == FilenameKind.NONCANONICAL) {
                    var nameFailure = classification.playerId() == null
                            ? P4E1SourceFailure.simple(
                                    P4E1SourceFailure.Code.PLAYERDATA_NAME_NONCANONICAL,
                                    P4E1AuditStage.DIRECTORY_ENTRIES)
                            : P4E1SourceFailure.forRoute(
                                    P4E1SourceFailure.Code.PLAYERDATA_NAME_NONCANONICAL,
                                    P4E1AuditStage.DIRECTORY_ENTRIES,
                                    classification.playerId());
                    return new CaptureResult.Failure(nameFailure);
                }
                if (!classification.kind().relevant()) {
                    continue;
                }

                var playerId = Objects.requireNonNull(classification.playerId(), "playerId");
                var route = routeBuilders.get(playerId);
                if (route == null) {
                    route = new RouteBuilder(playerId);
                    routeBuilders.put(playerId, route);
                }
                route.observe(classification.kind());
            }
        } catch (NoSuchFileException exception) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_RACE_DETECTED);
        } catch (DirectoryIteratorException exception) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_UNREADABLE);
        } catch (IOException exception) {
            return failure(P4E1SourceFailure.Code.DIRECTORY_UNREADABLE);
        }

        observer.afterInitialEnumeration();
        var records = new ArrayList<RouteRecord>(routeBuilders.size());
        for (var route : routeBuilders.values()) {
            records.add(route.build(directory));
        }
        return new CaptureResult.Ready(new P4E1PlayerDataDirectorySnapshot(
                directory, directoryMetadata, entries, records, fileSystem));
    }

    RecordSelection selectRecords(
            Optional<UUID> excludedIntegratedOwner,
            P4E1AuditBudget budget) {
        Objects.requireNonNull(excludedIntegratedOwner, "excludedIntegratedOwner");
        Objects.requireNonNull(budget, "budget");
        var selected = new ArrayList<RouteRecord>(records.size());
        for (var record : records) {
            if (excludedIntegratedOwner.filter(record.playerId()::equals).isPresent()) {
                continue;
            }
            var exceeded = budget.checkpointSingle(
                    P4E1AuditCounter.RELEVANT_RECORDS,
                    P4E1AuditStage.RELEVANT_RECORDS,
                    1L);
            if (exceeded.isPresent()) {
                return new RecordSelection.Failure(
                        P4E1SourceFailure.capacity(exceeded.orElseThrow()));
            }
            selected.add(record);
        }
        return new RecordSelection.Ready(selected);
    }

    int entryCount() {
        return entries.size();
    }

    VerificationResult verifyUnchanged() {
        return verifyUnchanged(Observer.NONE);
    }

    VerificationResult verifyUnchanged(Observer observer) {
        Objects.requireNonNull(observer, "observer");
        observer.beforeFinalEnumeration();

        final P4E1FileMetadata finalDirectoryMetadata;
        try {
            finalDirectoryMetadata = P4E1FileMetadata.capture(
                    fileSystem.readAttributes(directory));
        } catch (IOException exception) {
            return raceVerification();
        }
        if (!directoryMetadata.sameIdentityAndShape(finalDirectoryMetadata)) {
            return raceVerification();
        }

        var remaining = new HashMap<>(entries);
        var count = 0;
        try (var stream = fileSystem.openDirectory(directory)) {
            for (var entry : stream) {
                count++;
                if (count > entries.size()) {
                    return raceVerification();
                }
                var name = entry.getFileName().toString();
                var expected = remaining.remove(name);
                if (expected == null) {
                    return raceVerification();
                }
                var actualMetadata = P4E1FileMetadata.capture(
                        fileSystem.readAttributes(entry));
                if (!expected.metadata().sameIdentityAndShape(actualMetadata)
                        || expected.kind() != classifyName(name).kind()) {
                    return raceVerification();
                }
            }
        } catch (DirectoryIteratorException | IOException exception) {
            return raceVerification();
        }
        return remaining.isEmpty()
                ? VerificationResult.Unchanged.INSTANCE
                : raceVerification();
    }

    private static CaptureResult.Failure failure(P4E1SourceFailure.Code code) {
        return new CaptureResult.Failure(P4E1SourceFailure.simple(
                code, P4E1AuditStage.DIRECTORY_ENTRIES));
    }

    private static VerificationResult.Failure raceVerification() {
        return new VerificationResult.Failure(P4E1SourceFailure.simple(
                P4E1SourceFailure.Code.DIRECTORY_RACE_DETECTED,
                P4E1AuditStage.DIRECTORY_ENTRIES));
    }

    private static FilenameClassification classifyName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.startsWith(".") || name.contains("_corrupted_")) {
            return FilenameClassification.IGNORED;
        }

        var lower = name.toLowerCase(Locale.ROOT);
        var suffix = lower.endsWith(OLD_SUFFIX)
                ? OLD_SUFFIX
                : lower.endsWith(PRIMARY_SUFFIX) ? PRIMARY_SUFFIX : null;
        if (suffix == null) {
            return FilenameClassification.IGNORED;
        }
        var base = name.substring(0, name.length() - suffix.length());
        var exactSuffix = name.endsWith(suffix);
        UUID parsed = null;
        try {
            parsed = UUID.fromString(base);
        } catch (IllegalArgumentException ignored) {
            // UUID-shaped malformed active names are classified below.
        }
        var uuidShaped = isUuidShaped(base);
        if (parsed == null && !uuidShaped) {
            return FilenameClassification.IGNORED;
        }
        if (parsed == null || !exactSuffix || !parsed.toString().equals(base)) {
            return new FilenameClassification(FilenameKind.NONCANONICAL, parsed);
        }
        return new FilenameClassification(
                suffix.equals(OLD_SUFFIX) ? FilenameKind.OLD : FilenameKind.PRIMARY,
                parsed);
    }

    private static boolean isUuidShaped(String value) {
        return value.length() == 36
                && value.charAt(8) == '-'
                && value.charAt(13) == '-'
                && value.charAt(18) == '-'
                && value.charAt(23) == '-';
    }

    sealed interface CaptureResult {
        record Ready(P4E1PlayerDataDirectorySnapshot snapshot) implements CaptureResult {
            public Ready {
                Objects.requireNonNull(snapshot, "snapshot");
            }
        }

        record Failure(P4E1SourceFailure failure) implements CaptureResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface VerificationResult {
        enum Unchanged implements VerificationResult {
            INSTANCE
        }

        record Failure(P4E1SourceFailure failure) implements VerificationResult {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface RecordSelection {
        record Ready(List<RouteRecord> records) implements RecordSelection {
            public Ready {
                records = List.copyOf(records);
            }
        }

        record Failure(P4E1SourceFailure failure) implements RecordSelection {
            public Failure {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    static final class RouteRecord {
        private final UUID playerId;
        private final Path primary;
        private final Path old;

        RouteRecord(UUID playerId, Path primary, Path old) {
            this.playerId = Objects.requireNonNull(playerId, "playerId");
            this.primary = Objects.requireNonNull(primary, "primary");
            this.old = Objects.requireNonNull(old, "old");
        }

        UUID playerId() {
            return playerId;
        }

        Path primary() {
            return primary;
        }

        Path old() {
            return old;
        }
    }

    interface Observer {
        Observer NONE = new Observer() {
        };

        default void afterDirectoryEntryCounted(int ordinal) {
        }

        default void afterInitialEnumeration() {
        }

        default void beforeFinalEnumeration() {
        }
    }

    private record EntryWitness(FilenameKind kind, P4E1FileMetadata metadata) {
        private EntryWitness {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(metadata, "metadata");
        }
    }

    private record FilenameClassification(FilenameKind kind, UUID playerId) {
        private static final FilenameClassification IGNORED =
                new FilenameClassification(FilenameKind.IGNORED, null);

        private FilenameClassification {
            Objects.requireNonNull(kind, "kind");
        }
    }

    private enum FilenameKind {
        IGNORED,
        PRIMARY,
        OLD,
        NONCANONICAL;

        private boolean relevant() {
            return this == PRIMARY || this == OLD;
        }
    }

    private static final class RouteBuilder {
        private final UUID playerId;

        private RouteBuilder(UUID playerId) {
            this.playerId = playerId;
        }

        private void observe(FilenameKind kind) {
            if (!kind.relevant()) {
                throw new IllegalArgumentException("route requires a canonical active name");
            }
        }

        private RouteRecord build(Path directory) {
            var route = playerId.toString();
            return new RouteRecord(
                    playerId,
                    directory.resolve(route + PRIMARY_SUFFIX),
                    directory.resolve(route + OLD_SUFFIX));
        }
    }
}
