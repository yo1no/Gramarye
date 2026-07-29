package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, canonical pending Attachment-update journal. */
final class PendingAttachmentJournal {
    private static final PendingAttachmentJournal EMPTY =
            new PendingAttachmentJournal(List.of());
    private static final Comparator<PendingAttachmentJournalEntry> CANONICAL_ORDER =
            Comparator.comparing(
                            (PendingAttachmentJournalEntry entry) -> entry.owner().value())
                    .thenComparing(entry -> entry.skillId().value())
                    .thenComparingInt(
                            PendingAttachmentJournalEntry::targetAttachmentGeneration);

    private final List<PendingAttachmentJournalEntry> entries;

    private PendingAttachmentJournal(List<PendingAttachmentJournalEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    static PendingAttachmentJournal empty() {
        return EMPTY;
    }

    static DomainAdmission admitPhysical(PendingAttachmentJournalPhysicalV0 physical) {
        Objects.requireNonNull(physical, "physical");
        if (physical.journalSchemaVersion()
                != PendingAttachmentJournalSchema.CURRENT_SCHEMA_VERSION) {
            return new DomainAdmission.Rejected(PendingAttachmentJournalFailure.at(
                    PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA,
                    PendingAttachmentJournalFailure.Stage.DOMAIN,
                    PendingAttachmentJournalFailure.Field.VERSION));
        }
        var input = new ArrayList<PendingAttachmentJournalEntry>(physical.entries().size());
        for (var entry : physical.entries()) {
            input.add(new PendingAttachmentJournalEntry(
                    entry.owner(),
                    entry.skillId(),
                    entry.expectedAttachmentGeneration(),
                    entry.targetAttachmentGeneration(),
                    entry.expectedPointer(),
                    entry.targetPointer()));
        }
        return admit(input);
    }

    private static DomainAdmission admit(List<PendingAttachmentJournalEntry> input) {
        if (input.size() > PendingAttachmentJournalSchema.MAX_ENTRIES) {
            return new DomainAdmission.Rejected(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENTRY_COUNT_EXCEEDED,
                    input.size(),
                    PendingAttachmentJournalSchema.MAX_ENTRIES));
        }

        var stableKeys = new HashSet<StableKey>();
        for (var index = 0; index < input.size(); index++) {
            var entry = Objects.requireNonNull(input.get(index), "entry");
            if (entry.expectedAttachmentGeneration() < 0
                    || entry.targetAttachmentGeneration() < 0) {
                return rejected(PendingAttachmentJournalFailure.Code.GENERATION_INVALID, index);
            }
            if (entry.expectedAttachmentGeneration() == Integer.MAX_VALUE) {
                return rejected(PendingAttachmentJournalFailure.Code.GENERATION_EXHAUSTED, index);
            }
            if (!PlayerSkillAttachmentService.isChangedGenerationSuccessor(
                    entry.expectedAttachmentGeneration(),
                    entry.targetAttachmentGeneration())) {
                return rejected(PendingAttachmentJournalFailure.Code.GENERATION_INVALID, index);
            }
            if (!referenceMatches(entry.expectedPointer(), entry.skillId())
                    || !entry.targetPointer().skillId().equals(entry.skillId())
                    || entry.expectedPointer().filter(entry.targetPointer()::equals).isPresent()) {
                return rejected(PendingAttachmentJournalFailure.Code.POINTER_ROUTE_MISMATCH, index);
            }
            if (!stableKeys.add(new StableKey(
                    entry.owner(), entry.skillId(), entry.targetAttachmentGeneration()))) {
                return rejected(PendingAttachmentJournalFailure.Code.DUPLICATE_STABLE_KEY, index);
            }
        }

        var canonical = new ArrayList<>(input);
        canonical.sort(CANONICAL_ORDER);
        for (var index = 1; index < canonical.size(); index++) {
            var previous = canonical.get(index - 1);
            var entry = canonical.get(index);
            if (!sameRoute(previous, entry)) {
                continue;
            }
            if (entry.expectedAttachmentGeneration()
                    != previous.targetAttachmentGeneration()) {
                return rejected(
                        PendingAttachmentJournalFailure.Code.BROKEN_GENERATION_CHAIN,
                        input.indexOf(entry));
            }
            if (!entry.expectedPointer().equals(Optional.of(previous.targetPointer()))) {
                return rejected(
                        PendingAttachmentJournalFailure.Code.BROKEN_POINTER_CHAIN,
                        input.indexOf(entry));
            }
        }
        var journal = canonical.isEmpty()
                ? EMPTY
                : new PendingAttachmentJournal(canonical);
        return new DomainAdmission.Admitted(journal, !input.equals(canonical));
    }

    List<PendingAttachmentJournalEntry> entries() {
        return entries;
    }

    int entryCount() {
        return entries.size();
    }

    List<SkillReference> targetReferences() {
        return entries.stream().map(PendingAttachmentJournalEntry::targetPointer).toList();
    }

    DomainMutation append(PendingAttachmentJournalEntry entry) {
        Objects.requireNonNull(entry, "entry");
        if (entries.size() == PendingAttachmentJournalSchema.MAX_ENTRIES) {
            return new DomainMutation.Rejected(PendingAttachmentJournalFailure.capacity(
                    PendingAttachmentJournalFailure.Code.ENTRY_COUNT_EXCEEDED,
                    (long) PendingAttachmentJournalSchema.MAX_ENTRIES + 1,
                    PendingAttachmentJournalSchema.MAX_ENTRIES));
        }
        var entryAdmission = admit(List.of(entry));
        if (entryAdmission instanceof DomainAdmission.Rejected rejected) {
            return new DomainMutation.Rejected(rejected.failure());
        }

        PendingAttachmentJournalEntry routeFinal = null;
        for (var current : entries) {
            if (sameRoute(current, entry)) {
                routeFinal = current;
            }
        }
        if (routeFinal != null
                && entry.expectedAttachmentGeneration()
                        != routeFinal.targetAttachmentGeneration()) {
            return new DomainMutation.Rejected(PendingAttachmentJournalFailure.entry(
                    PendingAttachmentJournalFailure.Code.BROKEN_GENERATION_CHAIN,
                    entries.size()));
        }
        if (routeFinal != null
                && !entry.expectedPointer().equals(Optional.of(routeFinal.targetPointer()))) {
            return new DomainMutation.Rejected(PendingAttachmentJournalFailure.entry(
                    PendingAttachmentJournalFailure.Code.BROKEN_POINTER_CHAIN,
                    entries.size()));
        }
        var prospective = new ArrayList<>(entries);
        prospective.add(entry);
        return switch (admit(prospective)) {
            case DomainAdmission.Admitted admitted ->
                    new DomainMutation.Updated(admitted.journal());
            case DomainAdmission.Rejected rejected ->
                    new DomainMutation.Rejected(rejected.failure());
        };
    }

    PrefixClear clearPrefix(
            SkillOwnerId owner,
            SkillId skillId,
            int persistedGeneration,
            SkillReference persistedPointer) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(skillId, "skillId");
        persistedPointer = Objects.requireNonNull(persistedPointer, "persistedPointer");
        var routeSeen = false;
        var matchingIndex = -1;
        for (var index = 0; index < entries.size(); index++) {
            var entry = entries.get(index);
            if (!entry.owner().equals(owner) || !entry.skillId().equals(skillId)) {
                continue;
            }
            routeSeen = true;
            if (entry.targetAttachmentGeneration() == persistedGeneration
                    && persistedPointer.equals(entry.targetPointer())) {
                matchingIndex = index;
                break;
            }
        }
        if (!routeSeen) {
            return PrefixClear.NoChain.INSTANCE;
        }
        if (matchingIndex < 0) {
            return PrefixClear.TargetMismatch.INSTANCE;
        }
        var remaining = new ArrayList<>(entries);
        var removed = 0;
        for (var index = matchingIndex; index >= 0; index--) {
            var entry = remaining.get(index);
            if (entry.owner().equals(owner) && entry.skillId().equals(skillId)) {
                remaining.remove(index);
                removed++;
            }
        }
        return new PrefixClear.Cleared(
                remaining.isEmpty() ? EMPTY : new PendingAttachmentJournal(remaining), removed);
    }

    private static boolean referenceMatches(Optional<SkillReference> reference, SkillId skillId) {
        return reference.isEmpty() || reference.orElseThrow().skillId().equals(skillId);
    }

    private static boolean sameRoute(
            PendingAttachmentJournalEntry left, PendingAttachmentJournalEntry right) {
        return left.owner().equals(right.owner()) && left.skillId().equals(right.skillId());
    }

    private static DomainAdmission.Rejected rejected(
            PendingAttachmentJournalFailure.Code code, int index) {
        return new DomainAdmission.Rejected(PendingAttachmentJournalFailure.entry(code, index));
    }

    @Override
    public String toString() {
        return "PendingAttachmentJournal[entryCount=" + entries.size() + ']';
    }

    sealed interface DomainAdmission
            permits DomainAdmission.Admitted, DomainAdmission.Rejected {
        record Admitted(PendingAttachmentJournal journal, boolean nonCanonicalOrder)
                implements DomainAdmission {
            public Admitted {
                Objects.requireNonNull(journal, "journal");
            }
        }

        record Rejected(PendingAttachmentJournalFailure failure) implements DomainAdmission {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface DomainMutation permits DomainMutation.Updated, DomainMutation.Rejected {
        record Updated(PendingAttachmentJournal journal) implements DomainMutation {
            public Updated {
                Objects.requireNonNull(journal, "journal");
            }
        }

        record Rejected(PendingAttachmentJournalFailure failure) implements DomainMutation {
            public Rejected {
                Objects.requireNonNull(failure, "failure");
            }
        }
    }

    sealed interface PrefixClear
            permits PrefixClear.NoChain, PrefixClear.TargetMismatch, PrefixClear.Cleared {
        enum NoChain implements PrefixClear {
            INSTANCE
        }

        enum TargetMismatch implements PrefixClear {
            INSTANCE
        }

        record Cleared(PendingAttachmentJournal journal, int entriesRemoved)
                implements PrefixClear {
            public Cleared {
                Objects.requireNonNull(journal, "journal");
                if (entriesRemoved <= 0) {
                    throw new IllegalArgumentException("entriesRemoved must be positive");
                }
            }
        }
    }

    private record StableKey(SkillOwnerId owner, SkillId skillId, int targetGeneration) {
    }
}

/** Immutable physical V0 entry preserving strict input-list order. */
record PendingAttachmentJournalEntryPhysicalV0(
        SkillOwnerId owner,
        SkillId skillId,
        int expectedAttachmentGeneration,
        int targetAttachmentGeneration,
        Optional<SkillReference> expectedPointer,
        SkillReference targetPointer) {
    PendingAttachmentJournalEntryPhysicalV0 {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(skillId, "skillId");
        expectedPointer = Objects.requireNonNull(expectedPointer, "expectedPointer");
        Objects.requireNonNull(targetPointer, "targetPointer");
    }
}

/** Immutable exact V0 physical journal decoded before domain admission. */
record PendingAttachmentJournalPhysicalV0(
        int journalSchemaVersion,
        List<PendingAttachmentJournalEntryPhysicalV0> entries) {
    PendingAttachmentJournalPhysicalV0 {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}

/** Immutable admitted pending transition. */
record PendingAttachmentJournalEntry(
        SkillOwnerId owner,
        SkillId skillId,
        int expectedAttachmentGeneration,
        int targetAttachmentGeneration,
        Optional<SkillReference> expectedPointer,
        SkillReference targetPointer) {
    PendingAttachmentJournalEntry {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(skillId, "skillId");
        expectedPointer = Objects.requireNonNull(expectedPointer, "expectedPointer");
        Objects.requireNonNull(targetPointer, "targetPointer");
    }
}
