package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.EnumSet;
import java.util.Objects;

/** Exact closed-provider inventory checked at global ordering checkpoint five. */
final class P4E1SourceInventory {
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
        return new Result.Ready(new Witness(coverage));
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

        private Witness(EnumSet<P4E1RootSourceFamily> coverage) {
            this.coverage = EnumSet.copyOf(coverage);
        }

        boolean coversExactlyV0() {
            requireActive();
            return coverage.equals(EnumSet.allOf(P4E1RootSourceFamily.class));
        }

        void discard() {
            requireActive();
            coverage.clear();
            coverage = null;
        }

        private void requireActive() {
            if (coverage == null) {
                throw new IllegalStateException("P4E1_SOURCE_INVENTORY_WITNESS_DISCARDED");
            }
        }
    }
}
