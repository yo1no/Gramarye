package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;

/** One non-installing observation of the player-skill Attachment map entry. */
sealed interface ObservedPlayerSkillAttachment {
    enum Missing implements ObservedPlayerSkillAttachment {
        INSTANCE
    }

    record Ready(PlayerSkillAttachmentReady state)
            implements ObservedPlayerSkillAttachment {
        public Ready {
            Objects.requireNonNull(state, "state");
        }
    }

    record Quarantined(PlayerSkillAttachmentService.UnavailableReason reason)
            implements ObservedPlayerSkillAttachment {
        public Quarantined {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
