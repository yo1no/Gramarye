package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import com.yo1no.gramarye.magic.definition.document.DraftActionSlot;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagVisitor;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentAdmissionTest {
    private static final HolderLookup.Provider PROVIDER =
            HolderLookup.Provider.create(Stream.empty());
    private final PlayerSkillAttachmentAdmission admission =
            new PlayerSkillAttachmentAdmission();
    private final PlayerSkillAttachmentSerializer serializer =
            new PlayerSkillAttachmentSerializer();

    @Test
    void acceptedReadyUsesTheFullPersistencePathWithoutMutatingInput() throws IOException {
        var input = emptyReadyTag();
        var before = input.copy();
        var admitted = assertInstanceOf(
                PlayerSkillAttachmentAdmission.Admitted.class,
                admission.admit(input, AttachmentTagSize.measure(input), Optional.empty()));
        var serialized = assertInstanceOf(
                PlayerSkillAttachmentReady.class,
                serializer.read(null, input, null));

        assertEquals(before, input);
        assertEquals(serialized.drafts(), admitted.ready().drafts());
        assertEquals(serialized.latestStates(), admitted.ready().latestStates());
        assertEquals(serialized.equipped(), admitted.ready().equipped());
        assertEquals(serialized.editor(), admitted.ready().editor());
        assertEquals(serialized.carrier().copyTag(), admitted.ready().carrier().copyTag());
        assertEquals(
                ((AttachmentTagSizeResult.WithinLimit) AttachmentTagSize.measure(input))
                        .exactByteCount(),
                admitted.exactEncodedByteCount());
    }

    @Test
    void canonicalNonemptyMixedFamilyReadyIsSerializerEquivalentAndFullyHydrated()
            throws IOException {
        var route = new SkillId(new UUID(0L, 45L));
        var json = new JsonObject();
        json.addProperty("preserved_json", "yes");
        var nbt = new CompoundTag();
        nbt.putInt("preserved_nbt", 7);
        var draft = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                route,
                Optional.empty(),
                List.of(
                        new DraftNode(
                                DraftTriggerSlot.missing(),
                                DraftActionSlot.missing(),
                                AppearanceOverrideDocument.none()),
                        new DraftNode(
                                DraftTriggerSlot.present(envelope(
                                        "trigger",
                                        new Dynamic<>(
                                                RegistryOps.create(
                                                        JsonOps.COMPRESSED, PROVIDER),
                                                json))),
                                DraftActionSlot.present(envelope(
                                        "action",
                                        new Dynamic<>(
                                                RegistryOps.create(
                                                        NbtOps.INSTANCE, PROVIDER),
                                                nbt))),
                                AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
        var encoded = assertInstanceOf(
                SkillDraftPersistenceFacade.Encoded.class,
                SkillDraftPersistenceFacade.encodeCurrent(draft)).draft();
        var input = emptyReadyTag();
        ((ListTag) input.get(PlayerSkillAttachmentSchema.DRAFTS))
                .add(draftEntry(route, encoded.draftEncoding(), encoded.copyBytes()));

        var admitted = assertInstanceOf(
                PlayerSkillAttachmentReady.class,
                assertEquivalent(input, Optional.of(PROVIDER)));

        assertEquals(draft, admitted.drafts().get(0).draft());
    }

    @Test
    void malformedDraftAndDuplicateRoutesAreSerializerEquivalentRejections()
            throws IOException {
        var malformed = emptyReadyTag();
        ((ListTag) malformed.get(PlayerSkillAttachmentSchema.DRAFTS)).add(
                draftEntry(
                        new SkillId(new UUID(0L, 7L)),
                        SkillDraftPersistenceFacade.EncodedSkillDraft.CURRENT_ENCODING,
                        new byte[] {1, 2, 3}));
        assertEquivalent(malformed, Optional.empty());

        var duplicate = emptyReadyTag();
        var latest = new CompoundTag();
        latest.put(
                PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(
                        new SkillId(new UUID(0L, 8L))));
        latest.putInt(PlayerSkillAttachmentSchema.MUTATION_GENERATION, 0);
        ((ListTag) duplicate.get(PlayerSkillAttachmentSchema.LATEST_STATES))
                .add(latest.copy());
        ((ListTag) duplicate.get(PlayerSkillAttachmentSchema.LATEST_STATES))
                .add(latest.copy());
        assertEquivalent(duplicate, Optional.empty());
    }

    @Test
    void exactAttachmentByteBoundaryRejectsWithoutAuditCopyAndSerializerPreserves()
            throws IOException {
        var input = new ByteArrayTag(new byte[16_777_211]);
        var measured = assertInstanceOf(
                AttachmentTagSizeResult.WithinLimit.class,
                AttachmentTagSize.measure(input));
        assertEquals(AttachmentTagSize.maximum(), measured.exactByteCount());

        var pure = assertInstanceOf(
                PlayerSkillAttachmentAdmission.Rejected.class,
                admission.admit(input, measured, Optional.empty()));
        var serialized = assertInstanceOf(
                PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, input, null));

        assertEquals(AttachmentTagSize.maximum(), pure.exactEncodedByteCount());
        assertEquals(pure.failure(), serialized.failure());
        assertEquals(input, serialized.copyRaw());
    }

    @Test
    void auditRejectionNeverCopiesAndSerializerCopiesExactlyOnce() {
        var input = new CopyCountingTag();
        var measured = new AttachmentTagSizeResult.WithinLimit(2);
        var rejected = assertInstanceOf(
                PlayerSkillAttachmentAdmission.Rejected.class,
                admission.admit(input, measured, Optional.empty()));

        assertEquals(0, input.copyCount);
        assertEquals(
                PlayerSkillAttachmentFailure.Code.ATTACHMENT_ENVELOPE_MALFORMED,
                rejected.failure().code());

        assertInstanceOf(
                PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, input, null));
        assertEquals(1, input.copyCount);
    }

    @Test
    void oversizeAndExactMarkerClassificationsNeverCopyRaw() throws IOException {
        var input = new CopyCountingTag();
        assertEquals(
                PlayerSkillAttachmentAdmission.Oversize.INSTANCE,
                admission.admit(
                        input,
                        new AttachmentTagSizeResult.Exceeded(
                                AttachmentTagSize.observedAtLeast(),
                                AttachmentTagSize.maximum()),
                        Optional.empty()));
        assertEquals(0, input.copyCount);

        var marker = PlayerSkillAttachmentMarker.freshTag();
        assertEquals(
                PlayerSkillAttachmentAdmission.Oversize.INSTANCE,
                admission.admit(
                        marker,
                        new AttachmentTagSizeResult.WithinLimit(128),
                        Optional.empty()));
        assertInstanceOf(
                PlayerSkillAttachmentOversizeMarker.class,
                assertEquivalent(marker, Optional.empty()));
    }

    @Test
    void serializerAndPureCoreHaveExactClassificationForMalformedFamilies()
            throws IOException {
        var malformedOuter = new CompoundTag();
        var future = emptyReadyTag();
        future.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 1);
        var negativeGeneration = emptyReadyTag();
        var latest = new CompoundTag();
        latest.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(new SkillId(new UUID(0, 7))));
        latest.putInt(PlayerSkillAttachmentSchema.MUTATION_GENERATION, -1);
        ((ListTag) negativeGeneration.get(PlayerSkillAttachmentSchema.LATEST_STATES))
                .add(latest);
        var nearMarker = PlayerSkillAttachmentMarker.freshTag();
        nearMarker.putInt("extra", 1);

        for (var input : List.<Tag>of(
                ByteTag.valueOf((byte) 1),
                malformedOuter,
                future,
                negativeGeneration,
                nearMarker)) {
            var pure = assertInstanceOf(
                    PlayerSkillAttachmentAdmission.Rejected.class,
                    admission.admit(input, AttachmentTagSize.measure(input), Optional.empty()));
            var preserved = assertInstanceOf(
                    PlayerSkillAttachmentPreservedRaw.class,
                    serializer.read(null, input, null));
            assertEquals(preserved.failure(), pure.failure());
        }
    }

    @Test
    void trustedPremeasurementMustStillUseTheFixedBoundaryCoordinate() {
        var input = ByteTag.valueOf((byte) 1);
        assertThrows(
                IllegalStateException.class,
                () -> admission.admit(
                        input,
                        new AttachmentTagSizeResult.Exceeded(8, 7),
                        Optional.empty()));
        assertThrows(
                IllegalStateException.class,
                () -> admission.admit(
                        input,
                        new AttachmentTagSizeResult.WithinLimit(
                                AttachmentTagSize.maximum() + 1),
                        Optional.empty()));
    }

    private static CompoundTag emptyReadyTag() {
        var root = new CompoundTag();
        root.putInt(PlayerSkillAttachmentSchema.ATTACHMENT_SCHEMA_VERSION, 0);
        root.put(PlayerSkillAttachmentSchema.DRAFTS, new ListTag());
        root.put(PlayerSkillAttachmentSchema.LATEST_STATES, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS, new ListTag());
        root.put(PlayerSkillAttachmentSchema.EDITOR, new CompoundTag());
        return root;
    }

    private PlayerSkillAttachmentState assertEquivalent(
            Tag input, Optional<HolderLookup.Provider> provider) throws IOException {
        var before = input.copy();
        var measured = AttachmentTagSize.measure(input);
        var pure = admission.admit(input, measured, provider);
        var state = serializer.read(null, input, provider.orElse(null));

        assertEquals(before, input);
        switch (pure) {
            case PlayerSkillAttachmentAdmission.Admitted admitted -> {
                var ready = assertInstanceOf(PlayerSkillAttachmentReady.class, state);
                assertEquals(admitted.ready().drafts(), ready.drafts());
                assertEquals(admitted.ready().latestStates(), ready.latestStates());
                assertEquals(admitted.ready().equipped(), ready.equipped());
                assertEquals(admitted.ready().editor(), ready.editor());
                assertEquals(admitted.ready().carrier().copyTag(), ready.carrier().copyTag());
                assertEquals(
                        ((AttachmentTagSizeResult.WithinLimit) measured).exactByteCount(),
                        admitted.exactEncodedByteCount());
            }
            case PlayerSkillAttachmentAdmission.Rejected rejected -> {
                var preserved = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class, state);
                assertEquals(rejected.failure(), preserved.failure());
                assertEquals(
                        rejected.exactEncodedByteCount(),
                        preserved.exactEncodedByteCount());
                assertEquals(input, preserved.copyRaw());
            }
            case PlayerSkillAttachmentAdmission.Oversize ignored ->
                    assertInstanceOf(PlayerSkillAttachmentOversizeMarker.class, state);
        }
        return state;
    }

    private static CompoundTag draftEntry(
            SkillId route, String encoding, byte[] bytes) {
        var entry = new CompoundTag();
        entry.put(
                PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(route));
        entry.putString(PlayerSkillAttachmentSchema.DRAFT_ENCODING, encoding);
        entry.putByteArray(PlayerSkillAttachmentSchema.DRAFT_BYTES, bytes);
        return entry;
    }

    private static DefinitionEnvelope envelope(String path, Dynamic<?> payload) {
        return new DefinitionEnvelope(
                ResourceLocation.fromNamespaceAndPath("test", path), 0, payload);
    }

    private static final class CopyCountingTag implements Tag {
        private int copyCount;

        @Override
        public void write(DataOutput output) throws IOException {
            output.writeByte(1);
        }

        @Override
        public byte getId() {
            return TAG_BYTE;
        }

        @Override
        public TagType<?> getType() {
            return ByteTag.TYPE;
        }

        @Override
        public Tag copy() {
            copyCount++;
            return ByteTag.valueOf((byte) 1);
        }

        @Override
        public int sizeInBytes() {
            return 1;
        }

        @Override
        public void accept(TagVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamTagVisitor.ValueResult accept(StreamTagVisitor visitor) {
            throw new UnsupportedOperationException();
        }
    }
}
