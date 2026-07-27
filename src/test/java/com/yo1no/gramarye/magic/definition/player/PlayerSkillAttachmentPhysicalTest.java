package com.yo1no.gramarye.magic.definition.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.yo1no.gramarye.magic.api.id.SkillId;
import com.yo1no.gramarye.magic.api.id.SkillRevision;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillDraftPersistenceFacade;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import org.junit.jupiter.api.Test;

final class PlayerSkillAttachmentPhysicalTest {
    private final PlayerSkillAttachmentSerializer serializer = new PlayerSkillAttachmentSerializer();

    @Test
    void routeAndReferenceUseTheLockedExistingCodecShapes() {
        var id = new SkillId(UUID.fromString("12345678-1234-5678-9abc-def012345678"));
        var reference = new SkillReference(id, new SkillRevision(7));

        var route = PlayerSkillAttachmentCodecs.encodeRoute(id);
        var encodedReference = PlayerSkillAttachmentCodecs.encodeReference(reference);

        assertEquals(4, ((net.minecraft.nbt.IntArrayTag) route).size());
        assertEquals("12345678-1234-5678-9abc-def012345678",
                encodedReference.getString("skill_id"));
        assertEquals(7, ((IntTag) encodedReference.get("revision")).getAsInt());
        assertEquals(id, PlayerSkillAttachmentCodecs.decodeRoute(route).orElseThrow());
        assertEquals(reference,
                PlayerSkillAttachmentCodecs.decodeReference(encodedReference).orElseThrow());
    }

    @Test
    void explicitEmptyLatestAtMaximumRoundTripsAsIntTag() {
        var root = emptyReadyTag();
        var id = new SkillId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
        var entry = new CompoundTag();
        entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(id));
        entry.putInt(PlayerSkillAttachmentSchema.MUTATION_GENERATION, Integer.MAX_VALUE);
        ((ListTag) root.get(PlayerSkillAttachmentSchema.LATEST_STATES)).add(entry);

        var ready = assertInstanceOf(PlayerSkillAttachmentReady.class,
                serializer.read(null, root, null));
        var output = (CompoundTag) serializer.write(ready, null);
        var written = (CompoundTag) ((ListTag) output.get(
                PlayerSkillAttachmentSchema.LATEST_STATES)).get(0);

        assertEquals(Integer.MAX_VALUE,
                ((IntTag) written.get(PlayerSkillAttachmentSchema.MUTATION_GENERATION)).getAsInt());
        assertEquals(false, written.contains(PlayerSkillAttachmentSchema.POINTER));
    }

    @Test
    void negativeAndLongGenerationsAreMalformedRatherThanCoerced() {
        var negative = emptyReadyTag();
        addLatest(negative, IntTag.valueOf(-1));
        var widened = emptyReadyTag();
        addLatest(widened, LongTag.valueOf(1));

        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, negative, null));
        assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, widened, null));
    }

    @Test
    void duplicateLatestAndEquippedRoutesAreDetectedBeforeReady() {
        var duplicateLatest = emptyReadyTag();
        addLatest(duplicateLatest, IntTag.valueOf(0));
        addLatest(duplicateLatest, IntTag.valueOf(1));

        var duplicateEquipped = emptyReadyTag();
        var id = new SkillId(new UUID(0, 4));
        var reference = new SkillReference(id, new SkillRevision(2));
        var equipped = (ListTag) duplicateEquipped.get(PlayerSkillAttachmentSchema.EQUIPPED_SLOTS);
        equipped.add(equippedEntry(3, reference));
        equipped.add(equippedEntry(3, reference));

        var latestState = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, duplicateLatest, null));
        var equippedState = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, duplicateEquipped, null));
        assertEquals(PlayerSkillAttachmentFailure.Code.DUPLICATE_LATEST_ROUTE,
                latestState.failure().code());
        assertEquals(PlayerSkillAttachmentFailure.Code.DUPLICATE_EQUIPPED_SLOT,
                equippedState.failure().code());
    }

    @Test
    void staleEditorMetadataIsRetainedButHardInvalidIndexQuarantinesWholeInput() {
        var stale = emptyReadyTag();
        var editor = (CompoundTag) stale.get(PlayerSkillAttachmentSchema.EDITOR);
        editor.put(PlayerSkillAttachmentSchema.SELECTED_DRAFT,
                PlayerSkillAttachmentCodecs.encodeRoute(new SkillId(new UUID(0, 99))));
        editor.putInt(PlayerSkillAttachmentSchema.SELECTED_NODE_INDEX, 255);
        var ready = assertInstanceOf(PlayerSkillAttachmentReady.class,
                serializer.read(null, stale, null));
        assertEquals(255, ready.editor().selectedNodeIndex().orElseThrow());

        editor.putInt(PlayerSkillAttachmentSchema.SELECTED_NODE_INDEX, 256);
        var invalid = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, stale, null));
        assertEquals(PlayerSkillAttachmentFailure.Code.EDITOR_STATE_INVALID,
                invalid.failure().code());
    }

    @Test
    void validDraftRouteLoadsThroughFacadeAndDuplicateRouteLeavesNoPartialReady() {
        var route = new SkillId(new UUID(0, 45));
        var draft = new SkillDraft(
                0, route, Optional.empty(), List.of(), AppearanceDocument.defaultAppearance());
        var encoded = ((SkillDraftPersistenceFacade.Encoded)
                SkillDraftPersistenceFacade.encodeCurrent(draft)).draft();
        var root = emptyReadyTag();
        var drafts = (ListTag) root.get(PlayerSkillAttachmentSchema.DRAFTS);
        drafts.add(draftEntry(route, encoded));

        var ready = assertInstanceOf(PlayerSkillAttachmentReady.class,
                serializer.read(null, root, null));
        assertEquals(draft, ready.drafts().get(0).draft());

        drafts.add(draftEntry(route, encoded));
        var preserved = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, root, null));
        assertEquals(PlayerSkillAttachmentFailure.Code.DUPLICATE_DRAFT_ROUTE,
                preserved.failure().code());
        assertEquals(root, serializer.write(preserved, null));
    }

    @Test
    void uniqueNoncanonicalLatestOrderIsCanonicalizedAndPointerMismatchRejects() {
        var high = new SkillId(new UUID(0, 50));
        var low = new SkillId(new UUID(0, 2));
        var root = emptyReadyTag();
        var latest = (ListTag) root.get(PlayerSkillAttachmentSchema.LATEST_STATES);
        latest.add(latestEntry(high, Optional.empty()));
        latest.add(latestEntry(low, Optional.empty()));

        var ready = assertInstanceOf(PlayerSkillAttachmentReady.class,
                serializer.read(null, root, null));
        var output = (CompoundTag) serializer.write(ready, null);
        var written = (ListTag) output.get(PlayerSkillAttachmentSchema.LATEST_STATES);
        assertEquals(low, PlayerSkillAttachmentCodecs.decodeRoute(
                ((CompoundTag) written.get(0)).get(PlayerSkillAttachmentSchema.SKILL_ID))
                .orElseThrow());

        var mismatch = emptyReadyTag();
        ((ListTag) mismatch.get(PlayerSkillAttachmentSchema.LATEST_STATES)).add(
                latestEntry(low, Optional.of(new SkillReference(high, new SkillRevision(0)))));
        var rejected = assertInstanceOf(PlayerSkillAttachmentPreservedRaw.class,
                serializer.read(null, mismatch, null));
        assertEquals(PlayerSkillAttachmentFailure.Code.LATEST_POINTER_ROUTE_MISMATCH,
                rejected.failure().code());
    }

    private static void addLatest(CompoundTag root, net.minecraft.nbt.Tag generation) {
        var entry = new CompoundTag();
        entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(new SkillId(new UUID(0, 1))));
        entry.put(PlayerSkillAttachmentSchema.MUTATION_GENERATION, generation);
        ((ListTag) root.get(PlayerSkillAttachmentSchema.LATEST_STATES)).add(entry);
    }

    private static CompoundTag equippedEntry(int slot, SkillReference reference) {
        var entry = new CompoundTag();
        entry.putInt(PlayerSkillAttachmentSchema.SLOT, slot);
        entry.put(PlayerSkillAttachmentSchema.REFERENCE,
                PlayerSkillAttachmentCodecs.encodeReference(reference));
        return entry;
    }

    private static CompoundTag latestEntry(
            SkillId route, Optional<SkillReference> pointer) {
        var entry = new CompoundTag();
        entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(route));
        entry.putInt(PlayerSkillAttachmentSchema.MUTATION_GENERATION, 0);
        pointer.ifPresent(value -> entry.put(PlayerSkillAttachmentSchema.POINTER,
                PlayerSkillAttachmentCodecs.encodeReference(value)));
        return entry;
    }

    private static CompoundTag draftEntry(
            SkillId route,
            SkillDraftPersistenceFacade.EncodedSkillDraft encoded) {
        var entry = new CompoundTag();
        entry.put(PlayerSkillAttachmentSchema.SKILL_ID,
                PlayerSkillAttachmentCodecs.encodeRoute(route));
        entry.putString(PlayerSkillAttachmentSchema.DRAFT_ENCODING,
                encoded.draftEncoding());
        entry.putByteArray(PlayerSkillAttachmentSchema.DRAFT_BYTES, encoded.copyBytes());
        return entry;
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
}
