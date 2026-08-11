package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentServiceTestSupport;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagType;
import net.minecraft.nbt.TagVisitor;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class P4E1RootAuditBridgeTest {
    private static final HolderLookup.Provider PROVIDER =
            HolderLookup.Provider.create(Stream.empty());

    @Test
    void diskAndIntegratedAdaptersImmediatelyConsumeCompleteObservations() {
        var diskService = service();
        var diskTag = emptyReadyTag();
        var disk = P4E1BoundPlayerSkillAttachmentAdmissionSource.admitDiskObservation(
                diskService,
                new P4E1PlayerDataNbtScanner.AttachmentObservation.Present(
                        diskTag, writeAnyTagWidth(diskTag)),
                PROVIDER);
        var diskAdmitted = assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditAdmitted.class, disk);
        assertEquals(0, diskService.rootCount(diskAdmitted));
        var zeroCallbacks = new ArrayList<Callback>();
        diskService.drainRootProjection(diskAdmitted, new RecordingSink(zeroCallbacks));
        assertTrue(zeroCallbacks.isEmpty());

        var integratedService = service();
        var integratedTag = emptyReadyTag();
        var integrated = P4E1BoundPlayerSkillAttachmentAdmissionSource
                .admitIntegratedObservation(
                        integratedService,
                        new P4E1IntegratedSnapshotTraversal.AttachmentObservation(
                                integratedTag, writeAnyTagWidth(integratedTag)),
                        PROVIDER);
        var integratedAdmitted = assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditAdmitted.class, integrated);
        assertEquals(0, integratedService.rootCount(integratedAdmitted));
        integratedService.discardRootProjection(integratedAdmitted);
    }

    @Test
    void sourceClaimIsSingleUseAcrossSuccessRejectionAndEveryProofFailure() {
        var owner = service();
        var other = service();
        var input = emptyReadyTag();
        var width = writeAnyTagWidth(input);

        var valid = source(owner, input, input, width, PROVIDER, PROVIDER);
        var admitted = assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditAdmitted.class,
                owner.admitForRootAudit(valid));
        owner.discardRootProjection(admitted);
        assertConsumed(owner, valid);

        var malformed = ByteTag.valueOf((byte) 1);
        var rejected = source(owner, malformed, malformed, 2L, PROVIDER, PROVIDER);
        assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditRejected.class,
                owner.admitForRootAudit(rejected));
        assertConsumed(owner, rejected);

        var wrongOwner = source(owner, input, input, width, PROVIDER, PROVIDER);
        assertMisuse("P4E1_ADMISSION_SOURCE_OWNER_MISMATCH",
                () -> other.admitForRootAudit(wrongOwner));
        assertConsumed(owner, wrongOwner);

        var differentTag = emptyReadyTag();
        var wrongInputProof = source(
                owner, input, differentTag, width, PROVIDER, PROVIDER);
        assertMisuse("P4E1_ADMISSION_INPUT_IDENTITY_MISMATCH",
                () -> owner.admitForRootAudit(wrongInputProof));
        assertConsumed(owner, wrongInputProof);

        var otherProvider = HolderLookup.Provider.create(Stream.empty());
        var wrongProviderProof = source(
                owner, input, input, width, PROVIDER, otherProvider);
        assertMisuse("P4E1_ADMISSION_PROVIDER_IDENTITY_MISMATCH",
                () -> owner.admitForRootAudit(wrongProviderProof));
        assertConsumed(owner, wrongProviderProof);

        var wrongInputBinding = source(
                owner, null, null, width, PROVIDER, PROVIDER);
        assertMisuse("P4E1_ADMISSION_INPUT_BINDING_INVALID",
                () -> owner.admitForRootAudit(wrongInputBinding));
        assertConsumed(owner, wrongInputBinding);

        var wrongProviderBinding = source(
                owner, input, input, width, null, null);
        assertMisuse("P4E1_ADMISSION_PROVIDER_BINDING_INVALID",
                () -> owner.admitForRootAudit(wrongProviderBinding));
        assertConsumed(owner, wrongProviderBinding);

        for (var invalidWidth : List.of(0L, -1L)) {
            var wrongSize = source(
                    owner, input, input, invalidWidth, PROVIDER, PROVIDER);
            assertMisuse("P4E1_ADMISSION_SIZE_PROOF_INVALID",
                    () -> owner.admitForRootAudit(wrongSize));
            assertConsumed(owner, wrongSize);
        }
    }

    @Test
    void sourceIsClearedBeforeSemanticRuntimeExceptionOrErrorEscapes() {
        var service = service();
        var runtimeTag = new ExplodingCompoundTag(
                new IllegalStateException("expected test runtime"));
        var runtime = source(service, runtimeTag, runtimeTag, 1L, PROVIDER, PROVIDER);
        assertThrows(IllegalStateException.class, () -> service.admitForRootAudit(runtime));
        assertConsumed(service, runtime);

        var errorTag = new ExplodingCompoundTag(new AssertionError("expected test error"));
        var error = source(service, errorTag, errorTag, 1L, PROVIDER, PROVIDER);
        assertThrows(AssertionError.class, () -> service.admitForRootAudit(error));
        assertConsumed(service, error);
    }

    @Test
    void bridgeMatchesSerializerClassificationWithoutCopyingRejectedRaw() {
        var canonical = emptyReadyTag();
        var marker = oversizeMarker();
        var nearMarker = oversizeMarker();
        nearMarker.putInt("extra", 1);
        var future = emptyReadyTag();
        future.putInt("attachment_schema_version", 1);
        var malformedDraft = emptyReadyTag();
        var draft = new CompoundTag();
        draft.put("skill_id", NbtUtils.createUUID(new UUID(0L, 17L)));
        draft.putString("draft_encoding", "unsupported_physical_encoding");
        draft.putByteArray("draft_bytes", new byte[] {1, 2, 3});
        ((ListTag) malformedDraft.get("drafts")).add(draft);

        assertEquivalent(canonical, PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.ADMITTED);
        assertEquivalent(marker, PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.OVERSIZE);
        assertEquivalent(nearMarker, PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.REJECTED);
        assertEquivalent(ByteTag.valueOf((byte) 1), PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.REJECTED);
        assertEquivalent(future, PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.REJECTED);
        assertEquivalent(malformedDraft, PlayerSkillAttachmentServiceTestSupport
                .SerializerClassification.REJECTED);

        var copyCounting = new CopyCountingTag();
        assertBridgeClassification(copyCounting, 2L,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED);
        assertEquals(0, copyCounting.copyCount);
        assertEquals(0, copyCounting.writeCount);
        assertEquals(
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED,
                PlayerSkillAttachmentServiceTestSupport.classifyWithRegisteredSerializer(
                        copyCounting, PROVIDER));
        assertEquals(1, copyCounting.copyCount);
        assertEquals(1, copyCounting.writeCount);
    }

    @Test
    void providerDependentMixedFamilyDraftUsesTheSameFullAdmissionCore() {
        var route = new SkillId(new UUID(0L, 45L));
        var json = new JsonObject();
        json.addProperty("preserved_json", "yes");
        var nbt = new CompoundTag();
        nbt.putInt("preserved_nbt", 7);
        var draft = new SkillDraft(
                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION,
                route,
                java.util.Optional.empty(),
                List.of(
                        new DraftNode(
                                DraftTriggerSlot.missing(),
                                DraftActionSlot.missing(),
                                AppearanceOverrideDocument.none()),
                        new DraftNode(
                                DraftTriggerSlot.present(new DefinitionEnvelope(
                                        ResourceLocation.fromNamespaceAndPath("test", "trigger"),
                                        0,
                                        new Dynamic<>(
                                                RegistryOps.create(JsonOps.COMPRESSED, PROVIDER),
                                                json))),
                                DraftActionSlot.present(new DefinitionEnvelope(
                                        ResourceLocation.fromNamespaceAndPath("test", "action"),
                                        0,
                                        new Dynamic<>(
                                                RegistryOps.create(NbtOps.INSTANCE, PROVIDER),
                                                nbt))),
                                AppearanceOverrideDocument.none())),
                AppearanceDocument.defaultAppearance());
        var encoded = assertInstanceOf(
                SkillDraftPersistenceFacade.Encoded.class,
                SkillDraftPersistenceFacade.encodeCurrent(draft)).draft();
        var tag = emptyReadyTag();
        var entry = new CompoundTag();
        entry.put("skill_id", NbtUtils.createUUID(route.value()));
        entry.putString("draft_encoding", encoded.draftEncoding());
        entry.putByteArray("draft_bytes", encoded.copyBytes());
        ((ListTag) tag.get("drafts")).add(entry);

        assertEquivalent(tag,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.ADMITTED);

        var logicalMigrationFailure = draftTag(
                route,
                encoded.draftEncoding(),
                mutateEncodedDraft(encoded.copyBytes(), root ->
                        root.putInt(
                                "draft_schema_version",
                                SkillDraft.CURRENT_DRAFT_SCHEMA_VERSION + 1)));
        assertEquivalent(
                logicalMigrationFailure,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED);

        var hydrateFailure = draftTag(
                route,
                encoded.draftEncoding(),
                mutateEncodedDraft(encoded.copyBytes(), root -> {
                    var nodes = assertInstanceOf(ListTag.class, root.get("nodes"));
                    var node = assertInstanceOf(CompoundTag.class, nodes.get(1));
                    var trigger = assertInstanceOf(CompoundTag.class, node.get("trigger"));
                    var definition = assertInstanceOf(
                            CompoundTag.class, trigger.get("definition"));
                    var payload = assertInstanceOf(
                            CompoundTag.class, definition.get("payload"));
                    payload.putByteArray("data", new byte[] {'{'});
                }));
        assertEquivalent(
                hydrateFailure,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED);
    }

    @Test
    void exactMaximumIsInBoundAndMaximumPlusOneSkipsSemanticAdmission() {
        var exact = new ByteArrayTag(new byte[
                MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES - 5]);
        assertEquals(
                MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES,
                writeAnyTagWidth(exact));
        assertBridgeClassification(
                exact,
                MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED);
        assertEquals(
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED,
                PlayerSkillAttachmentServiceTestSupport.classifyWithRegisteredSerializer(
                        exact, PROVIDER));

        var plusOne = new ByteArrayTag(new byte[
                MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES - 4]);
        assertEquals(
                (long) MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 1L,
                writeAnyTagWidth(plusOne));
        assertBridgeClassification(
                plusOne,
                (long) MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 1L,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.OVERSIZE);
        assertEquals(
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.OVERSIZE,
                PlayerSkillAttachmentServiceTestSupport.classifyWithRegisteredSerializer(
                        plusOne, PROVIDER));

        var exploding = new ExplodingCompoundTag(new AssertionError(
                "semantic admission must not inspect a proven oversize Tag"));
        assertBridgeClassification(
                exploding,
                (long) MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 1L,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.OVERSIZE);
        assertBridgeClassification(
                exploding,
                (long) MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 2L,
                PlayerSkillAttachmentServiceTestSupport.SerializerClassification.OVERSIZE);
    }

    @Test
    void projectionCountsThenDrainsCanonicalCategoriesAndPreservesDuplicates() {
        var low = reference(1L, 3);
        var high = reference(9L, 5);
        var tag = emptyReadyTag();
        addLatest(tag, high, true, 2);
        addLatest(tag, new SkillReference(
                new SkillId(new UUID(0L, 4L)), new SkillRevision(7)), false, 7);
        addLatest(tag, low, true, 1);
        addEquipped(tag, 63, high);
        addEquipped(tag, 1, low);
        addEquipped(tag, 0, low);

        var service = service();
        var admitted = assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditAdmitted.class,
                P4E1BoundPlayerSkillAttachmentAdmissionSource.admitIntegratedObservation(
                        service,
                        new P4E1IntegratedSnapshotTraversal.AttachmentObservation(
                                tag, writeAnyTagWidth(tag)),
                        PROVIDER));

        assertEquals(5, service.rootCount(admitted));
        assertEquals(5, service.rootCount(admitted));
        var callbacks = new ArrayList<Callback>();
        service.drainRootProjection(admitted, new RecordingSink(callbacks));
        assertEquals(List.of(
                new Callback("latest", -1, low),
                new Callback("latest", -1, high),
                new Callback("equipped", 0, low),
                new Callback("equipped", 1, low),
                new Callback("equipped", 63, high)), callbacks);
        assertProjectionConsumed(service, admitted);
    }

    @Test
    void reserveFailureDiscardsWithoutCallbacksOrPublishedRoots() {
        var tag = emptyReadyTag();
        addLatest(tag, reference(1L, 1), true, 1);
        var service = service();
        var admitted = admitted(service, tag);
        var callbacks = new ArrayList<Callback>();
        var published = new ArrayList<SkillReference>();

        var count = service.rootCount(admitted);
        assertEquals(1, count);
        var allReservationsSucceeded = false;
        if (allReservationsSucceeded) {
            service.drainRootProjection(admitted, new RecordingSink(callbacks));
        } else {
            service.discardRootProjection(admitted);
        }

        assertTrue(callbacks.isEmpty());
        assertTrue(published.isEmpty());
        assertProjectionConsumed(service, admitted);
    }

    @Test
    void latestOnlyAndEquippedOnlyEachUseTheirExactCallbackShape() {
        var latestReference = reference(3L, 4);
        var latestTag = emptyReadyTag();
        addLatest(latestTag, latestReference, true, 1);
        var latestService = service();
        var latestHandle = admitted(latestService, latestTag);
        var latestCallbacks = new ArrayList<Callback>();
        latestService.drainRootProjection(
                latestHandle, new RecordingSink(latestCallbacks));
        assertEquals(
                List.of(new Callback("latest", -1, latestReference)), latestCallbacks);

        var equippedReference = reference(6L, 7);
        var equippedTag = emptyReadyTag();
        addEquipped(equippedTag, 4, equippedReference);
        var equippedService = service();
        var equippedHandle = admitted(equippedService, equippedTag);
        var equippedCallbacks = new ArrayList<Callback>();
        equippedService.drainRootProjection(
                equippedHandle, new RecordingSink(equippedCallbacks));
        assertEquals(
                List.of(new Callback("equipped", 4, equippedReference)), equippedCallbacks);
    }

    @Test
    void projectionWrongOwnerNullSinkAndSinkFailuresConsumeBeforeFailure() {
        var tag = emptyReadyTag();
        addLatest(tag, reference(1L, 1), true, 1);
        addEquipped(tag, 0, reference(2L, 2));

        var owner = service();
        var other = service();
        var wrongOwner = admitted(owner, tag);
        assertMisuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH",
                () -> other.rootCount(wrongOwner));
        assertProjectionConsumed(owner, wrongOwner);

        var wrongOwnerDrain = admitted(owner, tag);
        assertMisuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH",
                () -> other.drainRootProjection(
                        wrongOwnerDrain, new RecordingSink(new ArrayList<>())));
        assertProjectionConsumed(owner, wrongOwnerDrain);

        var wrongOwnerDiscard = admitted(owner, tag);
        assertMisuse("P4E1_ROOT_PROJECTION_OWNER_MISMATCH",
                () -> other.discardRootProjection(wrongOwnerDiscard));
        assertProjectionConsumed(owner, wrongOwnerDiscard);

        var nullSink = admitted(owner, tag);
        assertThrows(NullPointerException.class,
                () -> owner.drainRootProjection(nullSink, null));
        assertProjectionConsumed(owner, nullSink);

        var runtime = admitted(owner, tag);
        var runtimeCalls = new ArrayList<SkillReference>();
        assertThrows(IllegalStateException.class, () -> owner.drainRootProjection(
                runtime,
                new PlayerSkillAttachmentService.RootAuditSink() {
                    @Override
                    public void latest(SkillReference reference) {
                        assertMisuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED",
                                () -> owner.rootCount(runtime));
                        runtimeCalls.add(reference);
                        throw new IllegalStateException("expected sink runtime");
                    }

                    @Override
                    public void equipped(int slot, SkillReference reference) {
                        runtimeCalls.add(reference);
                    }
                }));
        assertEquals(1, runtimeCalls.size());
        assertProjectionConsumed(owner, runtime);

        var error = admitted(owner, tag);
        var exactError = new AssertionError("expected sink error");
        assertSame(exactError, assertThrows(AssertionError.class,
                () -> owner.drainRootProjection(
                        error,
                        new PlayerSkillAttachmentService.RootAuditSink() {
                            @Override
                            public void latest(SkillReference reference) {
                                throw exactError;
                            }

                            @Override
                            public void equipped(int slot, SkillReference reference) {
                                throw exactError;
                            }
                        })));
        assertProjectionConsumed(owner, error);

        var oome = admitted(owner, tag);
        var exactOome = new OutOfMemoryError("expected sink OOME");
        assertSame(exactOome, assertThrows(OutOfMemoryError.class,
                () -> owner.drainRootProjection(
                        oome,
                        new PlayerSkillAttachmentService.RootAuditSink() {
                            @Override
                            public void latest(SkillReference reference) {
                                throw exactOome;
                            }

                            @Override
                            public void equipped(int slot, SkillReference reference) {
                                throw exactOome;
                            }
                        })));
        assertProjectionConsumed(owner, oome);
    }

    private static void assertEquivalent(
            Tag tag,
            PlayerSkillAttachmentServiceTestSupport.SerializerClassification expected) {
        var before = tag.copy();
        var width = writeAnyTagWidth(tag);
        assertBridgeClassification(tag, width, expected);
        assertEquals(expected,
                PlayerSkillAttachmentServiceTestSupport.classifyWithRegisteredSerializer(
                        tag, PROVIDER));
        assertEquals(before, tag);
    }

    private static void assertBridgeClassification(
            Tag tag,
            long width,
            PlayerSkillAttachmentServiceTestSupport.SerializerClassification expected) {
        var service = service();
        var result = P4E1BoundPlayerSkillAttachmentAdmissionSource.admitIntegratedObservation(
                service,
                new P4E1IntegratedSnapshotTraversal.AttachmentObservation(tag, width),
                PROVIDER);
        var actual = switch (result) {
            case PlayerSkillAttachmentService.RootAuditAdmitted admitted -> {
                service.discardRootProjection(admitted);
                yield PlayerSkillAttachmentServiceTestSupport.SerializerClassification.ADMITTED;
            }
            case PlayerSkillAttachmentService.RootAuditRejected ignored ->
                    PlayerSkillAttachmentServiceTestSupport.SerializerClassification.REJECTED;
            case PlayerSkillAttachmentService.RootAuditOversize ignored ->
                    PlayerSkillAttachmentServiceTestSupport.SerializerClassification.OVERSIZE;
        };
        assertEquals(expected, actual);
    }

    private static PlayerSkillAttachmentService.RootAuditAdmitted admitted(
            PlayerSkillAttachmentService service, Tag tag) {
        return assertInstanceOf(
                PlayerSkillAttachmentService.RootAuditAdmitted.class,
                P4E1BoundPlayerSkillAttachmentAdmissionSource.admitIntegratedObservation(
                        service,
                        new P4E1IntegratedSnapshotTraversal.AttachmentObservation(
                                tag, writeAnyTagWidth(tag)),
                        PROVIDER));
    }

    private static P4E1BoundPlayerSkillAttachmentAdmissionSource source(
            PlayerSkillAttachmentService owner,
            Tag input,
            Tag measurementInput,
            long width,
            HolderLookup.Provider provider,
            HolderLookup.Provider providerWitness) {
        return new P4E1BoundPlayerSkillAttachmentAdmissionSource(
                owner, input, measurementInput, width, provider, providerWitness);
    }

    private static void assertConsumed(
            PlayerSkillAttachmentService service,
            P4E1BoundPlayerSkillAttachmentAdmissionSource source) {
        assertMisuse("P4E1_ADMISSION_SOURCE_ALREADY_CONSUMED",
                () -> service.admitForRootAudit(source));
    }

    private static void assertProjectionConsumed(
            PlayerSkillAttachmentService service,
            PlayerSkillAttachmentService.RootAuditAdmitted admitted) {
        assertMisuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED",
                () -> service.rootCount(admitted));
        assertMisuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED",
                () -> service.discardRootProjection(admitted));
        assertMisuse("P4E1_ROOT_PROJECTION_ALREADY_CONSUMED",
                () -> service.drainRootProjection(admitted, new RecordingSink(new ArrayList<>())));
    }

    private static void assertMisuse(String code, org.junit.jupiter.api.function.Executable action) {
        var failure = assertThrows(IllegalStateException.class, action);
        assertEquals(code, failure.getMessage());
    }

    private static PlayerSkillAttachmentService service() {
        return PlayerSkillAttachmentServiceTestSupport.createService();
    }

    private static CompoundTag emptyReadyTag() {
        var root = new CompoundTag();
        root.putInt("attachment_schema_version", 0);
        root.put("drafts", new ListTag());
        root.put("latest_states", new ListTag());
        root.put("equipped_slots", new ListTag());
        root.put("editor", new CompoundTag());
        return root;
    }

    private static CompoundTag oversizeMarker() {
        var marker = new CompoundTag();
        marker.putInt("schema_version", 0);
        marker.putString("code", "encoded_capacity_exceeded");
        marker.putLong("observed_at_least",
                (long) MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES + 1L);
        marker.putLong("maximum",
                MagicSafetyCeilings.MAX_PLAYER_SKILL_ATTACHMENT_ENCODED_BYTES);
        var root = new CompoundTag();
        root.put("__gramarye_attachment_quarantine_v0", marker);
        return root;
    }

    private static CompoundTag draftTag(
            SkillId route, String encoding, byte[] encodedDraft) {
        var tag = emptyReadyTag();
        var entry = new CompoundTag();
        entry.put("skill_id", NbtUtils.createUUID(route.value()));
        entry.putString("draft_encoding", encoding);
        entry.putByteArray("draft_bytes", encodedDraft);
        ((ListTag) tag.get("drafts")).add(entry);
        return tag;
    }

    private static byte[] mutateEncodedDraft(
            byte[] encoded,
            java.util.function.Consumer<CompoundTag> mutation) {
        try {
            CompoundTag root;
            try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
                root = assertInstanceOf(
                        CompoundTag.class,
                        NbtIo.readAnyTag(input, new NbtAccounter(
                                MagicSafetyCeilings.MAX_PLAYER_DRAFT_ENTRY_ENCODED_BYTES,
                                MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH)));
                assertEquals(-1, input.read());
            }
            mutation.accept(root);
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                NbtIo.writeAnyTag(root, output);
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("test Draft mutation failed", exception);
        }
    }

    private static void addLatest(
            CompoundTag root, SkillReference reference, boolean present, int generation) {
        var entry = new CompoundTag();
        entry.put("skill_id", NbtUtils.createUUID(reference.skillId().value()));
        entry.putInt("mutation_generation", generation);
        if (present) {
            entry.put("pointer", referenceTag(reference));
        }
        ((ListTag) root.get("latest_states")).add(entry);
    }

    private static void addEquipped(CompoundTag root, int slot, SkillReference reference) {
        var entry = new CompoundTag();
        entry.putInt("slot", slot);
        entry.put("reference", referenceTag(reference));
        ((ListTag) root.get("equipped_slots")).add(entry);
    }

    private static CompoundTag referenceTag(SkillReference reference) {
        var tag = new CompoundTag();
        tag.putString("skill_id", reference.skillId().value().toString());
        tag.putInt("revision", reference.revision().value());
        return tag;
    }

    private static SkillReference reference(long route, int revision) {
        return new SkillReference(
                new SkillId(new UUID(0L, route)), new SkillRevision(revision));
    }

    private static long writeAnyTagWidth(Tag tag) {
        var output = new CountingDataOutput();
        try {
            net.minecraft.nbt.NbtIo.writeAnyTag(tag, output);
        } catch (IOException exception) {
            throw new IllegalStateException("test Tag counting failed", exception);
        }
        return output.count;
    }

    private record Callback(String category, int slot, SkillReference reference) {
    }

    private record RecordingSink(List<Callback> callbacks)
            implements PlayerSkillAttachmentService.RootAuditSink {
        private RecordingSink {
            java.util.Objects.requireNonNull(callbacks, "callbacks");
        }

        @Override
        public void latest(SkillReference reference) {
            callbacks.add(new Callback("latest", -1, reference));
        }

        @Override
        public void equipped(int slot, SkillReference reference) {
            callbacks.add(new Callback("equipped", slot, reference));
        }
    }

    private static final class ExplodingCompoundTag extends CompoundTag {
        private final RuntimeException runtime;
        private final Error error;

        private ExplodingCompoundTag(RuntimeException runtime) {
            this.runtime = runtime;
            this.error = null;
        }

        private ExplodingCompoundTag(Error error) {
            this.runtime = null;
            this.error = error;
        }

        @Override
        public Set<String> getAllKeys() {
            if (error != null) {
                throw error;
            }
            throw runtime;
        }
    }

    private static final class CopyCountingTag implements Tag {
        private int copyCount;
        private int writeCount;

        @Override
        public void write(DataOutput output) throws IOException {
            writeCount++;
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

    private static final class CountingDataOutput implements DataOutput {
        private long count;

        private void add(long delta) {
            count = Math.addExact(count, delta);
        }

        @Override
        public void write(int value) {
            add(1);
        }

        @Override
        public void write(byte[] value) {
            add(value.length);
        }

        @Override
        public void write(byte[] value, int offset, int length) {
            add(length);
        }

        @Override
        public void writeBoolean(boolean value) {
            add(1);
        }

        @Override
        public void writeByte(int value) {
            add(1);
        }

        @Override
        public void writeShort(int value) {
            add(2);
        }

        @Override
        public void writeChar(int value) {
            add(2);
        }

        @Override
        public void writeInt(int value) {
            add(4);
        }

        @Override
        public void writeLong(long value) {
            add(8);
        }

        @Override
        public void writeFloat(float value) {
            add(4);
        }

        @Override
        public void writeDouble(double value) {
            add(8);
        }

        @Override
        public void writeBytes(String value) {
            add(value.length());
        }

        @Override
        public void writeChars(String value) {
            add(Math.multiplyExact(value.length(), 2));
        }

        @Override
        public void writeUTF(String value) throws IOException {
            var output = new java.io.ByteArrayOutputStream();
            try (var data = new java.io.DataOutputStream(output)) {
                data.writeUTF(value);
            }
            add(output.size());
        }
    }
}
