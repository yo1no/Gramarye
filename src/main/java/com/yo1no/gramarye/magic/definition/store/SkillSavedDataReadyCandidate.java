package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.definition.migration.PipelineFactReport;
import java.util.Objects;

/** Immutable, non-installed P4-B1 load candidate for a fully rebuilt Store/carrier pair. */
final class SkillSavedDataReadyCandidate {
    private final SkillDefinitionStore store;
    private final SkillSavedDataInnerCarrier carrier;
    private final PipelineFactReport facts;
    private final boolean rewriteRequired;

    /**
     * Creates a candidate after the B1 bridge has rebuilt {@code carrier} from {@code store}.
     * Construction does not repeat Store encoding merely to re-prove that internal provenance.
     */
    private SkillSavedDataReadyCandidate(
            SkillDefinitionStore store,
            SkillSavedDataInnerCarrier carrier,
            PipelineFactReport facts,
            boolean rewriteRequired) {
        this.store = Objects.requireNonNull(store, "store");
        this.carrier = Objects.requireNonNull(carrier, "carrier");
        this.facts = Objects.requireNonNull(facts, "facts");
        this.rewriteRequired = rewriteRequired;
    }

    /**
     * Creates the transient pair only after the B1 bridge has received A3 rebuild success.
     * Phase-local API gates keep that bridge as the sole production caller.
     */
    static SkillSavedDataReadyCandidate afterCarrierRebuild(
            SkillDefinitionStore store,
            SkillSavedDataInnerCarrier carrier,
            PipelineFactReport facts,
            boolean rewriteRequired) {
        return new SkillSavedDataReadyCandidate(store, carrier, facts, rewriteRequired);
    }

    SkillDefinitionStore store() {
        return store;
    }

    SkillSavedDataInnerCarrier carrier() {
        return carrier;
    }

    PipelineFactReport facts() {
        return facts;
    }

    boolean rewriteRequired() {
        return rewriteRequired;
    }

    @Override
    public String toString() {
        return "SkillSavedDataReadyCandidate[historyCount="
                + carrier.storeCarrier().historyCount()
                + ", revisionCount=" + carrier.storeCarrier().revisionCount()
                + ", carrierByteCount=" + carrier.encodedByteCount()
                + ", factCount=" + facts.facts().size()
                + ", factsTruncated=" + facts.truncated()
                + ", rewriteRequired=" + rewriteRequired + "]";
    }
}
