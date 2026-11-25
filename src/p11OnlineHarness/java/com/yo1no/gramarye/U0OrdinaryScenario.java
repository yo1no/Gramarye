package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.runtime.mana.U0OrdinaryManaObservation;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;

/** Excluded fixed fixture. No normal ingress/sender, projectile creation, receipt or cooldown setter. */
public final class U0OrdinaryScenario {
    private static final List<String> CHECKPOINTS = List.of("a-hit", "a-held", "a-active-retry", "b-hit",
            "expiry-before", "expiry-attempt", "capacity-first", "capacity-retry", "pre-save", "post-save", "pre-stop");
    private static final int TOOL_TICK_LIMIT = 36000;
    private static final int MAX_AUTOMATIC_SNAPSHOTS = 64;
    private static final List<Map<String, Object>> AUTOMATIC = new ArrayList<>();
    private static final String[] LAST_TYPED = new String[2];
    private static boolean automaticTruncated, stopWritten;
    private static int arenaFloor;
    private static final ServerPlayer[] ACTORS = new ServerPlayer[2];
    private static final Pig[] TARGETS = new Pig[2];
    private static final List<Map<String, Object>> SAMPLES = new ArrayList<>();
    private static MinecraftServer server;
    private static Path output;
    private static int admitted, checkpoint, startTick, lastTick = -1;
    private static boolean setup, air, written;
    private static V0SlotRuntime capturedSlot;
    private static V0SlotStartup capturedStartup;
    private static V0SlotBinding capturedBinding;
    private static NormalSpellPolicy observedPolicy;
    private static V0BootstrapRetirementCoordinator retirement;
    private static final List<Map<String, Object>> SUNSET = new ArrayList<>();
    private static int sunsetStage, sunsetActor;
    private static boolean sunsetPending;
    private static final List<Map<String, Object>> ACTIVE_SAVE = new ArrayList<>();
    private static String activeSaveMode;
    private static boolean activeSaveIssued;
    private static final List<ArmorStand> DENSITY_FILLERS = new ArrayList<>();
    private static String densityMode = "NONE";
    private static int densityStage;
    private static final List<Map<String, Object>> LIFECYCLE = new ArrayList<>();
    private static boolean lifecycleRequested, lifecycleWritten;
    private static int lifecycleStage, lifecycleActor = -1;
    private static ServerPlayer lifecycleOriginal;
    private static ServerGamePacketListenerImpl lifecycleListener;
    private static Connection lifecycleConnection;
    private static P11QualifiedSourceOwner lifecycleSource;
    private static P11QualifiedSourceOwner.Body lifecycleOriginalBody;
    private static long lifecycleOldExpiry = -1;
    private static CompletableFuture<Void> lifecycleReload;

    private U0OrdinaryScenario() { }

    /** Borrow only the original constructor tuple; it is unusable until root publication is checked. */
    public static void captureRuntime(Object suppliedSlot, Object suppliedStartup, Object suppliedBinding) {
        if (!"u0-ordinary-ab".equals(System.getProperty("gramarye.p11.online.case", ""))) return;
        require(suppliedSlot instanceof V0SlotRuntime && suppliedStartup instanceof V0SlotStartup
                && suppliedBinding instanceof V0SlotBinding, "CONSTRUCTOR_TUPLE");
        var slot = (V0SlotRuntime) suppliedSlot; var startup = (V0SlotStartup) suppliedStartup;
        var binding = (V0SlotBinding) suppliedBinding;
        require(startup.profile() == V0StartupProfile.Kind.NORMAL && binding.normalPolicy()
                && slot.hasAuthority(startup.authority()) && startup.server().isSameThread()
                && capturedSlot == null, "NORMAL_CONSTRUCTOR_OWNER");
        var policy = binding.activationPolicy();
        require(policy != null, "ACTUAL_NORMAL_POLICY");
        capturedSlot = slot; capturedStartup = startup; capturedBinding = binding; observedPolicy = policy;
    }

    /** Sole caller is the original hasJoined + exact encrypted PLAY successful callback, no supplied boolean. */
    public static void authenticatedPlay(MinecraftServer exact, ServerPlayer actor) {
        require(current(exact, actor), "AUTH_PLAY_TUPLE");
        if (server == null) { server = exact; startTick = exact.getTickCount(); }
        require(server == exact, "EXACT_SERVER");
        U0OrdinaryLogoutScenario.authenticatedPlay(exact, actor);
        for (int i = 0; i < admitted; i++) {
            if (ACTORS[i].getUUID().equals(actor.getUUID())) { ACTORS[i] = actor; return; }
        }
        require(admitted < 2, "TWO_ACTOR_BOUND");
        // This is arrival order only, not a host-supplied account identity or a/b authority.
        ACTORS[admitted++] = actor;
    }

    /** Existing ServerTick Post caller invokes outside every observation catch. */
    public static void tick(MinecraftServer exact) {
        if (server == null) return;
        require(exact == server && exact.isSameThread(), "SERVER_THREAD");
        if (lastTick == exact.getTickCount()) return;
        lastTick = exact.getTickCount();
        require(lastTick >= startTick && lastTick - startTick <= TOOL_TICK_LIMIT, "FIXED_SCENE_BOUND");
        if (admitted != 2) return;
        if (U0OrdinaryLogoutScenario.active()) {
            try { U0OrdinaryLogoutScenario.tick(exact); } catch (IOException failure) { throw new UncheckedIOException(failure); }
            return;
        }
        if (lifecycleStage >= 2) {
            try { lifecycleTick(); } catch (IOException failure) { throw new UncheckedIOException(failure); }
            return;
        }
        for (var actor : ACTORS) {
            require(current(exact, actor), "CURRENT_PLAY");
            var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            if (body == null || !source.canCopy(body)) return;
        }
        try {
            if (!setup) {
                if (observedPolicy != null && !observedPolicy.bootstrap()) {
                    // Wait only for original vanilla streaming, before any fixture mutation or input-ready.
                    var level = exact.overworld(); var spawn = level.getSharedSpawnPos();
                    int chunkX = Math.floorDiv(spawn.getX(), 16), chunkZ = Math.floorDiv(spawn.getZ(), 16);
                    if (P9V0LoadedBlocks.completedChunk(level, chunkX, chunkZ) == null
                            || P9V0LoadedBlocks.completedChunk(level, chunkX, chunkZ + 1) == null) return;
                }
                output = Files.createDirectory(P11C4aEvidence.root().resolve("scenario"));
                prepareArena(); // Real native fixture control: never inside an observer catch.
                require(observedPolicy != null, "CAPTURED_POLICY_BEFORE_INPUT_READY");
                if (!observedPolicy.bootstrap()) {
                    require(!P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "density-64.ready")
                            && !P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "density-65.ready"), "FUTURE_NO_DENSITY_PROFILE");
                }
                prepareDensity(); // Fixed fresh-world setup before normal-input-ready or any requested R.
                U0OrdinaryLogoutScenario.prepare(exact, ACTORS, TARGETS, output);
                setup = true;
                sample("setup", 0);
                if (!observedPolicy.bootstrap()) {
                    String selected = com.yo1no.gramarye.magic.definition.scoped.U021FutureCatalogUpdate.targetOwnerLabel();
                    boolean selectedCurrent = false;
                    for (var actor : ACTORS) { selectedCurrent |= P11C4aEvidence.pseudonym(actor.getUUID()).equals(selected); }
                    require(selected.matches("[0-9a-f]{64}") && selectedCurrent, "FUTURE_SELECTED_ACTUAL_ACTOR");
                    P11C4aEvidence.write(output, "future-policy-ready.json", Map.of("format", "U0_FUTURE_POLICY_READY_V1",
                            "status", "ACTUAL_SERVER_BINDING_POLICY_AND_AUTH_OWNER_NOT_CAST_ACCEPTANCE",
                            "policyKey", observedPolicy.key().policyName().toString(), "bootstrap", false,
                            "manaFee", observedPolicy.manaFee(), "cooldownTicks", observedPolicy.cooldownTicks(),
                            "updateOwnerLabel", selected));
                }
                P11C4aEvidence.cue(output, "normal-input-ready.ready");
            }
            if (U0OrdinaryLogoutScenario.active()) { U0OrdinaryLogoutScenario.tick(exact); return; }
            automaticSample();
            if (observedPolicy.bootstrap()) {
                if (activeSaveStep()) return;
            } else {
                require(!cue("active-save.ready") && !cue("active-save-stop.ready")
                        && !cue("lifecycle-death.ready") && !cue("sunset-begin.ready"), "FUTURE_EXCLUDES_BOOTSTRAP_CONTROL_CUES");
            }
            // Fixed zero-byte operation cues acknowledge UI/callback work, never prove it happened.
            if (!air && cue("air-lanes.ready")) {
                for (int i = 0; i < 2; i++) {
                    var actor = ACTORS[i];
                    actor.teleportTo(actor.serverLevel(), actor.getX(), actor.getY(), actor.getZ(), Set.of(), 0, -30);
                    TARGETS[i].teleportTo(TARGETS[i].getX(), TARGETS[i].getY(), TARGETS[i].getZ() + 5);
                }
                air = true;
            }
            if (checkpoint < CHECKPOINTS.size() && cue("checkpoint-" + (checkpoint + 1) + ".ready")) {
                int next = checkpoint + 1;
                String name = CHECKPOINTS.get(checkpoint);
                if (name.equals("post-save")) exact.getPlayerList().saveAll(); // Original native owner, no alternative writer.
                sample(name, next);
                checkpoint = next;
            }
            if (observedPolicy.bootstrap()) sunsetStep();
            if (!written && cue("finish.ready")) {
                flushScene("FINISH");
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** Flush only already-captured closed scalars before the original server trace terminal. */
    public static void flushStopped(MinecraftServer exact) {
        if (server != null) require(exact == server, "STOP_FLUSH_OWNER");
        if (capturedStartup != null) require(capturedStartup.server() == exact, "STOP_CAPTURE_OWNER");
        try { U0OrdinaryLogoutScenario.flushStopped(exact); flushLifecycle("STOPPED_PARTIAL_OR_COMPLETE_PREFIX"); flushScene("STOP"); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    public static void stopped(MinecraftServer exact) {
        if (server != null) require(exact == server, "STOP_OWNER");
        if (capturedStartup != null && capturedStartup.server() == exact) { U021FutureCompletionBridge.stopped(capturedStartup.authority()); }
        // The original server lifecycle closes entities. Do not discard a product projectile to make zero.
        for (int i = 0; i < 2; i++) { ACTORS[i] = null; TARGETS[i] = null; }
        server = null; output = null;
        capturedSlot = null; capturedStartup = null; capturedBinding = null; observedPolicy = null; retirement = null;
        SAMPLES.clear(); SUNSET.clear(); AUTOMATIC.clear();
        LAST_TYPED[0] = null; LAST_TYPED[1] = null;
        automaticTruncated = false; stopWritten = false; arenaFloor = 0;
        admitted = 0; checkpoint = 0; startTick = 0; lastTick = -1;
        setup = false; air = false; written = false;
        sunsetStage = 0; sunsetActor = 0; sunsetPending = false;
        ACTIVE_SAVE.clear(); activeSaveMode = null; activeSaveIssued = false;
        DENSITY_FILLERS.clear(); densityMode = "NONE"; densityStage = 0;
        LIFECYCLE.clear(); lifecycleRequested = false; lifecycleWritten = false;
        lifecycleStage = 0; lifecycleActor = -1; lifecycleOriginal = null; lifecycleListener = null;
        lifecycleConnection = null; lifecycleSource = null; lifecycleOriginalBody = null;
        lifecycleOldExpiry = -1; lifecycleReload = null;
        U0OrdinaryLogoutScenario.stopped();
    }

    /** One pre-armed native save/readback, separate from the manual checkpoint document. */
    private static boolean activeSaveStep() throws IOException {
        if (activeSaveIssued) return false;
        boolean save = cue("active-save.ready"), stop = cue("active-save-stop.ready");
        require(!(save && stop), "ACTIVE_SAVE_CUES_EXCLUSIVE");
        long now = server.overworld().getGameTime();
        if (activeSaveMode == null) {
            if (!save && !stop) return false;
            require(firstActiveActor(now) < 0, "ACTIVE_SAVE_CUE_BEFORE_RELEASE");
            activeSaveMode = stop ? "ACTIVE_SAVE_STOP" : "ACTIVE_SAVE";
            lifecycleRequested = cue("lifecycle-death.ready");
            require(!lifecycleRequested || save && !stop && densityMode.equals("NONE"), "LIFECYCLE_EXCLUSIVE_PROFILE");
            if (lifecycleRequested) lifecycleStage = 1;
            ACTIVE_SAVE.add(activeSaveObservation("ARMED", 2000, -1, "NOT_REQUESTED", -1, -1));
            P11C4aEvidence.write(output, "active-save-armed.json", ACTIVE_SAVE.getFirst());
            return false;
        }
        require(activeSaveMode.equals("ACTIVE_SAVE_STOP") ? !save : !stop, "ACTIVE_SAVE_MODE_UNCHANGED");
        int selected = firstActiveActor(now);
        if (selected < 0) return false;
        var actor = ACTORS[selected]; var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && source.canCopy(body), "ACTIVE_SAVE_BODY");
        activeSaveIssued = true; // Set before observation/native calls; even a primary Error cannot retry.
        ACTIVE_SAVE.add(activeSaveObservation("BEFORE_SAVE", 2001, selected, "NOT_REQUESTED", -1, -1));
        P11C4aEvidence.write(output, "active-save-before.json", ACTIVE_SAVE.getLast());
        server.getPlayerList().saveAll(); // Original native writer once; no alternative store or setter.
        require(source.body(actor) == body && source.canCopy(body), "ACTIVE_SAVE_WRITTEN_BODY");
        var writtenSource = body.source; // This actual writer publication only; never rebase a refused read.
        // Sole additional qualification caller. The peer has observations, not a primary-readback claim.
        boolean readback = source.prepareCooldownRetirementReadback(body, writtenSource);
        boolean verified = readback && source.body(actor) == body && body.source == writtenSource && source.canCopy(body);
        ACTIVE_SAVE.add(activeSaveObservation("AFTER_READBACK", 2002, selected,
                verified ? "VERIFIED" : "UNPROVED", writtenSource.epoch(), writtenSource.version()));
        P11C4aEvidence.write(output, "active-save.json", Map.of("format", "U0_ORDINARY_ACTIVE_SAVE_V1",
                "status", verified ? "PRIMARY_READBACK_VERIFIED_NOT_U0_ACCEPTANCE" : "PRIMARY_READBACK_UNPROVED",
                "mode", activeSaveMode, "selectedAuthOrder", selected + 1, "nativeSaveIssued", true,
                "primaryReadbackReturned", readback, "sourceStillCurrent", body.source == writtenSource,
                "observations", List.copyOf(ACTIVE_SAVE)));
        if (verified && lifecycleRequested) {
            beginNativeLifecycle(selected);
            return true;
        }
        if (verified && activeSaveMode.equals("ACTIVE_SAVE_STOP")) {
            server.halt(false); // Original normal stop, not a product deadline extension or cleanup permit.
            return true;
        }
        return false;
    }

    private static int firstActiveActor(long now) {
        for (int i = 0; i < 2; i++) {
            var actor = ACTORS[i]; var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            require(body != null && source.canCopy(body) && body.cooldown != null, "ACTIVE_SAVE_CURRENT_SOURCE");
            var data = body.cooldown.data;
            var entry = data == null ? null : data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
            if (entry != null && data.kind == P11CastCooldownData.Kind.ROUTED && entry.kind == 0
                    && entry.duration == 20 && now < entry.expiresAt) return i;
        }
        return -1;
    }

    private static Map<String, Object> activeSaveObservation(String phase, int number, int selected,
            String readback, long writtenEpoch, long writtenVersion) {
        var actual = snapshot("active-save-" + phase.toLowerCase(java.util.Locale.ROOT).replace('_', '-'), number);
        return Map.of("format", "U0_ORDINARY_ACTIVE_SAVE_OBSERVATION_V1", "phase", phase,
                "mode", activeSaveMode, "actual", actual, "selectedAuthOrder", selected + 1,
                "selectedPrimaryReadback", readback, "peerPrimaryReadback", "NOT_REQUESTED",
                "writtenEpoch", writtenEpoch, "writtenVersion", writtenVersion,
                "manaRole", "EXISTING_MANA_TRACE_JOIN_BY_CHECKPOINT_AND_AUTH_ORDER");
    }

    /** Actual existing respawn event only; never an authentication replacement or a supplied success flag. */
    static void observedLifecycleRespawn(MinecraftServer exact, ServerPlayer replacement, boolean endConquered) {
        if (!lifecycleRequested || lifecycleOriginal == null
                || !replacement.getUUID().equals(lifecycleOriginal.getUUID())) return;
        require(lifecycleStage == 2 && exact == server && exact.isSameThread() && !endConquered
                && replacement != lifecycleOriginal && replacement.connection == lifecycleListener
                && replacement.connection.getConnection() == lifecycleConnection
                && replacement.connection.player == lifecycleOriginal
                && lifecycleConnection.getPacketListener() == replacement.connection,
                "LIFECYCLE_ACTUAL_RESPAWN_EVENT");
        ACTORS[lifecycleActor] = replacement;
        lifecycleStage = 3;
        U0OrdinaryTrace.note("SERVER_NATIVE_RESPAWN", 0, 0, 0, 0, 0, 0, 1, 1, 0,
                P11C4aEvidence.pseudonym(replacement.getUUID()));
    }

    private static void beginNativeLifecycle(int selected) throws IOException {
        require(lifecycleStage == 1 && lifecycleRequested && current(server, ACTORS[selected]), "LIFECYCLE_SELECTED_CURRENT");
        lifecycleActor = selected; lifecycleOriginal = ACTORS[selected];
        lifecycleSource = P11NativeStorageBoundary.nativeSourceOwner(lifecycleOriginal);
        lifecycleOriginalBody = lifecycleSource == null ? null : lifecycleSource.body(lifecycleOriginal);
        require(lifecycleOriginalBody != null && lifecycleSource.canCopy(lifecycleOriginalBody), "LIFECYCLE_ORIGINAL_BODY");
        var entry = lifecycleOriginalBody.cooldown.data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
        require(entry != null && entry.kind == 0 && entry.duration == 20, "LIFECYCLE_ACTUAL_ACTIVE20");
        lifecycleOldExpiry = entry.expiresAt;
        lifecycleListener = lifecycleOriginal.connection;
        lifecycleConnection = lifecycleListener.getConnection();
        lifecycleStage = 2; // Before original kill or evidence; a primary cannot issue another death.
        lifecycleRecord("BEFORE_NATIVE_DEATH");
        P11C4aEvidence.write(output, "lifecycle-selected.json", Map.of("format", "U0_LIFECYCLE_SELECTED_V1",
                "pseudonym", P11C4aEvidence.pseudonym(lifecycleOriginal.getUUID())));
        lifecycleOriginal.kill(); // Native operation outside every observer catch, exactly once.
        lifecycleRecord("NATIVE_DEATH_RETURN");
        if (!lifecycleOriginal.isDeadOrDying()) {
            lifecycleStage = 8;
            finishNativeLifecycle("DEATH_NOT_COMMITTED_REASON_UNOBSERVED");
            return;
        }
        P11C4aEvidence.cue(output, "lifecycle-respawn.ready");
    }

    /** Only the selected expected-dead actor bypasses the fixture's alive/current assertion. */
    private static void lifecycleTick() throws IOException {
        require(lifecycleActor >= 0 && lifecycleActor < 2 && current(server, ACTORS[1 - lifecycleActor]),
                "LIFECYCLE_PEER_STILL_CURRENT");
        var peer = ACTORS[1 - lifecycleActor];
        var peerSource = P11NativeStorageBoundary.nativeSourceOwner(peer);
        var peerBody = peerSource == null ? null : peerSource.body(peer);
        if (peerBody == null || !peerSource.canCopy(peerBody)) return;
        var actor = ACTORS[lifecycleActor];
        if (lifecycleStage == 2) {
            require(actor == lifecycleOriginal && lifecycleOriginal.isDeadOrDying()
                    && lifecycleOriginal.connection == lifecycleListener
                    && lifecycleConnection.isConnected(), "LIFECYCLE_EXPECTED_DEAD_ORIGINAL");
            return; // Original GUI/TRY/native respawn event must supply B; no forced replacement.
        }
        if (lifecycleStage == 3) {
            if (!current(server, actor)) return;
            var body = lifecycleSource.body(actor);
            if (body == null || !lifecycleSource.canCopy(body)) return;
            require(actor != lifecycleOriginal && actor.getUUID().equals(lifecycleOriginal.getUUID())
                    && actor.connection == lifecycleListener && actor.connection.getConnection() == lifecycleConnection,
                    "LIFECYCLE_TRUE_SUCCESSOR_CURRENT");
            lifecycleRecord("NATIVE_RESPAWN_CURRENT");
            lifecycleStage = 4; // Before original dimension call; no retry if native call throws.
            var from = actor.serverLevel();
            var target = server.getLevel(from.dimension() == Level.NETHER ? Level.OVERWORLD : Level.NETHER);
            require(target != null && target != from, "LIFECYCLE_NATIVE_DIMENSION_DESTINATION");
            lifecycleRecord("BEFORE_NATIVE_DIMENSION");
            var returned = actor.changeDimension(new DimensionTransition(target, actor, DimensionTransition.PLACE_PORTAL_TICKET));
            LIFECYCLE.add(Map.of("phase", "NATIVE_DIMENSION_RETURN", "nativeTick", server.getTickCount(),
                    "returnedNull", returned == null, "returnedSameNativeActor", returned == actor,
                    "actualLevelChanged", actor.serverLevel() != from,
                    "actualLevelIsRequested", actor.serverLevel() == target,
                    "classification", returned == actor && actor.serverLevel() == target
                            ? "NATIVE_COMMIT_RETURN" : "NATIVE_REFUSAL_OR_UNOBSERVED_COMMIT_NOT_EVENT_CANCEL"));
            lifecycleStage = 5;
            return;
        }
        if (lifecycleStage == 5) {
            if (!current(server, actor)) return;
            var body = lifecycleSource.body(actor);
            if (body == null || !lifecycleSource.canCopy(body)) return;
            lifecycleRecord("AFTER_DIMENSION_CURRENT");
            lifecycleStage = 6;
            lifecycleReload = server.reloadResources(List.copyOf(server.getPackRepository().getSelectedIds()));
            return;
        }
        if (lifecycleStage == 6) {
            if (lifecycleReload == null || !lifecycleReload.isDone()) return;
            lifecycleStage = 7; // A failed original join cannot be retried or relabeled a successful reload.
            lifecycleReload.join(); // Only after original completion; never blocks the server on an incomplete future.
            lifecycleReload = null;
            lifecycleRecord("ORIGINAL_RELOAD_COMPLETED");
            lifecycleStage = 8;
            finishNativeLifecycle("NATIVE_SEQUENCE_RETURNED_NOT_U0_ACCEPTANCE");
        }
    }

    private static void lifecycleRecord(String phase) throws IOException {
        require(LIFECYCLE.size() < 12, "LIFECYCLE_SCALAR_RECORD_BOUND");
        var actors = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 2; i++) {
            var actor = ACTORS[i];
            var source = i == lifecycleActor ? lifecycleSource : P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            var data = body == null || body.cooldown == null ? null : body.cooldown.data;
            var entry = data == null ? null : data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
            U0OrdinaryManaObservation.recordExisting(actor, 4000 + LIFECYCLE.size(), i + 1);
            var row = new LinkedHashMap<String, Object>();
            row.put("authOrder", i + 1); row.put("pseudonym", P11C4aEvidence.pseudonym(actor.getUUID()));
            row.put("nativeCurrent", current(server, actor)); row.put("alive", actor.isAlive());
            row.put("removed", actor.isRemoved()); row.put("bodyPresent", body != null);
            row.put("bodyCopyCurrent", body != null && source.canCopy(body));
            row.put("bodyComplete", body != null && body.complete);
            row.put("loadObserved", body != null && body.loadObserved);
            row.put("sourceInput", body == null ? "UNKNOWN" : body.inputKind.name());
            row.put("sourceFault", body == null ? "UNAVAILABLE" : body.fault.name());
            row.put("epoch", body == null ? -1L : body.source.epoch());
            row.put("version", body == null ? -1L : body.source.version());
            row.put("materialKind", data == null ? "ABSENT" : data.kind.name());
            row.put("schemaVersion", data == null ? 0 : data.schemaVersion);
            row.put("clockFloor", data == null ? -1L : data.clockFloor);
            row.put("entry", entry == null ? Map.of("present", false) : Map.of("present", true,
                    "kind", entry.kind, "duration", entry.duration, "acceptedAt", entry.acceptedAt,
                    "releasedAt", entry.releasedAt, "expiresAt", entry.expiresAt,
                    "attemptMost", entry.attemptId == null ? 0L : entry.attemptId.getMostSignificantBits(),
                    "attemptLeast", entry.attemptId == null ? 0L : entry.attemptId.getLeastSignificantBits(),
                    "releaseWindow", entry.releaseWindow, "releaseNotAfter", entry.releaseNotAfter));
            if (i == lifecycleActor) {
                row.put("differentNativeBodyFromA", actor != lifecycleOriginal);
                row.put("sameOriginalUUID", actor.getUUID().equals(lifecycleOriginal.getUUID()));
                row.put("sameOriginalListener", actor.connection == lifecycleListener);
                row.put("sameOriginalConnection", actor.connection.getConnection() == lifecycleConnection);
                row.put("sameOriginalP11Body", body == lifecycleOriginalBody);
            }
            actors.add(Map.copyOf(row));
        }
        var actual = Map.of("phase", phase, "nativeTick", server.getTickCount(),
                "gameTime", server.overworld().getGameTime(), "selectedAuthOrder", lifecycleActor + 1,
                "oldExpiryUnchanged", lifecycleOldExpiry, "actualClockAtOrAfterOldExpiry",
                server.overworld().getGameTime() >= lifecycleOldExpiry, "actors", List.copyOf(actors));
        LIFECYCLE.add(actual);
        P11C4aEvidence.write(output, "lifecycle-" + LIFECYCLE.size() + ".json", actual);
    }

    private static void finishNativeLifecycle(String outcome) throws IOException {
        flushLifecycle(outcome);
        var serverOutput = P11C4aEvidence.root().resolve("server");
        for (String role : List.of("a", "b")) for (String suffix : List.of("-finish.ready", "-exit-game.ready")) {
            if (!P11C4aEvidence.cuePresent(serverOutput, role + suffix)) P11C4aEvidence.cue(serverOutput, role + suffix);
        }
        server.halt(false); // Original stop; actual normal SDK/process exit remains separate evidence.
    }

    private static void flushLifecycle(String outcome) throws IOException {
        if (!lifecycleRequested || lifecycleWritten || output == null) return;
        lifecycleWritten = true;
        P11C4aEvidence.write(output, "lifecycle-result.json", Map.of("format", "U0_NATIVE_LIFECYCLE_COMPONENT_V1",
                "status", "OBSERVATIONS_NOT_U0_ROW_ACCEPTANCE", "outcome", outcome,
                "stage", lifecycleStage, "oldExpiryUnchanged", lifecycleOldExpiry,
                "observations", List.copyOf(LIFECYCLE),
                "eventCancelObserved", false, "firstTryOrRetryProof", "ORIGINAL_P11_PROTOCOL_AND_NATIVE_CALLBACKS_REQUIRED"));
    }

    private static void sunsetStep() throws IOException {
        if (sunsetStage == 0) {
            if (!cue("sunset-begin.ready")) return;
            require(capturedStartup != null
                    && capturedStartup.server() == server && capturedSlot != null && capturedBinding != null
                    && capturedStartup.runtime().v0Active(capturedSlot, server), "PUBLISHED_SUNSET_OWNER");
            retirement = V0BootstrapRetirementCoordinator.begin(capturedStartup.runtime(), capturedSlot,
                    capturedBinding, capturedStartup.authority(), server).orElseThrow(
                            () -> new IllegalStateException("U0_SCENE_SUNSET_BARRIER_REFUSED"));
            SUNSET.add(Map.of("phase", "BARRIER_STARTED", "nativeTick", server.getTickCount()));
            sunsetStage = 1;
        }
        if (sunsetStage == 1) {
            if (!capturedSlot.bootstrapDrainedForSunset(capturedStartup.authority(), capturedBinding)) return;
            removeDensityAfterDrain();
            SUNSET.add(Map.of("phase", "ORIGINAL_DRAINED", "nativeTick", server.getTickCount()));
            sunsetStage = 2;
        }
        if (sunsetStage == 2) {
            if (sunsetPending) {
                var result = retirement.takeCompletion().orElse(null);
                if (result == null) return;
                require(result.owner().value().equals(ACTORS[sunsetActor].getUUID()) && result.receipt() != null,
                        "ACTUAL_PLAYER_CLEANUP_RECEIPT");
                SUNSET.add(Map.of("phase", "PLAYER_COMPLETION", "authOrder", sunsetActor + 1,
                        "pseudonym", P11C4aEvidence.pseudonym(ACTORS[sunsetActor].getUUID()),
                        "result", result.result().name(), "ticket", result.ticket(), "nativeTick", server.getTickCount()));
                sunsetPending = false;
                sunsetActor++;
            }
            if (sunsetActor < 2) {
                // The read-only retirement producer needs an actual current native write, not an old clean bit.
                server.getPlayerList().saveAll();
                var actor = ACTORS[sunsetActor]; var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
                var body = source == null ? null : source.body(actor);
                require(body != null && retirement.requestPlayerCleanup(source, body), "PLAYER_RETIREMENT_READBACK_UNAVAILABLE");
                sunsetPending = true;
                return;
            }
            require(retirement.requestRosterFinalization(), "ROSTER_REQUEST_REFUSED");
            sunsetStage = 3;
            return;
        }
        if (sunsetStage == 3) {
            var result = retirement.takeCompletion().orElse(null);
            if (result == null) return;
            require(result.roster() != null, "ACTUAL_FULL_ROSTER_PROOF");
            SUNSET.add(Map.of("phase", "ROSTER_FINALIZED", "nativeTick", server.getTickCount(), "ticket", result.ticket()));
            require(retirement.requestSeedCleanup(), "SEED_REQUEST_REFUSED");
            sunsetStage = 4;
            return;
        }
        if (sunsetStage == 4) {
            var result = retirement.takeCompletion().orElse(null);
            if (result == null) return;
            require(result.owner().equals(V0BootstrapRetirementCoordinator.SEED_OWNER), "SEED_OWNER");
            SUNSET.add(Map.of("phase", "SEED_COMPLETION", "result", result.result().name(),
                    "nativeTick", server.getTickCount(), "ticket", result.ticket()));
            P11C4aEvidence.write(output, "sunset.json", Map.of("format", "U0_QUALIFICATION_SUNSET_V1",
                    "status", "ACTUAL_COORDINATOR_OUTCOMES_NOT_FUTURE_EDITOR_COMPLETION", "steps", List.copyOf(SUNSET)));
            P11C4aEvidence.cue(output, "sunset-finished.ready");
            sunsetStage = 5;
        }
    }

    /** One initial fixed profile, never an adaptive raw counter or a reversible phase switch. */
    private static void prepareDensity() throws IOException {
        require(densityStage == 0 && DENSITY_FILLERS.isEmpty(), "DENSITY_INITIAL_SETUP_ONLY");
        boolean at64 = P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "density-64.ready");
        boolean at65 = P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "density-65.ready");
        require(!(at64 && at65), "DENSITY_EXCLUSIVE_PROFILE");
        densityMode = at64 ? "RAW64" : at65 ? "RAW65" : "NONE";
        densityStage = 1; // Before native factory/load/insertion or output; no retry after a primary.
        int count = at64 ? 59 : at65 ? 60 : 0;
        var level = server.overworld(); var spawn = level.getSharedSpawnPos();
        int x0 = Math.floorDiv(spawn.getX(), 16) * 16, z0 = Math.floorDiv(spawn.getZ(), 16) * 16;
        var at = new BlockPos(x0 + 8, arenaFloor + 1, z0 + 8);
        require(count == 0 || level.hasChunkAt(at), "DENSITY_ALREADY_LOADED");
        for (int i = 0; i < count; i++) {
            var marker = EntityType.ARMOR_STAND.create(level);
            require(marker != null, "DENSITY_VANILLA_FACTORY");
            var nativeState = marker.saveWithoutId(new CompoundTag());
            nativeState.putBoolean("Marker", true);
            marker.readAdditionalSaveData(nativeState); // Public vanilla path; private setMarker is not called here.
            marker.setInvisible(true); marker.setNoGravity(true);
            marker.setPos(x0 + 8.5, arenaFloor + 1, z0 + 8.5);
            DENSITY_FILLERS.add(marker); // Own references only, retained even if original insertion fails.
            require(level.addFreshEntity(marker), "DENSITY_NATIVE_INSERTION");
        }
        P11C4aEvidence.write(output, "density-setup.json", Map.of("format", "U0_FIXED_DENSITY_SETUP_V1",
                "status", "FIXED_FILLERS_AND_EXPECTED_RAW_NOT_SCAN_PROOF", "mode", densityMode,
                "fixedFillerCount", count, "expectedRawNotMeasured", at64 ? 64 : at65 ? 65 : -1,
                "openedAfterNativePostTick", server.getTickCount(), "fixtureFloorY", arenaFloor,
                "markers", densityMarkerScalars()));
    }

    /** Sole caller follows the original sealed Bootstrap drain guard, never a timer or terminal guess. */
    private static void removeDensityAfterDrain() throws IOException {
        require(densityStage == 1, "DENSITY_CLEANUP_ONCE_AFTER_SETUP");
        densityStage = 2;
        for (var marker : DENSITY_FILLERS) {
            if (!marker.isRemoved()) {
                require(marker.level() == server.overworld()
                        && server.overworld().getEntity(marker.getId()) == marker, "DENSITY_EXACT_OWN_MARKER");
                marker.discard();
            }
        }
        P11C4aEvidence.write(output, "density-cleanup.json", Map.of("format", "U0_FIXED_DENSITY_CLEANUP_V1",
                "status", "OWN_MARKER_REMOVAL_AFTER_ORIGINAL_BOOTSTRAP_DRAIN", "mode", densityMode,
                "nativeTick", server.getTickCount(), "markers", densityMarkerScalars()));
        DENSITY_FILLERS.clear();
    }

    private static List<Map<String, Object>> densityMarkerScalars() {
        var values = new ArrayList<Map<String, Object>>();
        for (var marker : DENSITY_FILLERS) {
            var box = marker.getBoundingBox();
            values.add(Map.of("nativeId", marker.getId(), "x", marker.getX(), "y", marker.getY(), "z", marker.getZ(),
                    "removed", marker.isRemoved(), "noGravity", marker.isNoGravity(),
                    "nativeMarkerTag", marker.saveWithoutId(new CompoundTag()).getBoolean("Marker"),
                    "boxWidth", box.getXsize(), "boxHeight", box.getYsize()));
        }
        return List.copyOf(values);
    }

    private static void prepareArena() {
        var level = server.overworld(); var spawn = level.getSharedSpawnPos();
        int x0 = Math.floorDiv(spawn.getX(), 16) * 16, z0 = Math.floorDiv(spawn.getZ(), 16) * 16;
        int floor = Math.max(level.getMinBuildHeight() + 68, Math.min(spawn.getY() + 2, level.getMaxBuildHeight() - 6));
        require(floor <= level.getMaxBuildHeight() - 6, "SUPPORTED_ARENA_HEIGHT");
        arenaFloor = floor;
        boolean future = observedPolicy != null && !observedPolicy.bootstrap();
        int maxZ = future ? 23 : 14;
        if (future) {
            for (int x = 1; x <= 14; x++) for (int z = 1; z <= maxZ; z++)
                for (int y = floor; y <= floor + 4; y++) {
                    var pos = new BlockPos(x0 + x, y, z0 + z);
                    require(level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos), "FUTURE_ARENA_BOUNDS");
                }
        }
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= maxZ; z++)
            for (int y = floor; y <= floor + 4; y++) require(level.hasChunkAt(new BlockPos(x0 + x, y, z0 + z)), "LOADED_ARENA");
        for (int x = 1; x <= 14; x++) for (int z = 1; z <= maxZ; z++) {
            level.setBlock(new BlockPos(x0 + x, floor, z0 + z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 1; y <= 4; y++) level.setBlock(new BlockPos(x0 + x, floor + y, z0 + z), Blocks.AIR.defaultBlockState(), 3);
        }
        for (int i = 0; i < 2; i++) {
            double x = x0 + (i == 0 ? 3.5 : 10.5), z = z0 + (future ? 10.5 : 3.5);
            ACTORS[i].teleportTo(level, x, floor + 1, z, Set.of(), 0, future ? 6 : 20);
            var pig = EntityType.PIG.create(level); require(pig != null, "NATIVE_PIG_FACTORY");
            pig.setNoAi(true); pig.setPersistenceRequired(); pig.setPos(x, floor + 1, z + (future ? 11 : 3));
            require(level.addFreshEntity(pig), "NATIVE_TARGET_INSERTION"); TARGETS[i] = pig;
        }
    }

    private static void sample(String name, int number) throws IOException {
        SAMPLES.add(snapshot(name, number));
    }

    private static Map<String, Object> snapshot(String name, int number) {
        var actors = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 2; i++) {
            var actor = ACTORS[i]; var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
            var body = source == null ? null : source.body(actor);
            require(body != null && source.canCopy(body) && body.cooldown != null, "CURRENT_SOURCE");
            U0OrdinaryManaObservation.recordExisting(actor, number, i + 1);
            var row = new LinkedHashMap<String, Object>();
            row.put("authOrder", i + 1); row.put("pseudonym", P11C4aEvidence.pseudonym(actor.getUUID()));
            row.put("epoch", body.source.epoch()); row.put("version", body.source.version());
            row.put("sourceCurrent", body.cooldown.current(actor));
            var data = body.cooldown.data;
            row.put("cooldownKind", data == null ? "ABSENT" : data.kind.name());
            row.put("schemaVersion", data == null ? 0 : data.schemaVersion);
            row.put("clockFloor", data == null ? -1L : data.clockFloor);
            var entry = data == null ? null : data.entries.get(observedPolicy.key());
            row.put("entry", entry == null ? Map.of("present", false) : Map.of("present", true,
                    "kind", entry.kind, "duration", entry.duration, "releasedAt", entry.releasedAt,
                    "expiresAt", entry.expiresAt, "acceptedAt", entry.acceptedAt, "releaseWindow", entry.releaseWindow,
                    "attemptMost", entry.attemptId == null ? 0 : entry.attemptId.getMostSignificantBits(),
                    "attemptLeast", entry.attemptId == null ? 0 : entry.attemptId.getLeastSignificantBits()));
            if (!observedPolicy.bootstrap()) {
                row.put("observedPolicyKey", observedPolicy.key().policyName().toString());
                row.put("observedPolicyBootstrap", false); row.put("observedPolicyCooldownTicks", observedPolicy.cooldownTicks());
                row.put("entryRole", "ACTUAL_FUTURE_POLICY_KEY_NOT_BOOTSTRAP_ABSENCE");
            }
            row.put("targetId", TARGETS[i].getId()); row.put("targetHealth", TARGETS[i].getHealth());
            var eye = actor.getEyePosition(); var look = actor.getLookAngle().normalize();
            row.put("x", actor.getX()); row.put("y", actor.getY()); row.put("z", actor.getZ());
            row.put("eyeY", eye.y); row.put("computedOriginY", eye.y + look.y * .10 - .10);
            row.put("minBuildHeight", actor.level().getMinBuildHeight());
            row.put("maxBuildHeight", actor.level().getMaxBuildHeight());
            row.put("spawnThresholdY", actor.level().getMinBuildHeight() + 64);
            row.put("fixtureFloorY", arenaFloor);
            row.put("poseRole", "CURRENT_FIXTURE_POSE_NOT_CAPTURED_REQUEST_GEOMETRY");
            actors.add(Map.copyOf(row));
        }
        return Map.of("checkpoint", name, "number", number, "nativeTick", server.getTickCount(),
                "gameTime", server.overworld().getGameTime(), "actors", List.copyOf(actors));
    }

    private static boolean typedStateChanged(String[] next, String[] previous) {
        return !java.util.Arrays.equals(next, previous);
    }

    private static boolean automaticSnapshotAvailable(int count) {
        return count >= 0 && count < MAX_AUTOMATIC_SNAPSHOTS;
    }

    /** Intentionally excludes advancing clockFloor/version; it compares exact closed typed state. */
    private static String typedStateKey(int role) {
        var actor = ACTORS[role]; var source = P11NativeStorageBoundary.nativeSourceOwner(actor);
        var body = source == null ? null : source.body(actor);
        require(body != null && source.canCopy(body) && body.cooldown != null, "SAMPLER_SOURCE");
        var data = body.cooldown.data;
        if (data == null) return body.source.epoch() + ":ABSENT";
        var entry = data.entries.get(observedPolicy.key());
        String prefix = body.source.epoch() + ":" + data.schemaVersion + ":" + data.kind.name();
        if (entry == null) return prefix + ":NONE";
        return prefix + ":" + entry.kind + ":" + entry.duration + ":" + entry.acceptedAt
                + ":" + entry.releaseWindow + ":" + entry.releaseNotAfter + ":" + entry.releasedAt
                + ":" + entry.expiresAt + ":" + entry.releaseKnowledge + ":" + entry.uncertainAt
                + ":" + (entry.reason == null ? 0 : entry.reason.code) + ":" + entry.observedAtLeast + ":" + entry.maximum
                + ":" + (entry.attemptId == null ? "NONE" : entry.attemptId.toString());
    }

    private static void automaticSample() throws IOException {
        String[] next = {typedStateKey(0), typedStateKey(1)};
        if (!typedStateChanged(next, LAST_TYPED)) return;
        LAST_TYPED[0] = next[0]; LAST_TYPED[1] = next[1];
        if (!automaticSnapshotAvailable(AUTOMATIC.size())) { automaticTruncated = true; return; }
        int index = AUTOMATIC.size() + 1;
        var value = snapshot("automatic-typed-change", 1000 + index);
        AUTOMATIC.add(value);
        P11C4aEvidence.write(output, "ordinary-change-" + index + ".json", value);
    }

    private static Map<String, Object> sceneDocument(String trigger) {
        var value = new LinkedHashMap<String, Object>();
        value.put("format", observedPolicy != null && !observedPolicy.bootstrap()
                ? "U0_ORDINARY_FUTURE_SCENE_V1" : "U0_ORDINARY_SCENE_V2");
        if (observedPolicy != null && !observedPolicy.bootstrap()) {
            value.put("observedPolicyKey", observedPolicy.key().policyName().toString());
            value.put("observedPolicyBootstrap", false); value.put("observedPolicyCooldownTicks", observedPolicy.cooldownTicks());
            value.put("bootstrapSunsetEnabled", false);
        }
        value.put("status", checkpoint == CHECKPOINTS.size()
                ? "CHECKPOINTS_RECORDED_NOT_U0_ACCEPTANCE" : "PARTIAL_OBSERVATIONS_NOT_U0_ACCEPTANCE");
        value.put("actorCount", admitted); value.put("firstAuthOrderIsNotClientRole", true);
        value.put("toolTickLimit", TOOL_TICK_LIMIT); value.put("flushTrigger", trigger);
        value.put("completionRole", "CHECKPOINT_CUES_PROCESSED_NOT_CASE_ACCEPTANCE");
        value.put("completed", List.copyOf(CHECKPOINTS.subList(0, checkpoint)));
        value.put("pending", List.copyOf(CHECKPOINTS.subList(checkpoint, CHECKPOINTS.size())));
        value.put("samples", List.copyOf(SAMPLES)); value.put("automaticSamples", List.copyOf(AUTOMATIC));
        value.put("automaticTruncated", automaticTruncated);
        value.put("sunsetSteps", List.copyOf(SUNSET)); value.put("sunsetStage", sunsetStage);
        return value;
    }

    private static void flushScene(String trigger) throws IOException {
        boolean stop = "STOP".equals(trigger);
        if (stop ? stopWritten : written) return;
        if (output == null) output = Files.createDirectory(P11C4aEvidence.root().resolve("scenario"));
        P11C4aEvidence.write(output, stop ? "ordinary-scene-stop.json" : "ordinary-scene.json", sceneDocument(trigger));
        if (stop) stopWritten = true; else written = true;
    }
    private static boolean cue(String leaf) throws IOException { return P11C4aEvidence.cuePresent(output, leaf); }
    private static boolean current(MinecraftServer exact, ServerPlayer actor) {
        if (exact == null || actor == null || !exact.isSameThread() || actor.getServer() != exact || !exact.usesAuthentication()
                || !exact.isDedicatedServer() || exact.getPlayerList().getPlayer(actor.getUUID()) != actor
                || actor.isFakePlayer() || actor.hasDisconnected() || !actor.isAlive() || actor.isRemoved()) return false;
        var connection = actor.connection.getConnection();
        return !connection.isMemoryConnection() && connection.isEncrypted() && connection.isConnected()
                && connection.getPacketListener() == actor.connection && actor.connection.player == actor;
    }
    private static void require(boolean value, String code) { if (!value) throw new IllegalStateException("U0_SCENE_" + code); }
}
