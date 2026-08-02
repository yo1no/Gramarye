package com.yo1no.gramarye.magic.definition.player;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.Tag;

/**
 * Research-only, non-authoritative access to the already-reviewed P4-C fixture and serializer
 * seams. Nothing in this type is a P4-E production parser or a persistence authority.
 */
public final class P4E0ResearchAttachmentFixtures {
    public static final int READY_LATEST_COUNT = 256;
    public static final int READY_EQUIPPED_COUNT = 64;
    public static final int READY_PROJECTED_ROOT_COUNT = 320;
    public static final long PRESERVED_RAW_BYTES = 16_777_216L;
    public static final long OVERSIZE_INPUT_BYTES = 16_777_217L;
    public static final long OVERSIZE_MARKER_BYTES = 142L;

    private static final long READY_SKILL_MSB = 0x5034_4530_5231_0000L;

    private P4E0ResearchAttachmentFixtures() {
    }

    public static Fixture readyRootMax() {
        return readyRootMax(true);
    }

    /** Builds and fully re-admits the maximum-root Ready fixture through the production serializer. */
    public static Fixture readyRootMax(boolean includeExistingMixedDraft) {
        var latest = new ArrayList<PlayerLatestState>(READY_LATEST_COUNT);
        var references = new ArrayList<SkillReference>(READY_LATEST_COUNT);
        for (var index = 0; index < READY_LATEST_COUNT; index++) {
            var skillId = skillId(index);
            var reference = new SkillReference(skillId, new SkillRevision(0));
            references.add(reference);
            latest.add(new PlayerLatestState(skillId, Optional.of(reference), index));
        }

        var equipped = new ArrayList<EquippedSkillReference>(READY_EQUIPPED_COUNT);
        for (var slot = 0; slot < READY_EQUIPPED_COUNT; slot++) {
            // Deliberately preserves cross-category duplicates in the raw root projection.
            equipped.add(new EquippedSkillReference(slot, references.get(slot)));
        }

        var drafts = includeExistingMixedDraft
                ? List.of(existingMixedDraft())
                : List.<PlayerDraftEntry>of();
        var built = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                drafts, latest, equipped, PlayerSkillEditorState.empty());
        if (!(built instanceof PlayerSkillAttachmentBuildResult.Built success)) {
            throw new AssertionError("research Ready-root maximum was rejected");
        }
        var canonical = PlayerSkillAttachmentSerializer.INSTANCE.write(
                success.ready(), RegistryAccess.EMPTY);
        var fixture = admit(canonical, RegistryAccess.EMPTY);
        if (fixture.variant() != Variant.READY
                || fixture.latestCount() != READY_LATEST_COUNT
                || fixture.equippedCount() != READY_EQUIPPED_COUNT
                || fixture.projectedRoots().orElseThrow().size()
                        != READY_PROJECTED_ROOT_COUNT) {
            throw new AssertionError("research Ready-root maximum changed during admission");
        }
        return fixture;
    }

    /** Builds the exact P4-C in-bound malformed raw coordinate and exercises its deep copy. */
    public static Fixture preservedRawExact() {
        var input = new ByteArrayTag(P4C2FixtureBuilder.payload(
                P4C2FixtureBuilder.PRESERVED_PAYLOAD_BYTES));
        var fixture = admit(input, null);
        if (fixture.variant() != Variant.PRESERVED_RAW
                || fixture.inputWriteAnyTagBytes() != PRESERVED_RAW_BYTES
                || fixture.serializedWriteAnyTagBytes() != PRESERVED_RAW_BYTES
                || !input.equals(fixture.serializedTag())) {
            throw new AssertionError("research exact PreservedRaw fixture changed");
        }
        return fixture;
    }

    /** Builds the exact cap+1 input and proves canonical destructive-marker publication. */
    public static Fixture oversizeMarker() {
        var input = new ByteArrayTag(P4C2FixtureBuilder.payload(
                P4C2FixtureBuilder.OVERSIZE_PAYLOAD_BYTES));
        var fixture = admit(input, null);
        if (fixture.variant() != Variant.OVERSIZE_MARKER
                || fixture.inputWriteAnyTagBytes() != OVERSIZE_INPUT_BYTES
                || fixture.serializedWriteAnyTagBytes() != OVERSIZE_MARKER_BYTES
                || !PlayerSkillAttachmentMarker.isExact(fixture.serializedTag())) {
            throw new AssertionError("research exact OversizeMarker fixture changed");
        }
        return fixture;
    }

    /**
     * Runs one materialized Tag through the production total serializer and retains the resulting
     * state for heap research. A null provider intentionally matches the existing P4-C unit seam.
     */
    public static Fixture admit(Tag input, HolderLookup.Provider provider) {
        Objects.requireNonNull(input, "input");
        var inputCount = P4C2FixtureBuilder.exactCount(input);
        var state = PlayerSkillAttachmentSerializer.INSTANCE.read(null, input, provider);
        var serialized = PlayerSkillAttachmentSerializer.INSTANCE.write(state, provider);
        var serializedCount = P4C2FixtureBuilder.exactCount(serialized);

        return switch (state) {
            case PlayerSkillAttachmentReady ready -> new Fixture(
                    Variant.READY,
                    input,
                    state,
                    serialized,
                    inputCount,
                    serializedCount,
                    ready.drafts().size(),
                    ready.latestStates().size(),
                    ready.equipped().size(),
                    Optional.of(project(ready)));
            case PlayerSkillAttachmentPreservedRaw ignored -> new Fixture(
                    Variant.PRESERVED_RAW,
                    input,
                    state,
                    serialized,
                    inputCount,
                    serializedCount,
                    -1,
                    -1,
                    -1,
                    Optional.empty());
            case PlayerSkillAttachmentOversizeMarker ignored -> new Fixture(
                    Variant.OVERSIZE_MARKER,
                    input,
                    state,
                    serialized,
                    inputCount,
                    serializedCount,
                    -1,
                    -1,
                    -1,
                    Optional.empty());
        };
    }

    private static PlayerDraftEntry existingMixedDraft() {
        var draft = P4C2FixtureBuilder.readyDraft(1);
        var encoded = SkillDraftPersistenceFacade.encodeCurrent(draft);
        if (!(encoded instanceof SkillDraftPersistenceFacade.Encoded success)) {
            throw new AssertionError("existing mixed-family Draft failed production encode");
        }
        return new PlayerDraftEntry(draft.skillId(), draft, success.draft());
    }

    private static List<SkillReference> project(PlayerSkillAttachmentReady ready) {
        var roots = new ArrayList<SkillReference>(
                ready.latestStates().size() + ready.equipped().size());
        ready.latestStates().stream()
                .flatMap(state -> state.pointer().stream())
                .forEach(roots::add);
        ready.equipped().stream()
                .map(EquippedSkillReference::reference)
                .forEach(roots::add);
        return List.copyOf(roots);
    }

    private static SkillId skillId(int index) {
        return new SkillId(new UUID(READY_SKILL_MSB, Integer.toUnsignedLong(index)));
    }

    public enum Variant {
        READY,
        PRESERVED_RAW,
        OVERSIZE_MARKER
    }

    /** Opaque retained state plus bounded scalar and defensive-copy accessors. */
    public static final class Fixture {
        private final Variant variant;
        private final Tag input;
        private final PlayerSkillAttachmentState state;
        private final Tag serialized;
        private final long inputWriteAnyTagBytes;
        private final long serializedWriteAnyTagBytes;
        private final int draftCount;
        private final int latestCount;
        private final int equippedCount;
        private final Optional<List<SkillReference>> projectedRoots;

        private Fixture(
                Variant variant,
                Tag input,
                PlayerSkillAttachmentState state,
                Tag serialized,
                long inputWriteAnyTagBytes,
                long serializedWriteAnyTagBytes,
                int draftCount,
                int latestCount,
                int equippedCount,
                Optional<List<SkillReference>> projectedRoots) {
            this.variant = Objects.requireNonNull(variant, "variant");
            this.input = Objects.requireNonNull(input, "input");
            this.state = Objects.requireNonNull(state, "state");
            this.serialized = Objects.requireNonNull(serialized, "serialized");
            this.inputWriteAnyTagBytes = inputWriteAnyTagBytes;
            this.serializedWriteAnyTagBytes = serializedWriteAnyTagBytes;
            this.draftCount = draftCount;
            this.latestCount = latestCount;
            this.equippedCount = equippedCount;
            this.projectedRoots = Objects.requireNonNull(projectedRoots, "projectedRoots")
                    .map(List::copyOf);
        }

        public Variant variant() {
            return variant;
        }

        public Tag inputTag() {
            return input.copy();
        }

        public Tag serializedTag() {
            return serialized.copy();
        }

        public long inputWriteAnyTagBytes() {
            return inputWriteAnyTagBytes;
        }

        public long serializedWriteAnyTagBytes() {
            return serializedWriteAnyTagBytes;
        }

        public int draftCount() {
            return draftCount;
        }

        public int latestCount() {
            return latestCount;
        }

        public int equippedCount() {
            return equippedCount;
        }

        public Optional<List<SkillReference>> projectedRoots() {
            return projectedRoots.map(List::copyOf);
        }

        /** Keeps all materialized and admitted objects alive at a research sampling point. */
        public void retainAtPeak() {
            Reference.reachabilityFence(input);
            Reference.reachabilityFence(state);
            Reference.reachabilityFence(serialized);
            Reference.reachabilityFence(projectedRoots);
        }
    }
}
