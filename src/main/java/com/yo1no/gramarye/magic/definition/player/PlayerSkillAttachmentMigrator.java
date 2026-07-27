package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

/** Applies outer migrations while keeping every Draft byte payload opaque and location-bound. */
final class PlayerSkillAttachmentMigrator {
    private final PlayerSkillAttachmentMigrationPlan plan;

    PlayerSkillAttachmentMigrator(PlayerSkillAttachmentMigrationPlan plan) {
        this.plan = Objects.requireNonNull(plan, "plan");
    }

    PlayerSkillAttachmentMigrationResult migrate(CompoundTag input) {
        Objects.requireNonNull(input, "input");
        if (!(input.get(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION) instanceof IntTag versionTag)) {
            return rejected(PlayerSkillAttachmentMigrationFailure.Code.ENVELOPE_MALFORMED, -1, "");
        }
        var sourceVersion = versionTag.getAsInt();
        if (sourceVersion < 0 || sourceVersion > plan.currentVersion()) {
            return rejected(PlayerSkillAttachmentMigrationFailure.Code.SCHEMA_UNSUPPORTED, sourceVersion, "");
        }

        var tokenized = tokenize(input, sourceVersion);
        if (tokenized instanceof PlayerSkillAttachmentMigrationResult.Rejected rejected) {
            return rejected;
        }
        var current = (PlayerSkillAttachmentMigrationResult.Migrated) tokenized;
        var tree = current.tokenizedCurrentOuter();
        for (var version = sourceVersion; version < plan.currentVersion(); version++) {
            var step = plan.stepFrom(version);
            if (step == null) {
                return rejected(
                        PlayerSkillAttachmentMigrationFailure.Code.MISSING_MIGRATION_EDGE, version, "");
            }
            try {
                tree = Objects.requireNonNull(step.migrate(tree.copy()), "migration output");
            } catch (RuntimeException exception) {
                return rejected(
                        PlayerSkillAttachmentMigrationFailure.Code.STEP_FAILED,
                        version,
                        exception.getClass().getName());
            }
            if (!(tree.get(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION)
                            instanceof IntTag migratedVersion)
                    || migratedVersion.getAsInt() != version + 1) {
                return rejected(
                        PlayerSkillAttachmentMigrationFailure.Code.PARTIAL_MIGRATION,
                        version,
                        "");
            }
        }
        if (!(tree.get(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION)
                        instanceof IntTag currentVersion)
                || currentVersion.getAsInt() != plan.currentVersion()) {
            return rejected(
                    PlayerSkillAttachmentMigrationFailure.Code.PARTIAL_MIGRATION,
                    sourceVersion,
                    "");
        }
        return validateTokens(tree, current.draftTokens(), sourceVersion);
    }

    private static PlayerSkillAttachmentMigrationResult tokenize(CompoundTag input, int sourceVersion) {
        if (!(input.get(PlayerSkillAttachmentSchema.DRAFTS) instanceof ListTag drafts)
                || drafts.size() > MagicSafetyCeilings.MAX_PLAYER_DRAFTS) {
            return rejected(PlayerSkillAttachmentMigrationFailure.Code.ENVELOPE_MALFORMED, sourceVersion, "");
        }
        var tree = new CompoundTag();
        for (var key : input.getAllKeys()) {
            if (!PlayerSkillAttachmentSchema.DRAFTS.equals(key)) {
                tree.put(key, Objects.requireNonNull(input.get(key), "outer field").copy());
            }
        }
        var treeDrafts = new ListTag();
        tree.put(PlayerSkillAttachmentSchema.DRAFTS, treeDrafts);
        var tokens = new ArrayList<PlayerSkillAttachmentMigrationResult.OpaqueDraftToken>(drafts.size());
        for (var index = 0; index < drafts.size(); index++) {
            if (!(drafts.get(index) instanceof CompoundTag sourceEntry)
                    || !(sourceEntry.get(PlayerSkillAttachmentSchema.SKILL_ID) instanceof net.minecraft.nbt.IntArrayTag route)
                    || !(sourceEntry.get(PlayerSkillAttachmentSchema.DRAFT_ENCODING) instanceof StringTag encoding)
                    || !(sourceEntry.get(PlayerSkillAttachmentSchema.DRAFT_BYTES) instanceof ByteArrayTag bytes)) {
                return rejected(PlayerSkillAttachmentMigrationFailure.Code.ENVELOPE_MALFORMED, sourceVersion, "");
            }
            var treeEntry = new CompoundTag();
            for (var key : sourceEntry.getAllKeys()) {
                if (!PlayerSkillAttachmentSchema.DRAFT_BYTES.equals(key)) {
                    treeEntry.put(key, Objects.requireNonNull(sourceEntry.get(key), "Draft field").copy());
                }
            }
            tokens.add(new PlayerSkillAttachmentMigrationResult.OpaqueDraftToken(
                    index, index, route, encoding.getAsString(), bytes));
            treeEntry.putString(
                    PlayerSkillAttachmentSchema.DRAFT_BYTES,
                    PlayerSkillAttachmentSchema.TOKEN_PREFIX + index);
            treeDrafts.add(treeEntry);
        }
        return new PlayerSkillAttachmentMigrationResult.Migrated(tree, tokens);
    }

    private static PlayerSkillAttachmentMigrationResult validateTokens(
            CompoundTag tree,
            java.util.List<PlayerSkillAttachmentMigrationResult.OpaqueDraftToken> tokens,
            int sourceVersion) {
        if (!(tree.get(PlayerSkillAttachmentSchema.DRAFTS) instanceof ListTag drafts)
                || drafts.size() != tokens.size()) {
            return rejected(
                    PlayerSkillAttachmentMigrationFailure.Code.OPAQUE_TOKEN_INVARIANT_VIOLATION,
                    sourceVersion,
                    "");
        }
        var observed = new HashSet<Integer>();
        for (var index = 0; index < drafts.size(); index++) {
            var token = tokens.get(index);
            if (!(drafts.get(index) instanceof CompoundTag entry)
                    || token.id() != index
                    || token.draftIndex() != index
                    || !Objects.equals(
                            entry.get(PlayerSkillAttachmentSchema.SKILL_ID), token.copyRouteSnapshot())
                    || !(entry.get(PlayerSkillAttachmentSchema.DRAFT_ENCODING) instanceof StringTag encoding)
                    || !encoding.getAsString().equals(token.draftEncoding())
                    || !(entry.get(PlayerSkillAttachmentSchema.DRAFT_BYTES) instanceof StringTag sentinel)
                    || !sentinel.getAsString().equals(PlayerSkillAttachmentSchema.TOKEN_PREFIX + token.id())
                    || !observed.add(token.id())) {
                return rejected(
                        PlayerSkillAttachmentMigrationFailure.Code.OPAQUE_TOKEN_INVARIANT_VIOLATION,
                        sourceVersion,
                        "");
            }
        }
        return new PlayerSkillAttachmentMigrationResult.Migrated(tree, tokens);
    }

    private static PlayerSkillAttachmentMigrationResult.Rejected rejected(
            PlayerSkillAttachmentMigrationFailure.Code code, int version, String exceptionClass) {
        return new PlayerSkillAttachmentMigrationResult.Rejected(
                new PlayerSkillAttachmentMigrationFailure(code, version, exceptionClass));
    }
}
