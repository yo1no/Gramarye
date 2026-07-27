package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.yo1no.gramarye.magic.api.id.SkillId;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentMigratorTest {
    @Test
    void currentZeroStepStillTokenizesAndValidatesOpaqueDraftBytesWithoutMutatingInput() {
        var input = attachmentWithDraft(0, new byte[] {1, 2, 3});
        var original = input.copy();
        var migrator = new PlayerSkillAttachmentMigrator(
                PlayerSkillAttachmentMigrationPlans.production());

        var migrated = assertInstanceOf(
                PlayerSkillAttachmentMigrationResult.Migrated.class,
                migrator.migrate(input));
        var shellEntry = (CompoundTag) ((ListTag) migrated.tokenizedCurrentOuter()
                .get(PlayerSkillAttachmentSchema.DRAFTS)).get(0);

        assertInstanceOf(StringTag.class, shellEntry.get(PlayerSkillAttachmentSchema.DRAFT_BYTES));
        var captured = assertInstanceOf(
                com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade.Captured.class,
                migrated.draftTokens().get(0).capturePersisted());
        assertArrayEquals(new byte[] {1, 2, 3}, captured.draft().copyBytes());
        assertEquals(original, input);
    }

    @Test
    void injectedAdjacentStepOnlySeesTokenAndRuntimeFailureIsBounded() {
        PlayerSkillAttachmentMigrationStep step = shell -> {
            var entry = (CompoundTag) ((ListTag) shell.get(
                    PlayerSkillAttachmentSchema.DRAFTS)).get(0);
            assertInstanceOf(StringTag.class, entry.get(PlayerSkillAttachmentSchema.DRAFT_BYTES));
            throw new IllegalArgumentException("must-not-persist");
        };
        var migrator = new PlayerSkillAttachmentMigrator(
                new PlayerSkillAttachmentMigrationPlan(1, Map.of(0, step)));

        var rejected = assertInstanceOf(
                PlayerSkillAttachmentMigrationResult.Rejected.class,
                migrator.migrate(attachmentWithDraft(0, new byte[] {9})));

        assertEquals(PlayerSkillAttachmentMigrationFailure.Code.STEP_FAILED,
                rejected.failure().code());
        assertEquals(IllegalArgumentException.class.getName(),
                rejected.failure().exceptionClass());
        assertEquals(false, rejected.failure().exceptionClass().contains("must-not-persist"));
    }

    @Test
    void missingEdgeAndFutureSchemaRejectWhileErrorPassesThrough() {
        var missing = new PlayerSkillAttachmentMigrator(
                new PlayerSkillAttachmentMigrationPlan(1, Map.of()));
        var rejected = assertInstanceOf(
                PlayerSkillAttachmentMigrationResult.Rejected.class,
                missing.migrate(attachmentWithDraft(0, new byte[] {1})));
        assertEquals(PlayerSkillAttachmentMigrationFailure.Code.MISSING_MIGRATION_EDGE,
                rejected.failure().code());

        var production = new PlayerSkillAttachmentMigrator(
                PlayerSkillAttachmentMigrationPlans.production());
        var future = attachmentWithDraft(0, new byte[] {1});
        future.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 1);
        assertEquals(PlayerSkillAttachmentMigrationFailure.Code.SCHEMA_UNSUPPORTED,
                ((PlayerSkillAttachmentMigrationResult.Rejected) production.migrate(future))
                        .failure().code());

        var error = new PlayerSkillAttachmentMigrator(new PlayerSkillAttachmentMigrationPlan(
                1, Map.of(0, ignored -> { throw new AssertionError("passthrough"); })));
        assertThrows(AssertionError.class,
                () -> error.migrate(attachmentWithDraft(0, new byte[] {1})));
    }

    @Test
    void adjacentStepMustPublishExactlyItsNextSchemaVersion() {
        var wrong = new PlayerSkillAttachmentMigrator(new PlayerSkillAttachmentMigrationPlan(
                1, Map.of(0, shell -> shell)));
        var rejected = assertInstanceOf(
                PlayerSkillAttachmentMigrationResult.Rejected.class,
                wrong.migrate(attachmentWithDraft(0, new byte[] {1})));
        assertEquals(PlayerSkillAttachmentMigrationFailure.Code.PARTIAL_MIGRATION,
                rejected.failure().code());

        var correct = new PlayerSkillAttachmentMigrator(new PlayerSkillAttachmentMigrationPlan(
                1, Map.of(0, shell -> {
                    shell.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 1);
                    return shell;
                })));
        assertInstanceOf(
                PlayerSkillAttachmentMigrationResult.Migrated.class,
                correct.migrate(attachmentWithDraft(0, new byte[] {1})));
    }

    private static CompoundTag attachmentWithDraft(int version, byte[] bytes) {
        var route = new SkillId(new UUID(0, 3));
        var draft = new CompoundTag();
        draft.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(route));
        draft.putString(PlayerSkillAttachmentSchema.DRAFT_ENCODING,
                "family_tagged_subtrees_v0");
        draft.putByteArray(PlayerSkillAttachmentSchema.DRAFT_BYTES, bytes);
        var drafts = new ListTag();
        drafts.add(draft);
        var root = new CompoundTag();
        root.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, version);
        root.put(PlayerSkillAttachmentSchema.DRAFTS, drafts);
        root.put(PlayerSkillAttachmentSchema.LATEST_STATES, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EDITOR, new CompoundTag());
        return root;
    }
}
