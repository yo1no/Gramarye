package com.yo1no.gramarye.magic.definition.player;

import java.util.Objects;

/** Complete result of rebuilding one current immutable player Attachment Ready state. */
sealed interface PlayerSkillAttachmentBuildResult
        permits PlayerSkillAttachmentBuildResult.Built,
                PlayerSkillAttachmentBuildResult.Rejected {
    record Built(PlayerSkillAttachmentReady ready) implements PlayerSkillAttachmentBuildResult {
        public Built {
            Objects.requireNonNull(ready, "ready");
        }
    }

    record Rejected(PlayerSkillAttachmentFailure failure)
            implements PlayerSkillAttachmentBuildResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }
}
