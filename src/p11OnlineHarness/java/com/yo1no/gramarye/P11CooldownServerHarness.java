package com.yo1no.gramarye;

import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.definition.document.DraftNode;
import com.yo1no.gramarye.magic.definition.document.DraftTriggerSlot;
import com.yo1no.gramarye.magic.definition.document.SkillDraft;
import com.yo1no.gramarye.magic.definition.document.SkillReference;
import com.yo1no.gramarye.magic.definition.envelope.DefinitionEnvelope;
import com.yo1no.gramarye.magic.definition.player.PlayerSkillAttachmentService;
import com.yo1no.gramarye.magic.definition.store.SkillDefinitionStoreService;
import com.yo1no.gramarye.magic.definition.submission.SkillDefinitionSubmissionService;
import com.yo1no.gramarye.magic.definition.submission.SkillSubmissionCompositionOutcome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded true R/formal-submit/native-add experiment. Does not set cooldown/work/clock facts. */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11CooldownServerHarness {
    private enum Stage { CONNECT, STARTER, FIRST_ARM, ACTIVE_RETRY, RECONNECT, EXPIRY, SECOND_ARM,
        RESTART_ACTIVE_RETRY, RESTART_EXPIRY, RESTART_NEW_ARM, FINISH }
    private static final Map<Connection, UUID> AUTH = new IdentityHashMap<>();
    private static PlayerSkillAttachmentService attachments;
    private static SkillDefinitionSubmissionService submissions;
    private static SkillDefinitionStoreService store;
    private static P11FoundationService foundation;
    private static P11CastCooldownService cooldowns;
    private static ServerSlot slot;
    private static volatile MinecraftServer server;
    private static ServerPlayer actor, peer, originalActor;
    private static Connection originalConnection;
    private static SkillReference reference;
    private static ServerSlot.InstanceState constructed;
    private static final ServerSlot.InstanceState[] instances = new ServerSlot.InstanceState[2];
    private static final RuntimeEvent[] admittedRoots = new RuntimeEvent[2];
    private static final long[] releaseRuntimeTicks = new long[2];
    private static final P9StarterProjectile[] projectiles = new P9StarterProjectile[2];
    private static final P11CastCooldownData.Entry[] releases = new P11CastCooldownData.Entry[2];
    private static Path output;
    private static Stage stage = Stage.CONNECT;
    private static int ticks, logins, admissions, rejections, transfers, claims, logouts;
    private static int durabilityExpectedFailures = -1;
    private static boolean aCue, bCue, reconnectCue, finishing, sealed;
    private static volatile boolean authFailed;
    private static String failure;
    private P11CooldownServerHarness() {}

    public static boolean selected() {
        return P11CooldownFaultProbe.selected() || P11CooldownCloneProbe.selected() || P11CooldownDurabilityProbe.selected() || P11CooldownDualProbe.selected()
                || List.of("cooldown-d1", "cooldown-d120", "cooldown-d600", "cooldown-restart-write", "cooldown-restart-read")
                .contains(System.getProperty("gramarye.p11.online.case", ""));
    }
    static int duration() {
        if (P11CooldownFaultProbe.selected() || P11CooldownCloneProbe.selected() || P11CooldownDurabilityProbe.selected() || P11CooldownDualProbe.selected()) return 600;
        return switch (System.getProperty("gramarye.p11.online.case", "")) {
            case "cooldown-d1" -> 1; case "cooldown-d120" -> 120;
            case "cooldown-d600", "cooldown-restart-write", "cooldown-restart-read" -> 600;
            default -> throw new IllegalStateException("COOLDOWN_CASE");
        };
    }

    public static void composition(PlayerSkillAttachmentService a, SkillDefinitionSubmissionService s,
            SkillDefinitionStoreService d) {
        if (!selected()) return;
        require(attachments == null && a != null && s != null && d != null, "COMPOSITION_ONCE");
        attachments = a; submissions = s; store = d;
    }
    public static void cooldownOwner(Object owner, Object source) {
        if (!selected()) return;
        require(cooldowns == null && owner instanceof P11CastCooldownService && source instanceof P11FoundationService,
                "COOLDOWN_COMPOSITION_ONCE");
        cooldowns = (P11CastCooldownService) owner; foundation = (P11FoundationService) source;
    }
    public static void runtimeStarted(MinecraftServer exact, Object actualSlot) {
        if (!selected()) return;
        require(slot == null && actualSlot instanceof ServerSlot && exact.isSameThread(), "RUNTIME_START_ONCE");
        slot = (ServerSlot) actualSlot;
        require(slot.instances.isEmpty() && slot.activeProjectileContinuations.isEmpty(), "FRESH_RUNTIME");
    }
    public static synchronized void authenticated(MinecraftServer exact, Connection connection, UUID id) {
        if (!selected()) return;
        if (server != exact || connection == null || id == null || AUTH.size() >= 3 || AUTH.containsKey(connection)) {
            authFailed = true; return;
        }
        AUTH.put(connection, id);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (!selected()) return;
        try {
            server = event.getServer();
            require(server.isDedicatedServer() && server.usesAuthentication() && !server.isSingleplayer()
                    && slot != null && cooldowns != null && attachments != null, "DEDICATED_WIRED_SOURCE");
            output = P11C4aEvidence.reserve("server");
            P11CooldownCostProbe.started(server, cooldowns);
            P11CooldownRestartProbe.initialize(server, slot, store);
            if (P11CooldownRestartProbe.readSelected()) {
                P11CooldownRestartProbe.prepareRead(output);
                reference = P11CooldownRestartProbe.reference();
            }
            P11C4aEvidence.write(output, "ready.json", Map.of("status", "ONLINE_DEDICATED_READY_NO_AUTH_CLAIM",
                    "case", P11C4aEvidence.property("case"), "runId", P11C4aEvidence.property("runId"),
                    "productionJarSha256", P11OnlineInputs.verifyFrozenJar(), "onlineMode", true,
                    "integrated", false, "expectedPlayers", 2, "configurationSha256", P11C4aLoadedConfiguration.hash()));
        } catch (Exception | LinkageError problem) { fail(problem); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!selected() || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) return;
        try {
            var connection = player.connection.getConnection(); UUID id;
            synchronized (P11CooldownServerHarness.class) { id = AUTH.remove(connection); }
            current(player);
            require(player.getUUID().equals(id) && connection.isEncrypted() && !connection.isMemoryConnection(), "EXACT_AUTH_PLAY");
            String role;
            if (originalActor == null) {
                originalActor = actor = player; originalConnection = connection; logins = 1; role = "a";
            } else if (player.getUUID().equals(originalActor.getUUID())) {
                require(stage == Stage.RECONNECT && logins == 1 && logouts == 1 && originalActor.isRemoved()
                        && !originalConnection.isConnected() && player != originalActor && connection != originalConnection,
                        "ORIGINAL_SAME_ACCOUNT_RECONNECT");
                actor = player; logins++; role = "a";
            } else {
                require(peer == null && logins == 1, "EXACT_SECOND_PEER"); peer = player; role = "b";
            }
            P11C4aEvidence.write(output, role + "-auth-" + (role.equals("a") ? logins : 1) + ".json",
                    Map.of("status", "ORIGINAL_HAS_JOINED_TO_EXACT_PLAY", "encrypted", true,
                            "sameAccountReplacement", role.equals("a") && logins == 2,
                            "identityPseudonym", P11C4aEvidence.pseudonym(player.getUUID())));
        } catch (Exception | LinkageError problem) { fail(problem); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!selected() || !(event.getEntity() instanceof ServerPlayer player) || player.getServer() != server) return;
        if (finishing && (player == actor || player == peer)
                || P11CooldownFaultProbe.originalNativeFaultObserved() && (player == actor || player == peer)
                || P11CooldownFaultProbe.originalUnarmedStopRequested() && (player == actor || player == peer)
                || stage == Stage.RECONNECT && player == originalActor && logouts == 0) { logouts++; }
        else if (failure == null) failure = "UNEXPECTED_LOGOUT";
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        if (!selected() || server != event.getServer() || sealed) return;
        try {
            require(server.isSameThread() && ++ticks <= 12_000 && !authFailed, "FINITE_TICK_OR_AUTH_BOUND");
            if (failure != null) { fail(new IllegalStateException("COOLDOWN_OBSERVER_FAILURE")); return; }
            if (!aCue && receipt("client-a", "inputs.json")) { aCue = true; cue("a-connect.ready"); }
            if (actor != null && !bCue && receipt("client-b", "inputs.json")) { bCue = true; cue("b-connect.ready"); }
            if (finishing) {
                if (logouts == (duration() == 1 || P11CooldownRestartProbe.readSelected()
                        || P11CooldownFaultProbe.selected() || P11CooldownCloneProbe.selected()
                        || P11CooldownDurabilityProbe.selected() || P11CooldownDualProbe.selected() ? 2 : 3)
                        && server.getPlayerList().getPlayers().isEmpty()
                        && receipt("client-a", "result.json") && receipt("client-b", "result.json")) {
                    sealed = true; server.halt(false);
                }
                return;
            }
            if (actor == null || peer == null) return;
            current(peer);
            if (P11CooldownCloneProbe.selected()) { progressClone(); return; }
            if (stage != Stage.RECONNECT) current(actor);
            if (P11CooldownFaultProbe.selected()) { progressFault(); return; }
            if (P11CooldownDurabilityProbe.selected()) { progressDurability(); return; }
            if (P11CooldownDualProbe.selected()) { progressDual(); return; }
            switch (stage) {
                case CONNECT -> {
                    arena();
                    if (P11CooldownRestartProbe.readSelected()) {
                        P11CooldownRestartProbe.loaded(actor, equipped().orElseThrow(), entry());
                        P11C4aEvidence.write(output, "formal-submission.json", Map.of(
                                "status", "ORIGINAL_RESTART_REFERENCE_NO_SUBMIT", "reference", reference.toString(),
                                "cooldownTicks", duration(), "starterOrRegrant", false));
                        stage = Stage.RESTART_ACTIVE_RETRY; cue("a-cast-1.ready");
                    } else { cue("a-starter.ready"); stage = Stage.STARTER; }
                }
                case STARTER -> {
                    var equipped = equipped(); if (equipped.isEmpty()) return;
                    submit(equipped.orElseThrow()); stage = Stage.FIRST_ARM; cue("a-cast-1.ready");
                }
                case FIRST_ARM -> {
                    if (transfers != 1) return;
                    if (P11CooldownRestartProbe.writeSelected() && !receipt("client-a", "hud-active.json")) return;
                    writeRelease(0);
                    if (P11CooldownRestartProbe.writeSelected()) {
                        require(admissions == 1 && claims == 0 && rejections == 0, "RESTART_ONE_REAL_RELEASE");
                        server.saveEverything(false, true, true);
                        P11CooldownRestartProbe.beforeOriginalStop(actor, reference, releases[0], instances[0], projectiles[0], output);
                        P11C4aEvidence.write(output, "cooldown-result.json", Map.of(
                                "status", "REAL_ARM_BEFORE_ORIGINAL_STOP_NOT_RESTART_PROOF", "durationTicks", duration(),
                                "accepted", admissions, "nativeTransfers", transfers, "queuedHitChildren", claims));
                        finishing = true; sealed = true; stage = Stage.FINISH; server.halt(false);
                    } else if (duration() == 1) { stage = Stage.EXPIRY; }
                    else {
                        require(gameTime() < releases[0].expiresAt, "ACTIVE_WINDOW_BEFORE_NATIVE_SAVE");
                        server.saveEverything(false, true, true);
                        physical(releases[0]);
                        P11C4aEvidence.write(output, "saved-active.json", Map.of("status", "ORIGINAL_SAVE_AND_PHYSICAL_ACTIVE",
                                "releaseGameTime", releases[0].releasedAt, "expiresAt", releases[0].expiresAt,
                                "gameTime", gameTime(), "unchangedDuration", duration()));
                        stage = Stage.ACTIVE_RETRY; cue("a-cast-2.ready");
                    }
                }
                case ACTIVE_RETRY -> {
                    if (rejections != 1) return;
                    require(admissions == 1 && transfers == 1 && gameTime() < releases[0].expiresAt, "ACTIVE_REFUSAL_WINDOW");
                    P11C4aEvidence.write(output, "active-refusal.json", Map.of("status", "REAL_R_ACTIVE_ADMISSION_REFUSAL",
                            "accepted", admissions, "rejected", rejections, "nativeTransfers", transfers,
                            "rejectionHasNoWorkOrQueue", true));
                    stage = Stage.RECONNECT; cue("a-close.ready");
                }
                case RECONNECT -> {
                    if (logouts == 1 && !originalConnection.isConnected() && !reconnectCue) {
                        reconnectCue = true; cue("a-reconnect.ready");
                    }
                    if (logins != 2) return;
                    current(actor); require(equipped().filter(reference::equals).isPresent(), "RECONNECT_EXACT_REFERENCE");
                    var entry = entry();
                    require(gameTime() < releases[0].expiresAt && sameRelease(entry, releases[0]), "RECONNECT_UNEXPIRED_SAME_OBLIGATION");
                    P11C4aEvidence.write(output, "reconnect-active.json", Map.of("status", "REAL_RECONNECT_SAME_ACTIVE_OBLIGATION",
                            "newActor", true, "newConnection", true, "expiresAt", entry.expiresAt,
                            "gameTime", gameTime(), "sameLogicalKey", true));
                    stage = Stage.EXPIRY;
                }
                case EXPIRY -> {
                    if (gameTime() < releases[0].expiresAt || !terminal(0)) return;
                    require(claims == 0, "EMPTY_RAY_NO_CHILD");
                    stage = Stage.SECOND_ARM; cue("a-cast-" + (duration() == 1 ? 2 : 3) + ".ready");
                }
                case SECOND_ARM -> {
                    if (transfers != 2 || !terminal(1)) return;
                    require(admissions == 2 && rejections == (duration() == 1 ? 0 : 1) && claims == 0,
                            "TWO_EXACT_RELEASES_AND_NO_CLAIMS");
                    writeRelease(1);
                    require(releases[1].acceptedAt >= releases[0].expiresAt
                            && !releases[1].attemptId.equals(releases[0].attemptId), "NATURAL_EXPIRY_NEW_ATTEMPT");
                    P11C4aEvidence.write(output, "cooldown-result.json", Map.of("status", "NATIVE_ARM_EXPIRY_AND_NEW_R_OBSERVED",
                            "durationTicks", duration(), "accepted", admissions, "activeRefusals", rejections,
                            "nativeTransfers", transfers, "queuedHitChildren", claims, "bothWorkPinsTerminal", true,
                            "sameServerReconnect", logins == 2, "newServerRestartClaim", false,
                            "physicalOsInputClaim", false));
                    finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
                }
                case RESTART_ACTIVE_RETRY -> {
                    if (rejections != 1) return;
                    require(admissions == 0 && transfers == 0 && claims == 0
                            && gameTime() < P11CooldownRestartProbe.expiresAt(), "RESTART_ACTIVE_REFUSAL_WITHOUT_NEW_WORK");
                    P11C4aEvidence.write(output, "active-refusal.json", Map.of(
                            "status", "REAL_NEW_SERVER_R_ACTIVE_REFUSAL", "accepted", admissions,
                            "activeRefusals", rejections, "nativeTransfers", transfers));
                    stage = Stage.RESTART_EXPIRY;
                }
                case RESTART_EXPIRY -> {
                    if (gameTime() < P11CooldownRestartProbe.expiresAt()
                            || !receipt("client-a", "hud-ready.json")) return;
                    stage = Stage.RESTART_NEW_ARM; cue("a-cast-2.ready");
                }
                case RESTART_NEW_ARM -> {
                    if (transfers != 1 || !terminal(0)) return;
                    require(admissions == 1 && rejections == 1 && claims == 0
                            && P11CooldownRestartProbe.isNewReleaseAfterExpiry(releases[0]), "RESTART_NATURAL_EXPIRY_NEW_ATTEMPT");
                    writeRelease(0);
                    P11C4aEvidence.write(output, "cooldown-result.json", Map.of(
                            "status", "NATIVE_RESTART_OBLIGATION_EXPIRY_AND_NEW_R_OBSERVED", "durationTicks", duration(),
                            "accepted", admissions, "activeRefusals", rejections, "nativeTransfers", transfers,
                            "queuedHitChildren", claims, "workPinTerminal", true, "newServerRestartClaim", true,
                            "physicalOsInputClaim", false));
                    finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
                }
                case FINISH -> { }
            }
        } catch (Exception | LinkageError problem) { fail(problem); }
    }

    public static void instance(ServerPlayer exactActor, Object value) {
        if (P11CooldownDualProbe.selected() && failure == null) {
            try { P11CooldownDualProbe.instance(exactActor, value); }
            catch (RuntimeException | LinkageError problem) { failScalar(problem); }
            return;
        }
        if (!selected() || exactActor != actor || failure != null) return;
        if (!(value instanceof ServerSlot.InstanceState actual) || !server.isSameThread()) {
            failure = "CONSTRUCTOR_IDENTITY"; return;
        }
        constructed = actual;
    }
    public static void admitted(MinecraftServer exact, ServerPlayer exactActor, SkillReference ref, Object value) {
        if (P11CooldownDualProbe.selected() && failure == null) {
            try { P11CooldownDualProbe.admitted(exact, exactActor, ref, value); }
            catch (RuntimeException | LinkageError problem) { failScalar(problem); }
            return;
        }
        if (!selected() || exactActor != actor || failure != null) return;
        try {
            require(exact == server && ref.equals(reference) && constructed != null, "ADMISSION_BINDING");
            if (P11CooldownFaultProbe.selected()) {
                P11CooldownFaultProbe.admitted(exact, exactActor, ref, constructed, value); constructed = null; return;
            }
            if (P11CooldownDurabilityProbe.selected()) {
                P11CooldownDurabilityProbe.admitted(exact, exactActor, ref, constructed, value); constructed = null; return;
            }
            if (value instanceof RuntimeAdmissionResult.AcceptedMemoryOnly accepted) {
                require(admissions < 2 && constructed.id.equals(accepted.eventToken().skillInstanceId())
                        && constructed.work != null && !constructed.lease.pin.isClosed()
                        && constructed.cooldownReceipt != null
                        && constructed.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.PENDING
                        && slot.instances.get(constructed.id) == constructed, "ACTUAL_PUBLISHED_PENDING");
                var pending = entry();
                var root = slot.queue.stream().filter(event -> event.eventId().equals(accepted.eventToken().eventId()))
                        .findFirst().orElseThrow();
                require(pending.kind == 1 && pending.duration == duration()
                        && pending.attemptId.equals(constructed.cooldownReceipt.attemptId())
                        && root.skillInstanceId().equals(constructed.id) && root.createdRuntimeTick() == slot.runtimeTick
                        && pending.releaseNotAfter == pending.acceptedAt + (root.deadlineRuntimeTick() - root.createdRuntimeTick()),
                        "PENDING_ATTACHMENT_SAME_RECEIPT");
                admittedRoots[admissions] = root;
                instances[admissions++] = constructed;
            } else {
                require((stage == Stage.ACTIVE_RETRY || stage == Stage.RESTART_ACTIVE_RETRY)
                        && value instanceof RuntimeAdmissionResult.CooldownRejected rejected
                        && rejected.reason() == CooldownRejectionReason.ACTIVE && rejections++ == 0
                        && constructed.work == null && !slot.instances.containsKey(constructed.id), "EXACT_ACTIVE_REJECTION");
            }
            constructed = null;
        } catch (Exception | LinkageError problem) { restoreDurability(problem); failScalar(problem); }
    }
    public static void transferred(Object value, Object disposition) {
        if (!selected() || failure != null || P11CooldownFaultProbe.selected()) return;
        try {
            if (P11CooldownDualProbe.selected()) { P11CooldownDualProbe.transferred(value, disposition); return; }
            if (P11CooldownDurabilityProbe.selected()) { P11CooldownDurabilityProbe.transferred(value, disposition); return; }
            require(server.isSameThread() && transfers < 2 && admissions == transfers + 1
                    && disposition == RuntimePermitTransferDisposition.TRANSFERRED && value instanceof P9StarterProjectile,
                    "ORIGINAL_TRANSFER_RETURN");
            var projectile = (P9StarterProjectile) value; var instance = instances[transfers];
            var permit = instance.activeProjectileContinuation; var active = entry();
            require(permit != null && permit.state == RuntimeProjectileContinuationPermit.State.OPEN
                    && projectile.hasContinuationPermitIdentity(permit)
                    && projectile.isAddedToLevel() && !projectile.isRemoved() && projectile.getOwner() == actor
                    && active.kind == 0 && active.duration == duration()
                    && active.expiresAt == active.releasedAt + duration() && active.releasedAt == gameTime()
                    && slot.runtimeTick < admittedRoots[transfers].deadlineRuntimeTick()
                    && instance.cooldownReceipt.fact() == P11CastCooldownService.ReleaseFact.ARM
                    && instance.cooldownReceipt.releasedAt() == active.releasedAt
                    && instance.cooldownReceipt.attemptId().equals(active.attemptId), "ACTUAL_OPEN_ARM_ATTACHMENT");
            releaseRuntimeTicks[transfers] = slot.runtimeTick;
            if (P11CooldownCloneProbe.selected()) {
                P11CooldownCloneProbe.released(transfers, instance, projectile, active);
            }
            projectiles[transfers] = projectile; releases[transfers++] = active;
        } catch (RuntimeException | LinkageError problem) { restoreDurability(problem); failScalar(problem); }
    }
    public static void claimed(Object disposition) {
        if (P11CooldownDualProbe.selected()) { P11CooldownDualProbe.claimed(disposition); return; }
        if (P11CooldownFaultProbe.selected()) { P11CooldownFaultProbe.claimed(disposition); return; }
        if (P11CooldownDurabilityProbe.selected()) { P11CooldownDurabilityProbe.claimed(disposition); return; }
        if (selected() && disposition == RuntimePermitClaimDisposition.QUEUED) claims++;
    }
    public static void rootRetired(MinecraftServer exact) {
        if (!selected() || exact != server || output == null) return;
        try {
            var data = P11NativeStorageBoundary.terminalDiagnostics();
            require(data != null, "ACTUAL_ROOT_TERMINAL");
            var roots = data.nativeResponsibilities().roots();
            require(roots.size() == 5 && roots.stream().map(root -> root.kind()).collect(java.util.stream.Collectors.toSet())
                    .equals(java.util.Set.of("WORK", "NATIVE_CREDIT", "OPERATION", "COMMAND_CONTEXT", "TRANSITION")),
                    "EXACT_ROOT_KIND_INVENTORY");
            var result = new LinkedHashMap<String, Object>();
            boolean expectedFailures = P11CooldownDurabilityProbe.selected()
                    ? durabilityExpectedFailures > 0 && data.failures() == durabilityExpectedFailures : data.failures() == 0;
            result.put("status", failure == null && finishing && sealed && data.nativeStopNormal()
                    && expectedFailures && data.resources().dirtyUuids() == 0 && roots.stream().allMatch(root -> root.count() == 0)
                    ? P11CooldownDurabilityProbe.selected() ? "NORMAL_NATIVE_STOP_WITH_EXPECTED_WRITER_FAILURES_AND_ROOTS_ZERO"
                        : "NORMAL_NATIVE_STOP_AND_ROOTS_ZERO" : "TERMINAL_NOT_QUALIFIED");
            if (P11CooldownDurabilityProbe.selected()) result.put("expectedSourceFailures", durabilityExpectedFailures);
            result.put("nativeStopNormal", data.nativeStopNormal()); result.put("sourceFailures", data.failures());
            result.put("dirtyUuids", data.resources().dirtyUuids()); result.put("originalWrites", data.writes());
            result.put("allRootCounts", roots.stream().map(root -> Map.of("kind", root.kind(), "count", root.count())).toList());
            result.put("harnessFailure", failure == null ? "NONE" : failure);
            P11C4aEvidence.write(output, "data-terminal.json", result);
            P11CooldownCostProbe.seal(output, data);
        } catch (Exception | LinkageError problem) { failScalar(problem); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void stopped(ServerStoppedEvent event) {
        if (!selected() || server != event.getServer() || output == null) return;
        try {
            if (P11CooldownFaultProbe.selected()) P11CooldownFaultProbe.stopped(server);
            if (failure == null && P11CooldownRestartProbe.writeSelected()) P11CooldownRestartProbe.stopped(server);
        } catch (Exception | LinkageError problem) { failScalar(problem); }
        if (P11CooldownCloneProbe.selected()) {
            try { P11CooldownCloneProbe.release(); }
            catch (RuntimeException | Error cleanup) { failScalar(cleanup); }
        }
        if (P11CooldownDurabilityProbe.selected()) {
            try { P11CooldownDurabilityProbe.restoreAfterFailure(null); P11CooldownDurabilityProbe.release(); }
            catch (IOException | RuntimeException | Error cleanup) { failScalar(cleanup); }
        }
        if (P11CooldownDualProbe.selected()) P11CooldownDualProbe.release();
        try { P11C4aEvidence.write(output, "stopped.json", Map.of("status", failure == null && P11CooldownFaultProbe.expectedServerStop()
                ? P11CooldownFaultProbe.expectedNativeFault() ? "ORIGINAL_NATIVE_FAULT_STOP_OBSERVED" : "ORIGINAL_UNARMED_STOP_OBSERVED"
                : failure == null && finishing
                ? "ORIGINAL_SERVER_STOPPED" : "STOP_AFTER_FAILURE", "failure", failure == null ? "NONE" : failure,
                "actualLogoutEvents", logouts, "remainingPlayers", server.getPlayerList().getPlayers().size())); }
        catch (IOException ignored) { }
    }

    private static void progressDual() throws IOException {
        if (stage == Stage.CONNECT) {
            P11CooldownDualProbe.arm(server, actor, peer, slot, foundation, cooldowns, attachments, submissions, output);
            stage = Stage.FIRST_ARM; return;
        }
        if (P11CooldownDualProbe.tick()) {
            finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
        }
    }

    private static void progressFault() throws IOException {
        if (stage == Stage.CONNECT) { arena(); cue("a-starter.ready"); stage = Stage.STARTER; return; }
        if (stage == Stage.STARTER) {
            var equipped = equipped(); if (equipped.isEmpty()) return;
            submit(equipped.orElseThrow());
            P11CooldownFaultProbe.arm(server, actor, peer, reference, slot, foundation, output);
            stage = Stage.FIRST_ARM; cue("a-cast-1.ready"); return;
        }
        if (stage == Stage.FIRST_ARM && P11CooldownFaultProbe.tick()) {
            finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
        }
    }

    private static void progressClone() throws IOException {
        if (stage == Stage.CONNECT) { current(actor); arena(); cue("a-starter.ready"); stage = Stage.STARTER; return; }
        if (stage == Stage.STARTER) {
            current(actor); var equipped = equipped(); if (equipped.isEmpty()) return;
            submit(equipped.orElseThrow()); P11CooldownCloneProbe.arm(server, actor, peer, reference, output);
            stage = Stage.FIRST_ARM; cue("a-cast-1.ready"); return;
        }
        P11CooldownCloneProbe.tick();
        actor = P11CooldownCloneProbe.currentActor();
        if (stage == Stage.FIRST_ARM && P11CooldownCloneProbe.nextCastReady()) {
            current(actor); stage = Stage.SECOND_ARM; cue("a-cast-2.ready"); return;
        }
        if (P11CooldownCloneProbe.complete()) {
            require(transfers == 2 && admissions == 2 && rejections == 0 && claims == 0,
                    "CLONE_TWO_REAL_ARMS_WITHOUT_EXTRA_CAST");
            P11C4aEvidence.write(output, "cooldown-clone-result.json", P11CooldownCloneProbe.result());
            finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
        }
    }

    private static void progressDurability() throws IOException {
        if (stage == Stage.CONNECT) { arena(); cue("a-starter.ready"); stage = Stage.STARTER; return; }
        if (stage == Stage.STARTER) {
            var equipped = equipped(); if (equipped.isEmpty()) return;
            submit(equipped.orElseThrow());
            P11CooldownDurabilityProbe.arm(server, actor, peer, reference, slot, cooldowns, output);
            stage = Stage.FIRST_ARM; cue("a-cast-1.ready"); return;
        }
        if (stage == Stage.FIRST_ARM && P11CooldownDurabilityProbe.tick()) {
            durabilityExpectedFailures = P11CooldownDurabilityProbe.expectedSourceFailures();
            finishing = true; stage = Stage.FINISH; cue("a-finish.ready"); cue("b-finish.ready");
        }
    }

    private static void submit(SkillReference old) throws IOException {
        var canonical = P9StarterSkillContent.canonicalDraft(old.skillId());
        var first = canonical.nodes().getFirst();
        var payload = P9ActiveCastTriggerType.INSTANCE.payloadCodec().codec()
                .encodeStart(JsonOps.INSTANCE, new P9ActiveCastTriggerPayloadV1(duration())).getOrThrow();
        var replacement = new DraftNode(DraftTriggerSlot.present(new DefinitionEnvelope(
                P9StarterSkillContent.ACTIVE_CAST_ID, 1, new Dynamic<>(JsonOps.INSTANCE, payload))),
                first.action(), first.appearanceOverride());
        var draft = new SkillDraft(canonical.draftSchemaVersion(), old.skillId(), Optional.of(old.revision()),
                List.of(replacement, canonical.nodes().get(1)), canonical.appearance());
        applied(attachments.putDraft(actor, draft));
        var outcome = submissions.submit(actor, old.skillId());
        require(outcome instanceof SkillSubmissionCompositionOutcome.Committed, "FORMAL_SUBMIT_COMMITTED");
        reference = ((SkillSubmissionCompositionOutcome.Committed) outcome).reference();
        require(reference.skillId().equals(old.skillId()) && reference.revision().value() > old.revision().value(), "SUCCESSOR_REFERENCE");
        applied(attachments.setEquipped(actor, 0, Optional.of(reference)));
        require(equipped().filter(reference::equals).isPresent(), "FORMAL_EQUIP");
        P11C4aEvidence.write(output, "formal-submission.json", Map.of("status", "FORMAL_V1_SUBMIT_AND_SLOT_ZERO_EQUIP",
                "reference", reference.toString(), "cooldownTicks", duration(), "serverOnlyDraftFixture", true));
    }
    private static void arena() {
        current(actor); current(peer); var level = actor.serverLevel();
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            level.setBlock(new BlockPos(x, 99, z), Blocks.STONE.defaultBlockState(), 3);
        actor.teleportTo(level, .5, 100, .5, java.util.Set.of(), 0, -45);
        peer.teleportTo(level, 2.5, 100, .5, java.util.Set.of(), 90, 0);
    }
    private static Optional<SkillReference> equipped() {
        var value = attachments.equippedAt(actor, 0);
        require(value instanceof PlayerSkillAttachmentService.Available<?>, "EQUIPMENT_AVAILABLE");
        return ((PlayerSkillAttachmentService.Available<Optional<SkillReference>>) value).value();
    }
    private static P11CastCooldownData.Entry entry() {
        var owner = foundation.sourceOwner(server); var body = owner == null ? null : owner.body(actor);
        require(body != null && owner.canCopy(body) && body.cooldown.data.kind == P11CastCooldownData.Kind.ROUTED,
                "EXACT_QUALIFIED_COOLDOWN_SOURCE");
        var entry = body.cooldown.data.entries.get(reference.skillId().value());
        require(entry != null, "EXACT_COOLDOWN_KEY"); return entry;
    }
    private static boolean terminal(int index) {
        var instance = instances[index]; var projectile = projectiles[index];
        return instance != null && projectile != null && instance.work == null && instance.lease.pin.isClosed()
                && instance.activeProjectileContinuation == null && projectile.isRemoved();
    }
    private static void writeRelease(int index) throws IOException {
        var active = releases[index];
        var facts = new LinkedHashMap<String, Object>();
        facts.put("status", "ORIGINAL_NATIVE_ADD_OPEN_AND_ARM"); facts.put("acceptedAt", active.acceptedAt);
        facts.put("releaseNotAfter", active.releaseNotAfter); facts.put("releasedAt", active.releasedAt);
        facts.put("expiresAt", active.expiresAt); facts.put("durationTicks", active.duration);
        facts.put("acceptedRuntimeTick", admittedRoots[index].createdRuntimeTick());
        facts.put("deadlineRuntimeTick", admittedRoots[index].deadlineRuntimeTick());
        facts.put("releaseRuntimeTick", releaseRuntimeTicks[index]);
        facts.put("sameActualReceiptAndAttachment", true); facts.put("nativeActorWitness", true);
        P11C4aEvidence.write(output, "arm-" + (index + 1) + ".json", facts);
    }
    private static void physical(P11CastCooldownData.Entry expected) throws IOException {
        var file = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(actor.getUUID() + ".dat");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file)
                && Files.size(file) <= 32L * 1024 * 1024, "OWNED_PLAYER_FILE_BOUND");
        var nbt = NbtIo.readCompressed(file, NbtAccounter.create(32L * 1024 * 1024));
        require(nbt.hasUUID("UUID") && nbt.getUUID("UUID").equals(actor.getUUID()), "PHYSICAL_PLAYER_ID");
        var material = P11CastCooldownCodec.read(nbt.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY)
                .get(P11CastCooldownAttachments.ID.toString()));
        require(material.kind == P11CastCooldownData.Kind.ROUTED
                && sameRelease(material.entries.get(reference.skillId().value()), expected), "PHYSICAL_SAME_ACTIVE");
    }
    private static boolean sameRelease(P11CastCooldownData.Entry value, P11CastCooldownData.Entry expected) {
        return value != null && value.kind == 0 && value.skillId.equals(expected.skillId)
                && value.revision == expected.revision && value.duration == expected.duration
                && value.acceptedAt == expected.acceptedAt && value.attemptId.equals(expected.attemptId)
                && value.releaseNotAfter == expected.releaseNotAfter && value.releasedAt == expected.releasedAt
                && value.expiresAt == expected.expiresAt;
    }
    private static void applied(PlayerSkillAttachmentService.Result<?> value) {
        require(value instanceof PlayerSkillAttachmentService.Available<?> available
                && available.value() == PlayerSkillAttachmentService.Applied.INSTANCE, "FORMAL_ATTACHMENT_OPERATION");
    }
    private static void current(ServerPlayer value) {
        require(server.isSameThread() && value.getServer() == server && !value.isFakePlayer() && !value.isRemoved()
                && value.isAlive() && value.connection.player == value && value.connection.getConnection().isConnected()
                && value.connection.getConnection().getPacketListener() == value.connection
                && server.getPlayerList().getPlayer(value.getUUID()) == value, "CURRENT_AUTHENTICATED_ACTOR");
    }
    private static long gameTime() { return server.overworld().getGameTime(); }
    private static boolean receipt(String role, String leaf) throws IOException {
        return P11C4aEvidence.receiptPresent(P11C4aEvidence.root().resolve(role), leaf);
    }
    private static void cue(String leaf) throws IOException { P11C4aEvidence.cue(output, leaf); }
    private static void require(boolean value, String code) { P11C4aEvidence.require(value, "COOLDOWN_" + code); }
    private static void failScalar(Throwable problem) {
        if (failure == null) failure = P11C4aEvidence.failureCode(problem);
    }
    private static void fail(Throwable problem) {
        failScalar(problem);
        restoreDurability(problem);
        if (sealed) return;
        sealed = true;
        try { if (output != null) {
            var facts = new LinkedHashMap<String, Object>(Map.of("status", "FAIL", "code", failure,
                    "stage", stage.name(), "ticks", ticks, "admissions", admissions, "transfers", transfers,
                    "rejections", rejections, "claims", claims));
            if (P11CooldownDualProbe.selected()) facts.put("dualAdmissionObservation", P11CooldownDualProbe.admissionObservation());
            P11C4aEvidence.write(output, "failure.json", facts);
        } } catch (IOException ignored) { }
        if (server != null) server.halt(false);
    }
    private static void restoreDurability(Throwable primary) {
        if (!P11CooldownDurabilityProbe.selected()) return;
        try { P11CooldownDurabilityProbe.restoreAfterFailure(primary); }
        catch (IOException | RuntimeException | Error cleanup) { failScalar(cleanup); }
    }
}
