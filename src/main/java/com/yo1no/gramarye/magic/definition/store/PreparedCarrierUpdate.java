package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.Objects;

/**
 * Complete immutable Store-only carrier replacement prepared before a future domain commit.
 *
 * <p>This value is applicable only while the live derived carrier is the same object as its base.
 * It is not a commit credential, does not prove that the Store accepted the proposal, and contains
 * no journal, validation report, or domain commit result. A typed Store failure must discard it;
 * only a matching {@link SkillStoreCommitResult.Committed} result may permit later composition to
 * publish its prospective carrier.</p>
 */
final class PreparedCarrierUpdate {
    private final EncodedSkillStoreCarrier baseCarrier;
    private final EncodedSkillStoreCarrier prospectiveCarrier;
    private final SkillReference proposedReference;
    private final CarrierUpdateKind kind;

    PreparedCarrierUpdate(
            EncodedSkillStoreCarrier baseCarrier,
            EncodedSkillStoreCarrier prospectiveCarrier,
            SkillReference proposedReference,
            CarrierUpdateKind kind) {
        this.baseCarrier = Objects.requireNonNull(baseCarrier, "baseCarrier");
        this.prospectiveCarrier = Objects.requireNonNull(
                prospectiveCarrier, "prospectiveCarrier");
        this.proposedReference = Objects.requireNonNull(
                proposedReference, "proposedReference");
        this.kind = Objects.requireNonNull(kind, "kind");
        if (baseCarrier == prospectiveCarrier) {
            throw new IllegalArgumentException(
                    "a prospective carrier update must replace its base object");
        }
        var prospectiveHistory = prospectiveCarrier
                .findHistory(proposedReference.skillId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "prospective carrier is missing the proposed history"));
        if (prospectiveHistory.findRevision(proposedReference.revision()).isEmpty()) {
            throw new IllegalArgumentException(
                    "prospective carrier is missing the proposed revision");
        }
    }

    boolean isFor(EncodedSkillStoreCarrier candidate) {
        return baseCarrier == Objects.requireNonNull(candidate, "candidate");
    }

    EncodedSkillStoreCarrier baseCarrier() {
        return baseCarrier;
    }

    EncodedSkillStoreCarrier prospectiveCarrier() {
        return prospectiveCarrier;
    }

    SkillReference proposedReference() {
        return proposedReference;
    }

    CarrierUpdateKind kind() {
        return kind;
    }

    @Override
    public String toString() {
        return "PreparedCarrierUpdate[kind=" + kind
                + ", proposedReference=" + proposedReference
                + ", prospectiveStoreByteCount=" + prospectiveCarrier.storeByteCount() + "]";
    }
}

enum CarrierUpdateKind {
    NEW,
    EXISTING
}
