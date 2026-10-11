package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import com.yo1no.gramarye.magic.definition.document.AppearanceDefinition;
import com.yo1no.gramarye.magic.definition.document.AppearanceDocument;
import com.yo1no.gramarye.magic.definition.document.ProfileSelection;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.store.SkillSubsystemResult;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** One real L1 cast with legal server appearance and an actually absent client asset. */
public final class P11L1P8FallbackProbe {
    static final ResourceLocation PROFILE = ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", "missing_sound");
    static final ResourceLocation ASSET = ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", "absent_sound_asset");
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionSubmissionService submissions;
    private static SkillDefinitionStoreService store;
    private static MinecraftServer server;
    private static ServerPlayer actor, peer;
    private static SkillReference reference;
    private static ServerSlot.InstanceState instance;
    private static long sequence, catalog;
    private static int targetId, sends;
    private P11L1P8FallbackProbe() { }

    public static boolean selected() {
        return "l1-p8-missing-asset".equals(System.getProperty("gramarye.p11.online.case", ""));
    }

    public static void composition(PlayerSkillAttachmentService players,
            SkillDefinitionSubmissionService submission, SkillDefinitionStoreService definitions) {
        if (!selected()) return;
        require(attachments == null && players != null && submission != null && definitions != null, "COMPOSITION_ONCE");
        attachments = players; submissions = submission; store = definitions;
    }

    static void prepare(MinecraftServer exact, ServerPlayer a, ServerPlayer b) throws IOException {
        if (!selected()) return;
        require(server == null && attachments != null && exact.isSameThread() && a != b
                && exact.getPlayerList().getPlayer(a.getUUID()) == a
                && exact.getPlayerList().getPlayer(b.getUUID()) == b, "ORIGINAL_TWO_ACTORS");
        var equipped = attachments.equippedAt(a, 0);
        require(equipped instanceof PlayerSkillAttachmentService.Available<?> value
                && value.value() instanceof Optional<?> option && option.orElse(null) instanceof SkillReference, "ORIGINAL_STARTER");
        var base = (SkillReference) ((Optional<?>) ((PlayerSkillAttachmentService.Available<?>) equipped).value()).orElseThrow();
        var canonical = P9StarterSkillContent.canonicalDraft(base.skillId());
        var appearance = AppearanceDocument.decoded(new AppearanceDefinition(OptionalInt.empty(), OptionalInt.empty(),
                ProfileSelection.specified(PROFILE), ProfileSelection.disabled(), ProfileSelection.disabled(), OptionalInt.empty()));
        var draft = new SkillDraft(canonical.draftSchemaVersion(), base.skillId(), Optional.of(base.revision()),
                canonical.nodes(), appearance);
        applied(attachments.putDraft(a, draft), "FORMAL_DRAFT");
        var submitted = submissions.submit(a, draft.skillId());
        require(submitted instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_COMMIT");
        reference = ((SkillSubmissionCompositionOutcome.Committed) submitted).reference();
        applied(attachments.setEquipped(a, 0, Optional.of(reference)), "FORMAL_EQUIP");
        var loaded = store.find(exact, reference);
        require(loaded instanceof SkillSubsystemResult.Available<?> value
                && value.value() instanceof Optional<?> option
                && option.orElse(null) instanceof com.yo1no.gramarye.magic.definition.document.SkillDocument document
                && P9StarterSkillContent.hasCanonicalGameplayFingerprint(reference, document), "UNCHANGED_GAMEPLAY_FINGERPRINT");
        server = exact; actor = a; peer = b;
        P11C4aEvidence.write(P11C4aEvidence.root().resolve("server"), "p8-fallback-formal.json", Map.of(
                "status", "ORIGINAL_FORMAL_COMMIT_EQUIP_UNCHANGED_GAMEPLAY", "reference", reference.toString(),
                "selectedProfile", PROFILE.toString(), "absentAsset", ASSET.toString(), "duration", 0));
    }

    static void accepted(ServerPlayer exactActor, Object value) {
        if (!selected()) return;
        require(server.isSameThread() && actor == exactActor && instance == null
                && value instanceof ServerSlot.InstanceState, "ACTUAL_ACCEPTANCE_ONCE");
        instance = (ServerSlot.InstanceState) value;
        require(instance.lease.reference.equals(reference) && instance.work != null && !instance.lease.pin.isClosed()
                && instance.hasP9AuthenticatedActorWitness(actor), "EXACT_ACCEPTED_REFERENCE_WORK");
    }

    /** Called after the unique original native send invocation has returned. */
    public static void sent(ServerPlayer recipient, CustomPacketPayload value) {
        if (!selected() || server == null || recipient != peer || !(value instanceof PresentationEventPayload event)
                || event.kind() != PresentationEventKind.HIT) return;
        require(server.isSameThread() && instance != null && sends == 0
                && event.appearance().soundProfileId().orElse(null) != null
                && event.appearance().soundProfileId().orElseThrow().equals(PROFILE)
                && event.sourceSummary().targetEntityId().isPresent(), "ORIGINAL_SELECTED_HIT_SEND");
        sends++; sequence = event.sequence(); catalog = event.catalogGeneration();
        targetId = event.sourceSummary().targetEntityId().getAsInt();
    }

    static boolean clientReady() throws IOException {
        if (!selected()) return true;
        var file = P11C4aEvidence.root().resolve("client-b").resolve("p8-fallback-client.json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return false;
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 16_384, "CLIENT_RECEIPT_SHAPE");
        var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        require(sends == 1 && matchesClientReceipt(json, sequence, catalog, targetId, P11C4aEvidence.property("runId")),
                "MATCHED_ACTUAL_CLIENT_FALLBACK");
        return true;
    }

    static boolean matchesClientReceipt(com.google.gson.JsonObject json, long sequence, long catalog, int targetId, String runId) {
        return json.get("status").getAsString().equals("ORIGINAL_CLIENT_MISSING_ASSET_DEFAULT_SOUND_FALLBACK")
                && json.get("runId").getAsString().equals(runId)
                && json.get("case").getAsString().equals("l1-p8-missing-asset")
                && json.get("role").getAsString().equals("b")
                && json.get("sequence").getAsLong() == sequence && json.get("catalogGeneration").getAsLong() == catalog
                && json.get("targetEntityId").getAsInt() == targetId
                && json.get("connectionGeneration").getAsLong() > 0 && json.get("worldGeneration").getAsLong() > 0
                && json.get("resourceGeneration").getAsLong() > 0
                && json.get("actualMissingAsset").getAsBoolean() && json.get("assetUnavailableDiagnostic").getAsBoolean()
                && json.get("profileUnavailableDiagnostic").getAsBoolean()
                && json.get("originalDefaultSoundEvents").getAsInt() == 1
                && json.get("presentCalls").getAsInt() == 1
                && json.get("originalResult").getAsString().equals("PRESENTED");
    }

    static Map<String, Object> finish(ServerPlayer current, int actualTarget) throws IOException {
        require(selected() && server.isSameThread() && clientReady() && targetId == actualTarget
                && current != actor && current.getUUID().equals(actor.getUUID())
                && instance != null && instance.work == null && instance.lease.pin.isClosed(), "FINAL_NATIVE_CUSTODY");
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "SERVER_LEGAL_APPEARANCE_CLIENT_ASSET_FALLBACK_AND_ORIGINAL_L1_TERMINAL");
        result.put("reference", reference.toString()); result.put("sequence", sequence); result.put("catalogGeneration", catalog);
        result.put("targetEntityId", targetId); result.put("originalPeerHitSends", sends);
        result.put("selectedProfile", PROFILE.toString()); result.put("absentAsset", ASSET.toString());
        result.put("workAndPinTerminal", true); result.put("newRegistryTypeMissingClaim", false);
        return Map.copyOf(result);
    }

    private static void applied(PlayerSkillAttachmentService.Result<?> result, String code) {
        require(result instanceof PlayerSkillAttachmentService.Available<?> value
                && value.value() == PlayerSkillAttachmentService.Applied.INSTANCE, code);
    }
    private static void require(boolean condition, String code) { P11C4aEvidence.require(condition, "P8_FALLBACK_" + code); }
}
