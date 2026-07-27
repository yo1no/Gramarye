package com.yo1no.gramarye.magic.definition.player;

/** Internal total state returned by the custom Attachment serializer. */
sealed interface PlayerSkillAttachmentState
        permits PlayerSkillAttachmentReady, PlayerSkillAttachmentQuarantine {
}
