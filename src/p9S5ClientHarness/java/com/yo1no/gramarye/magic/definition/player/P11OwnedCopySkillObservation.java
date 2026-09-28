package com.yo1no.gramarye.magic.definition.player;

import net.minecraft.server.level.ServerPlayer;

/** Excluded non-installing classification of the native-read/copied current datum. */
public final class P11OwnedCopySkillObservation {
    private P11OwnedCopySkillObservation() {}

    public static String existingKind(ServerPlayer actor) {
        if (actor == null || actor.getServer() == null || !actor.getServer().isSameThread()) { return "UNOBSERVED"; }
        var state = actor.getExistingDataOrNull(PlayerSkillAttachments.type());
        if (state instanceof PlayerSkillAttachmentPreservedRaw) { return "PRESERVED_RAW"; }
        if (state instanceof PlayerSkillAttachmentOversizeMarker) { return "OVERSIZE_MARKER"; }
        return state == null ? "ABSENT" : "READY";
    }
}
