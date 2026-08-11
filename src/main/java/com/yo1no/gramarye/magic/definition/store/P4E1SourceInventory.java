package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.EnumSet;
import java.util.Objects;

/** Exact closed-provider inventory checked at global ordering checkpoint five. */
final class P4E1SourceInventory {
    private static final InventoryDefinition FIXED_V0 = InventoryDefinition.V0;

    private P4E1SourceInventory() {
    }

    static Result capture(
            PlayerSkillAttachmentService playerProvider,
            P4E1PendingJournalObservation.Ready journalProvider) {
        var coverage = EnumSet.noneOf(P4E1RootSourceFamily.class);
        for (var family : P4E1RootSourceFamily.values()) {
            var present = switch (family) {
                case PLAYER_SKILL_ATTACHMENT -> playerProvider != null;
                case PENDING_ATTACHMENT_JOURNAL -> journalProvider != null;
            };
            if (!present) {
                return new Result.Missing(family);
            }
            coverage.add(family);
        }
        var expected = EnumSet.allOf(P4E1RootSourceFamily.class);
        if (!coverage.equals(expected)) {
            throw new IllegalStateException("P4E1_SOURCE_INVENTORY_COVERAGE_MISMATCH");
        }
        return new Result.Ready(new Witness(
                coverage, playerProvider, journalProvider, FIXED_V0));
    }

    sealed interface Result {
        record Ready(Witness witness) implements Result {
            public Ready {
                Objects.requireNonNull(witness, "witness");
            }
        }

        record Missing(P4E1RootSourceFamily family) implements Result {
            public Missing {
                Objects.requireNonNull(family, "family");
            }
        }
    }

    static final class Witness {
        private EnumSet<P4E1RootSourceFamily> coverage;
        private PlayerSkillAttachmentService playerProviderIdentity;
        private P4E1PendingJournalObservation.Ready journalProviderIdentity;
        private InventoryDefinition definitionIdentity;

        private Witness(
                EnumSet<P4E1RootSourceFamily> coverage,
                PlayerSkillAttachmentService playerProviderIdentity,
                P4E1PendingJournalObservation.Ready journalProviderIdentity,
                InventoryDefinition definitionIdentity) {
            this.coverage = EnumSet.copyOf(coverage);
            this.playerProviderIdentity = Objects.requireNonNull(
                    playerProviderIdentity, "playerProviderIdentity");
            this.journalProviderIdentity = Objects.requireNonNull(
                    journalProviderIdentity, "journalProviderIdentity");
            this.definitionIdentity = Objects.requireNonNull(
                    definitionIdentity, "definitionIdentity");
        }

        boolean coversExactlyV0() {
            requireActive();
            return coverage.equals(EnumSet.allOf(P4E1RootSourceFamily.class));
        }

        boolean isCurrent(
                PlayerSkillAttachmentService playerProvider,
                P4E1PendingJournalObservation.Ready journalProvider) {
            requireActive();
            return definitionIdentity == FIXED_V0
                    && coverage.equals(EnumSet.allOf(P4E1RootSourceFamily.class))
                    && playerProviderIdentity
                            == Objects.requireNonNull(playerProvider, "playerProvider")
                    && journalProviderIdentity
                            == Objects.requireNonNull(journalProvider, "journalProvider");
        }

        void discard() {
            requireActive();
            coverage.clear();
            coverage = null;
            playerProviderIdentity = null;
            journalProviderIdentity = null;
            definitionIdentity = null;
        }

        private void requireActive() {
            if (coverage == null) {
                throw new IllegalStateException("P4E1_SOURCE_INVENTORY_WITNESS_DISCARDED");
            }
        }
    }

    private enum InventoryDefinition {
        V0
    }
}
