package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Unique bounded production consumer for the compressed primary skill SavedData file. */
final class SkillSavedDataPrimaryIngress {
    private static final String DATA_DIRECTORY = "data";
    private static final String PRIMARY_FILE_NAME =
            SkillDefinitionStoreService.SAVED_DATA_NAME + ".dat";
    private static final Set<OpenOption> READ_NOFOLLOW = Set.of(
            StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);

    private SkillSavedDataPrimaryIngress() {
    }

    static Path resolvePrimaryPath(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return server.getWorldPath(LevelResource.ROOT)
                .resolve(DATA_DIRECTORY)
                .resolve(PRIMARY_FILE_NAME);
    }

    static SkillSavedDataPrimaryLoadResult load(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        return load(
                resolvePrimaryPath(server),
                Optional.of(server.registryAccess()),
                PrimaryIngressObserver.NONE);
    }

    static SkillSavedDataPrimaryLoadResult load(
            Path primary,
            Optional<HolderLookup.Provider> provider) {
        return load(primary, provider, PrimaryIngressObserver.NONE);
    }

    static SkillSavedDataPrimaryLoadResult load(
            Path primary,
            Optional<HolderLookup.Provider> provider,
            PrimaryIngressObserver observer) {
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(observer, "observer");

        PrimaryFileMetadata baseline;
        try {
            baseline = readMetadata(primary);
        } catch (NoSuchFileException absent) {
            observer.afterFirstAbsentCheck();
            try {
                readMetadata(primary);
                return race(
                        SkillSavedDataPrimaryFailure.PrimaryFileRaceKind
                                .APPEARED_AFTER_ABSENT_CHECK);
            } catch (NoSuchFileException confirmedAbsent) {
                return SkillSavedDataPrimaryLoadResult.Absent.INSTANCE;
            } catch (IOException exception) {
                return unreadable(
                        SkillSavedDataPrimaryFailure.PrimaryIngressStage.ABSENT_RECHECK);
            }
        } catch (IOException exception) {
            return unreadable(
                    SkillSavedDataPrimaryFailure.PrimaryIngressStage.INITIAL_ATTRIBUTES);
        }

        var initialClassification = classifyPresent(baseline);
        if (initialClassification != null) {
            return initialClassification;
        }
        if (baseline.size() > MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES) {
            return failure(new SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded(
                    (long) MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES + 1,
                    MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES));
        }
        observer.afterInitialMetadata();

        FileChannel channel;
        try {
            channel = FileChannel.open(primary, READ_NOFOLLOW);
        } catch (NoSuchFileException exception) {
            return race(SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED);
        } catch (IOException exception) {
            return unreadable(SkillSavedDataPrimaryFailure.PrimaryIngressStage.OPEN_CHANNEL);
        }

        SkillSavedDataPrimaryLoadResult result;
        try (channel) {
            PrimaryFileMetadata postOpen;
            long postOpenChannelSize;
            try {
                postOpen = readMetadata(primary);
                postOpenChannelSize = channel.size();
            } catch (NoSuchFileException exception) {
                return race(SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED);
            } catch (IOException exception) {
                return unreadable(
                        SkillSavedDataPrimaryFailure.PrimaryIngressStage
                                .POST_OPEN_ATTRIBUTES);
            }
            var postOpenRace = compareMetadata(
                    baseline, postOpen, postOpenChannelSize);
            if (postOpenRace != null) {
                return race(postOpenRace);
            }

            observer.afterPostOpenChecks();
            var strict = StrictSingleMemberGzipInput.load(
                    channel,
                    baseline.size(),
                    MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES,
                    provider);
            result = switch (strict) {
                case StrictSingleMemberGzipResult.Ready ready ->
                        new SkillSavedDataPrimaryLoadResult.Ready(ready.candidate());
                case StrictSingleMemberGzipResult.Failure failed ->
                        failure(failed.failure());
            };
            observer.afterParse();

            PrimaryFileMetadata finalMetadata;
            long finalChannelSize;
            try {
                finalMetadata = readMetadata(primary);
                finalChannelSize = channel.size();
            } catch (NoSuchFileException exception) {
                return race(SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED);
            } catch (IOException exception) {
                return unreadable(
                        SkillSavedDataPrimaryFailure.PrimaryIngressStage.FINAL_ATTRIBUTES);
            }
            var finalRace = compareMetadata(
                    baseline, finalMetadata, finalChannelSize);
            if (finalRace != null) {
                return race(finalRace);
            }
        } catch (IOException exception) {
            return unreadable(SkillSavedDataPrimaryFailure.PrimaryIngressStage.CLOSE_CHANNEL);
        }
        return result;
    }

    private static PrimaryFileMetadata readMetadata(Path primary) throws IOException {
        return PrimaryFileMetadata.capture(java.nio.file.Files.readAttributes(
                primary,
                BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS));
    }

    static SkillSavedDataPrimaryLoadResult classifyPresent(
            PrimaryFileMetadata metadata) {
        if (metadata.symbolicLink()) {
            return failure(new SkillSavedDataPrimaryFailure.UnsupportedPrimaryFileType(
                    SkillSavedDataPrimaryFailure.PrimaryFileKind.SYMBOLIC_LINK));
        }
        if (!metadata.regularFile()) {
            return failure(new SkillSavedDataPrimaryFailure.UnsupportedPrimaryFileType(
                    metadataKind(metadata)));
        }
        return metadata.fileKey() == null
                ? failure(
                        SkillSavedDataPrimaryFailure.PrimaryFileIdentityUnavailable.INSTANCE)
                : null;
    }

    private static SkillSavedDataPrimaryFailure.PrimaryFileKind metadataKind(
            PrimaryFileMetadata metadata) {
        return metadata.regularFile()
                ? SkillSavedDataPrimaryFailure.PrimaryFileKind.OTHER
                : metadata.directory()
                        ? SkillSavedDataPrimaryFailure.PrimaryFileKind.DIRECTORY
                        : SkillSavedDataPrimaryFailure.PrimaryFileKind.OTHER;
    }

    private static SkillSavedDataPrimaryFailure.PrimaryFileRaceKind compareMetadata(
            PrimaryFileMetadata baseline,
            PrimaryFileMetadata observed,
            long channelSize) {
        if (channelSize > baseline.size() || observed.size() > baseline.size()) {
            return SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.GREW_DURING_READ;
        }
        if (channelSize < baseline.size() || observed.size() < baseline.size()) {
            return SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.SHRANK_DURING_READ;
        }
        return baseline.sameIdentityAndShape(observed)
                ? null
                : SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED;
    }

    private static SkillSavedDataPrimaryLoadResult.Failure unreadable(
            SkillSavedDataPrimaryFailure.PrimaryIngressStage stage) {
        return failure(new SkillSavedDataPrimaryFailure.OuterSavedDataUnreadable(stage));
    }

    private static SkillSavedDataPrimaryLoadResult.Failure race(
            SkillSavedDataPrimaryFailure.PrimaryFileRaceKind kind) {
        return failure(new SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected(kind));
    }

    private static SkillSavedDataPrimaryLoadResult.Failure failure(
            SkillSavedDataPrimaryFailure failure) {
        return new SkillSavedDataPrimaryLoadResult.Failure(failure);
    }

    interface PrimaryIngressObserver {
        PrimaryIngressObserver NONE = new PrimaryIngressObserver() {
        };

        default void afterFirstAbsentCheck() {
        }

        default void afterInitialMetadata() {
        }

        default void afterPostOpenChecks() {
        }

        default void afterParse() {
        }
    }
}
