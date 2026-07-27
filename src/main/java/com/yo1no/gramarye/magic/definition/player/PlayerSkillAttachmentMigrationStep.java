package com.yo1no.gramarye.magic.definition.player;

import net.minecraft.nbt.CompoundTag;

/** One adjacent Attachment-outer migration over an opaque-Draft shell. */
@FunctionalInterface
interface PlayerSkillAttachmentMigrationStep {
    CompoundTag migrate(CompoundTag tokenizedOuter);
}
