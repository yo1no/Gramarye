package com.yo1no.gramarye.magic.definition.player;

import java.util.Map;

/** Unique production provider for the Attachment outer axis. */
final class PlayerSkillAttachmentMigrationPlans {
    private static final PlayerSkillAttachmentMigrationPlan PRODUCTION =
            new PlayerSkillAttachmentMigrationPlan(Map.of());

    private PlayerSkillAttachmentMigrationPlans() {
    }

    static PlayerSkillAttachmentMigrationPlan production() {
        return PRODUCTION;
    }
}
