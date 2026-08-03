package com.yo1no.gramarye.magic.definition.player;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.store.P4D3StoreJournalFixture;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;

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
    private static final int R2Q_READY_ADMISSIONS = 1_024;
    private static final int R2Q_LATEST_ROOTS = 2_049;
    private static final int R2Q_EQUIPPED_ROOTS = 59_391;
    private static final int R2Q_PLAYER_ROOTS = 61_440;

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

    /**
     * Installs one mixed-family, RegistryOps-backed submission Draft only after a complete
     * production P4-C write/read admission round trip. This is the reduced R2Q-A D2 smoke seam;
     * it does not read or write playerdata.
     */
    public static void installReAdmittedSubmissionDraft(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        player.setData(PlayerSkillAttachments.type(), reAdmittedSubmissionReady(player));
    }

    /**
     * Executes the exact R2Q admission multiplicity without reading or writing playerdata: one
     * mixed RegistryOps/NBT Draft followed by 1,023 independent minimal canonical Ready tags.
     */
    public static AdmissionFacts admitR2QProfileReadyAttachments() {
        reAdmittedSubmissionReady(null);
        var empty = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                List.of(), List.of(), List.of(), PlayerSkillEditorState.empty());
        if (!(empty instanceof PlayerSkillAttachmentBuildResult.Built built)) {
            throw new AssertionError("research minimal Ready failed rebuild");
        }
        var canonical = PlayerSkillAttachmentSerializer.INSTANCE.write(
                built.ready(), RegistryAccess.EMPTY);
        for (var index = 0; index < 1_023; index++) {
            var admitted = PlayerSkillAttachmentSerializer.INSTANCE.read(
                    null, canonical.copy(), RegistryAccess.EMPTY);
            if (!(admitted instanceof PlayerSkillAttachmentReady ready)
                    || !ready.drafts().isEmpty()
                    || !ready.latestStates().isEmpty()
                    || !ready.equipped().isEmpty()) {
                throw new AssertionError("research minimal Ready admission changed");
            }
        }
        return new AdmissionFacts(1_024, 1, 1_023);
    }

    /**
     * Builds the R2Q positive player-root envelope from actual P4-C Ready states. Each owner input
     * is rebuilt, canonically written, and admitted through the production total serializer exactly
     * once. The retained result exposes only immutable root projections and bounded facts.
     */
    public static R2QReadyAttachments admitR2QProfileReadyAttachments(
            List<List<SkillReference>> latestByOwner) {
        Objects.requireNonNull(latestByOwner, "latestByOwner");
        if (latestByOwner.size() != R2Q_READY_ADMISSIONS) {
            throw new IllegalArgumentException("R2Q Ready owner count changed");
        }

        var retained = new ArrayList<PlayerSkillAttachmentReady>(R2Q_READY_ADMISSIONS);
        var projections = new ArrayList<OwnerRootProjection>(R2Q_READY_ADMISSIONS);
        var latestCount = 0;
        var equippedCount = 0;
        var mixedCount = 0;
        for (var ownerIndex = 0; ownerIndex < latestByOwner.size(); ownerIndex++) {
            var expectedLatest = List.copyOf(Objects.requireNonNull(
                    latestByOwner.get(ownerIndex), "latestByOwner entry"));
            var containsSubmissionDraft = expectedLatest.stream()
                    .anyMatch(reference -> reference.skillId().equals(
                            P4D3StoreJournalFixture.submissionSkillId()));
            var drafts = containsSubmissionDraft
                    ? List.of(submissionDraftEntry())
                    : List.<PlayerDraftEntry>of();
            if (containsSubmissionDraft) {
                mixedCount++;
            }

            var latest = expectedLatest.stream()
                    .map(reference -> new PlayerLatestState(
                            reference.skillId(), Optional.of(reference), 0))
                    .toList();
            var equippedForOwner = ownerIndex == R2Q_READY_ADMISSIONS - 1 ? 57 : 58;
            if (expectedLatest.size() < 2) {
                throw new IllegalArgumentException("R2Q owner has too few latest roots");
            }
            var equipped = new ArrayList<EquippedSkillReference>(equippedForOwner);
            for (var slot = 0; slot < equippedForOwner; slot++) {
                equipped.add(new EquippedSkillReference(
                        slot, expectedLatest.get(slot % expectedLatest.size())));
            }

            var ready = admitReady(null, drafts, latest, equipped);
            var admittedLatest = projectLatest(ready);
            var admittedEquipped = projectEquipped(ready);
            if (ready.drafts().size() != drafts.size()
                    || admittedLatest.size() != expectedLatest.size()
                    || !admittedLatest.containsAll(expectedLatest)
                    || !expectedLatest.containsAll(admittedLatest)
                    || admittedEquipped.size() != equippedForOwner) {
                throw new AssertionError("R2Q Ready state changed during P4-C admission");
            }
            retained.add(ready);
            projections.add(new OwnerRootProjection(
                    ownerIndex, admittedLatest, admittedEquipped));
            latestCount = Math.addExact(latestCount, admittedLatest.size());
            equippedCount = Math.addExact(equippedCount, admittedEquipped.size());
        }

        if (mixedCount != 1
                || latestCount != R2Q_LATEST_ROOTS
                || equippedCount != R2Q_EQUIPPED_ROOTS
                || Math.addExact(latestCount, equippedCount) != R2Q_PLAYER_ROOTS) {
            throw new AssertionError("R2Q admitted Ready projection shape changed");
        }
        return new R2QReadyAttachments(
                retained,
                projections,
                new AdmissionFacts(
                        R2Q_READY_ADMISSIONS,
                        mixedCount,
                        R2Q_READY_ADMISSIONS - mixedCount));
    }

    private static PlayerSkillAttachmentReady reAdmittedSubmissionReady(
            net.neoforged.neoforge.attachment.IAttachmentHolder holder) {
        var entry = submissionDraftEntry();
        var ready = admitReady(
                holder, List.of(entry), List.of(), List.of());
        if (ready.drafts().size() != 1
                || !ready.drafts().getFirst().skillId().equals(entry.skillId())) {
            throw new AssertionError("research mixed submission Ready failed P4-C admission");
        }
        return ready;
    }

    private static PlayerSkillAttachmentReady admitReady(
            net.neoforged.neoforge.attachment.IAttachmentHolder holder,
            List<PlayerDraftEntry> drafts,
            List<PlayerLatestState> latest,
            List<EquippedSkillReference> equipped) {
        var rebuilt = PlayerSkillAttachmentPersistenceBridge.rebuildReady(
                drafts, latest, equipped, PlayerSkillEditorState.empty());
        if (!(rebuilt instanceof PlayerSkillAttachmentBuildResult.Built built)) {
            throw new AssertionError("research R2Q Ready failed rebuild");
        }
        var canonical = PlayerSkillAttachmentSerializer.INSTANCE.write(
                built.ready(), RegistryAccess.EMPTY);
        var admitted = PlayerSkillAttachmentSerializer.INSTANCE.read(
                holder, canonical, RegistryAccess.EMPTY);
        if (!(admitted instanceof PlayerSkillAttachmentReady ready)) {
            throw new AssertionError("research R2Q Ready failed P4-C admission");
        }
        return ready;
    }

    private static PlayerDraftEntry submissionDraftEntry() {
        var draft = submissionDraft();
        var encoded = SkillDraftPersistenceFacade.encodeCurrent(draft);
        if (!(encoded instanceof SkillDraftPersistenceFacade.Encoded success)) {
            throw new AssertionError("research mixed submission Draft failed encode");
        }
        return new PlayerDraftEntry(draft.skillId(), draft, success.draft());
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

    private static SkillDraft submissionDraft() {
        var triggerPayload = new JsonObject();
        triggerPayload.addProperty("value", 41);
        var actionPayload = new CompoundTag();
        actionPayload.putInt("value", 42);
        var trigger = new DefinitionEnvelope(
                P4D3PlayerProbe.TRIGGER_ID,
                0,
                new Dynamic<>(RegistryOps.create(
                        JsonOps.INSTANCE, RegistryAccess.EMPTY), triggerPayload));
        var action = new DefinitionEnvelope(
                P4D3PlayerProbe.ACTION_ID,
                0,
                new Dynamic<>(NbtOps.INSTANCE, actionPayload));
        return new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                P4D3StoreJournalFixture.submissionSkillId(),
                Optional.empty(),
                List.of(new DraftNode(
                        new DraftTriggerSlot.Present(trigger),
                        new DraftActionSlot.Present(action),
                        AppearanceOverrideDocument.None.INSTANCE)),
                AppearanceDocument.Default.INSTANCE);
    }

    private static List<SkillReference> project(PlayerSkillAttachmentReady ready) {
        var roots = new ArrayList<SkillReference>();
        roots.addAll(projectLatest(ready));
        roots.addAll(projectEquipped(ready));
        return List.copyOf(roots);
    }

    private static List<SkillReference> projectLatest(PlayerSkillAttachmentReady ready) {
        return ready.latestStates().stream()
                .flatMap(state -> state.pointer().stream())
                .toList();
    }

    private static List<SkillReference> projectEquipped(PlayerSkillAttachmentReady ready) {
        return ready.equipped().stream()
                .map(EquippedSkillReference::reference)
                .toList();
    }

    private static SkillId skillId(int index) {
        return new SkillId(new UUID(READY_SKILL_MSB, Integer.toUnsignedLong(index)));
    }

    public enum Variant {
        READY,
        PRESERVED_RAW,
        OVERSIZE_MARKER
    }

    public record AdmissionFacts(
            int totalAdmissions, int mixedFamilyAdmissions, int minimalReadyAdmissions) {
        public AdmissionFacts {
            if (totalAdmissions != 1_024
                    || mixedFamilyAdmissions != 1
                    || minimalReadyAdmissions != 1_023
                    || totalAdmissions
                            != Math.addExact(mixedFamilyAdmissions, minimalReadyAdmissions)) {
                throw new IllegalArgumentException("invalid R2Q admission facts");
            }
        }
    }

    /** One admitted Ready state's bounded owner-indexed root projection. */
    public record OwnerRootProjection(
            int ownerIndex,
            List<SkillReference> latestRoots,
            List<SkillReference> equippedRoots) {
        public OwnerRootProjection {
            if (ownerIndex < 0 || ownerIndex >= R2Q_READY_ADMISSIONS) {
                throw new IllegalArgumentException("R2Q owner index is outside its fixture");
            }
            latestRoots = List.copyOf(Objects.requireNonNull(latestRoots, "latestRoots"));
            equippedRoots = List.copyOf(Objects.requireNonNull(
                    equippedRoots, "equippedRoots"));
        }
    }

    /** Opaque retention of the 1,024 actual Ready states admitted for the R2Q positive envelope. */
    public static final class R2QReadyAttachments {
        private final List<PlayerSkillAttachmentReady> retainedStates;
        private final List<OwnerRootProjection> ownerProjections;
        private final AdmissionFacts admissionFacts;

        private R2QReadyAttachments(
                List<PlayerSkillAttachmentReady> retainedStates,
                List<OwnerRootProjection> ownerProjections,
                AdmissionFacts admissionFacts) {
            this.retainedStates = List.copyOf(retainedStates);
            this.ownerProjections = List.copyOf(ownerProjections);
            this.admissionFacts = Objects.requireNonNull(admissionFacts, "admissionFacts");
            if (this.retainedStates.size() != admissionFacts.totalAdmissions()
                    || this.ownerProjections.size() != admissionFacts.totalAdmissions()) {
                throw new IllegalArgumentException("R2Q retained Ready count changed");
            }
        }

        public AdmissionFacts admissionFacts() {
            return admissionFacts;
        }

        public List<OwnerRootProjection> ownerProjections() {
            return ownerProjections;
        }

        public List<SkillReference> latestRoots() {
            return ownerProjections.stream()
                    .flatMap(projection -> projection.latestRoots().stream())
                    .toList();
        }

        public List<SkillReference> equippedRoots() {
            return ownerProjections.stream()
                    .flatMap(projection -> projection.equippedRoots().stream())
                    .toList();
        }

        public void retainAtPeak() {
            Reference.reachabilityFence(retainedStates);
            Reference.reachabilityFence(ownerProjections);
        }
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
