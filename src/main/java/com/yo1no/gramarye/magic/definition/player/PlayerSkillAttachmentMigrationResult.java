package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

sealed interface PlayerSkillAttachmentMigrationResult
        permits PlayerSkillAttachmentMigrationResult.Migrated,
                PlayerSkillAttachmentMigrationResult.Rejected {
    record Migrated(CompoundTag tokenizedCurrentOuter, List<OpaqueDraftToken> draftTokens)
            implements PlayerSkillAttachmentMigrationResult {
        public Migrated {
            tokenizedCurrentOuter = Objects.requireNonNull(tokenizedCurrentOuter, "tokenizedCurrentOuter");
            draftTokens = List.copyOf(Objects.requireNonNull(draftTokens, "draftTokens"));
        }
    }

    record Rejected(PlayerSkillAttachmentMigrationFailure failure)
            implements PlayerSkillAttachmentMigrationResult {
        public Rejected {
            Objects.requireNonNull(failure, "failure");
        }
    }

    /** A location-bound reference to bytes hidden from the migration-visible tree. */
    final class OpaqueDraftToken {
        private final int id;
        private final int draftIndex;
        private final Tag routeSnapshot;
        private final String draftEncoding;
        private final ByteArrayTag borrowedSourceBytes;

        OpaqueDraftToken(
                int id,
                int draftIndex,
                Tag routeSnapshot,
                String draftEncoding,
                ByteArrayTag borrowedSourceBytes) {
            if (id < 0 || draftIndex < 0) {
                throw new IllegalArgumentException("token identity and location must be non-negative");
            }
            this.id = id;
            this.draftIndex = draftIndex;
            this.routeSnapshot = Objects.requireNonNull(routeSnapshot, "routeSnapshot").copy();
            this.draftEncoding = Objects.requireNonNull(draftEncoding, "draftEncoding");
            this.borrowedSourceBytes = Objects.requireNonNull(borrowedSourceBytes, "borrowedSourceBytes");
        }

        int id() {
            return id;
        }

        int draftIndex() {
            return draftIndex;
        }

        String draftEncoding() {
            return draftEncoding;
        }

        Tag copyRouteSnapshot() {
            return routeSnapshot.copy();
        }

        SkillDraftPersistenceFacade.CaptureResult capturePersisted() {
            return SkillDraftPersistenceFacade.EncodedSkillDraft.capturePersisted(
                    draftEncoding,
                    borrowedSourceBytes.size(),
                    borrowedSourceBytes::getAsByteArray);
        }
    }
}
