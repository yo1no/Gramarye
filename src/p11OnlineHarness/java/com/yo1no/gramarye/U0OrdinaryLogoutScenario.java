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
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Qualification-only finite native caller. Observer entry points copy scalars only.
 * Retained native references belong only to this bounded scene controller, never to a DTO/recorder. */
public final class U0OrdinaryLogoutScenario {
    private enum Mode { NORMAL, CLEANUP_FAULT }
    private static Mode mode;
    private static MinecraftServer server;
    private static Path output;
    private static ServerPlayer[] actors;
    private static Pig[] targets;
    private static ServerPlayer original, peer, successor;
    private static ServerGamePacketListenerImpl listener;
    private static Connection connection;
    private static P11QualifiedSourceOwner owner;
    private static P11QualifiedSourceOwner.Body body;
    private static P9StarterProjectile carrier;
    private static final List<Map<String,Object>> facts = new ArrayList<>();
    private static final RuntimeException OWNED_FAULT = new RuntimeException("U0_OWNED_LOGOUT_CLEANUP_FAULT");
    private static int selected = -1, stage, wholeEntries, wholeReturns, proofCalls, leaveFaults;
    private static long openLineage, openBinding, openEntity, newLineage, expires, attemptMost, attemptLeast;
    private static boolean inWhole, proofTrue, wholeNormal, sameFault, expectedCallerCaught;
    private static boolean continuedAsOldA, unexpected, written;
    private U0OrdinaryLogoutScenario() { }

    static void prepare(MinecraftServer exact, ServerPlayer[] supplied, Pig[] targets, Path directory) throws IOException {
        boolean normal = P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "logout-normal.ready");
        boolean fault = P11C4aEvidence.cuePresent(P11C4aEvidence.root(), "logout-cleanup-fault.ready");
        if (!normal && !fault) return;
        require(mode == null && normal != fault && exact.isSameThread() && supplied.length == 2
                && targets.length == 2 && supplied[0] != supplied[1], "FRESH_EXCLUSIVE_MODE");
        for (String cue : List.of("lifecycle-death.ready", "active-save.ready", "active-save-stop.ready",
                "density-64.ready", "density-65.ready")) {
            require(!P11C4aEvidence.cuePresent(directory, cue), "EXCLUSIVE_CASE");
        }
        server = exact; output = directory; actors = supplied; U0OrdinaryLogoutScenario.targets = targets; mode = normal ? Mode.NORMAL : Mode.CLEANUP_FAULT;
        stage = 1;
        for (int i = 0; i < 2; i++) {
            require(current(supplied[i]) && targets[i] != null && !targets[i].isRemoved(), "INITIAL_NATIVE_ACTORS");
            // Initial fixture placement only, before input-ready/accepted work; unchanged dry/K1 physics.
            var actor = supplied[i]; double z = actor.getZ() - 2.0;
            actor.teleportTo(actor.serverLevel(), actor.getX(), actor.getY(), z, java.util.Set.of(), 0, 6);
            targets[i].teleportTo(actor.getX(), targets[i].getY(), z + 11.0);
        }
        record("ARMED_BEFORE_REAL_R");
    }

    static boolean active() { return mode != null; }

    /** Existing publishOpen RETURN hook; never retains supplied binding/carrier/actor. */
    public static void opened(Object supplied) {
        if (mode == null || !(supplied instanceof SkillRuntimeService.V0CarrierBinding b)
                || b.child || !b.normalPolicy() || b.carrier() == null) return;
        var actual = b.carrier(); var actor = actual.getOwner();
        if (stage == 1 && openLineage == 0) {
            for (int i = 0; i < 2; i++) if (actor == actors[i]) {
                selected = i; openLineage = b.lineage.value(); openBinding = b.id.value(); openEntity = actual.getId();
                U0OrdinaryTrace.note("LOGOUT_SELECTED_OPEN", openLineage, openBinding, openEntity, i + 1, 0, 0, 0, 0, 0, "");
                return;
            }
        } else if (stage >= 4 && successor != null && actor == successor && b.lineage.value() != openLineage) {
            newLineage = b.lineage.value();
            U0OrdinaryTrace.note("LOGOUT_NEW_C_OPEN", newLineage, b.id.value(), actual.getId(), selected + 1, 0, 0, 0, 0, 0, "");
        }
    }

    /** Existing exact AUTH→PLAY successful callback only; no host-supplied authentication flag. */
    static void authenticatedPlay(MinecraftServer exact, ServerPlayer actor) {
        if (mode == null || original == null || !actor.getUUID().equals(original.getUUID())) return;
        require(stage == 4 && exact == server && current(actor) && actor != original
                && actor.connection != listener && actor.connection.getConnection() != connection
                && !connection.isConnected() && wholeReturns == 1, "GENUINE_NEW_C_AFTER_ORIGINAL_EXIT");
        successor = actor; actors[selected] = actor;
        U0OrdinaryTrace.note("LOGOUT_NEW_C_AUTH", openLineage, 0, 0, 0, 0, 0, 1, 1, 0,
                P11C4aEvidence.pseudonym(actor.getUUID()));
    }

    static void tick(MinecraftServer exact) throws IOException {
        require(mode != null && exact == server && exact.isSameThread(), "SCENE_OWNER");
        if (stage == 1) {
            if (openLineage == 0) return;
            original = actors[selected]; peer = actors[1 - selected];
            require(current(original) && current(peer), "SELECTED_ORIGINAL_CURRENT");
            listener = original.connection; connection = listener.getConnection();
            owner = P11NativeStorageBoundary.nativeSourceOwner(original); body = owner == null ? null : owner.body(original);
            require(body != null && owner.canCopy(body), "SELECTED_ORIGINAL_SOURCE");
            var entry = body.cooldown.data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
            require(entry != null && entry.kind == 0 && entry.duration == 20 && entry.attemptId != null,
                    "ACTUAL_ACTIVE20_AFTER_OPEN");
            expires = entry.expiresAt; attemptMost = entry.attemptId.getMostSignificantBits();
            attemptLeast = entry.attemptId.getLeastSignificantBits();
            var entity = original.serverLevel().getEntity((int)openEntity);
            require(entity instanceof P9StarterProjectile projectile && projectile.getOwner() == original
                    && !entity.isRemoved() && entity.isAddedToLevel(), "ORIGINAL_TRUE_NATIVE_CARRIER");
            carrier = (P9StarterProjectile) entity;
            stage = 2; record("TRUE_ACCEPTED_OPEN_BEFORE_DEPARTURE");
            P11C4aEvidence.write(output, "logout-selected.json", Map.of("format", "U0_LOGOUT_SELECTED_V1",
                    "pseudonym", P11C4aEvidence.pseudonym(original.getUUID()), "mode", mode.name()));
            if (mode == Mode.NORMAL) {
                P11C4aEvidence.cue(output, "logout-disconnect.ready");
            } else {
                // This close is merely a trigger. Only the actual failing original native cleanup
                // below, not a kick reason, may establish the CLEANUP_FAULT evidence role.
                connection.disconnect(Component.literal("U0 owned cleanup-fault fixture departure"));
                try { connection.handleDisconnection(); }
                catch (RuntimeException primary) {
                    if (primary != OWNED_FAULT) throw primary;
                    expectedCallerCaught = true; // Exact expected native primary, never a substituted Throwable.
                }
                record("ORIGINAL_FAULT_CALLER_RETURN");
            }
            return;
        }
        require(current(peer), "PEER_STAYS_CURRENT");
        if (stage == 2) {
            if (wholeReturns == 0) return;
            require(!inWhole && wholeEntries == 1 && proofCalls == 1 && !unexpected,
                    "EXACT_WHOLE_ORIGINAL_TERMINAL");
            if (mode == Mode.NORMAL) require(wholeNormal && proofTrue && leaveFaults == 0, "NORMAL_PROOF_REQUIRED");
            else require(!wholeNormal && !proofTrue && sameFault && leaveFaults == 1 && expectedCallerCaught
                    && body.account.cleanupUnknown && !owner.canCopy(body)
                    && body.envelope == null && body.pendingEnvelope == null, "ACTUAL_CLEANUP_FAULT_NO_GRANT");
            record("WHOLE_NATIVE_LOGOUT_TERMINAL");
            physicalReadback("logout-primary.json");
            stage = 3;
        }
        if (stage == 3) {
            if (!carrier.isRemoved()) {
                if (mode == Mode.NORMAL && carrier.getOwner() == original) continuedAsOldA = true;
                return; // Original ticking/target/claim/deadline only. No hold, late hit or removal by fixture.
            }
            require(!connection.isConnected(), "OLD_CONNECTION_CLOSED");
            if (mode == Mode.NORMAL) require(server.getPlayerList().getPlayer(original.getUUID()) != original,
                    "WHOLE_NORMAL_ROSTER_RETIREMENT");
            record("OLD_NATIVE_CARRIER_REMOVED");
            if (mode == Mode.CLEANUP_FAULT) {
                // Keep the exact native partial tail, including a retained old roster/source if present.
                // This case never forces a reconnect/repair; NORMAL supplies the genuine new-C role.
                record("FAULT_NATIVE_TAIL_OBSERVED_NO_RECONNECT");
                stage = 5; P11C4aEvidence.cue(output, "logout-fault-terminal.ready");
                return;
            }
            stage = 4; P11C4aEvidence.cue(output, "logout-reconnect.ready");
            return;
        }
        if (stage == 4 && successor != null) {
            require(current(successor), "NATIVE_NEW_C_CURRENT");
            var nextOwner = P11NativeStorageBoundary.nativeSourceOwner(successor);
            var nextBody = nextOwner == null ? null : nextOwner.body(successor);
            if (mode == Mode.NORMAL && (nextBody == null || !nextOwner.canCopy(nextBody))) return;
            record("NEW_C_NATIVE_SOURCE_OBSERVED");
            stage = 5; P11C4aEvidence.cue(output, "logout-new-c-input-ready.ready");
        }
        if (stage == 5 && P11C4aEvidence.cuePresent(output, "logout-finish.ready")) {
            stage = 6; record("BEFORE_FINAL_ORIGINAL_SAVE");
            if (mode == Mode.NORMAL) server.getPlayerList().saveAll(); // Never force a writer over cleanupUnknown.
            physicalReadback("logout-final-primary.json"); record("FINAL_NATIVE_READBACK"); flush("FINISH");
            var dir = P11C4aEvidence.root().resolve("server");
            for (String role : List.of("a", "b")) for (String suffix : List.of("-finish.ready", "-exit-game.ready")) {
                if (!P11C4aEvidence.cuePresent(dir, role + suffix)) P11C4aEvidence.cue(dir, role + suffix);
            }
            server.halt(false);
        }
    }

    /** Existing original-whole-logout wrapper supplies this call; no synthetic proof or state write. */
    public static boolean wholeEntering(ServerGamePacketListenerImpl actual) {
        if (mode == null || stage != 2 || actual != listener) return false;
        if (!server.isSameThread() || inWhole || wholeEntries != 0 || actual.player != original) {
            unexpected = true;
            U0OrdinaryTrace.note("OBSERVATION_FAILURE", 0, 0, 0, 0, 0, 0, 0, 0, 0, "");
            return false; // A failed observer may never skip or replace the original whole native call.
        }
        inWhole = true; wholeEntries++;
        return true;
    }
    public static void proofReturned(boolean proved) {
        if (mode == null || !inWhole) return;
        proofCalls++; proofTrue = proved;
        U0OrdinaryTrace.note("LOGOUT_ORIGINAL_V0_PROOF", openLineage, 0, 0, 0, 0, 0, proved ? 1 : 0, 0, 0, "");
    }
    public static void wholeFinished(boolean selectedCall, Throwable primary) {
        if (!selectedCall) return;
        wholeReturns++; inWhole = false; wholeNormal = primary == null; sameFault = primary == OWNED_FAULT;
        if (primary != null && !sameFault) unexpected = true;
        U0OrdinaryTrace.note("LOGOUT_ORIGINAL_WHOLE_RETURN", openLineage, 0, 0, 0, 0, 0,
                wholeNormal ? 1 : 0, sameFault ? 1 : 0, unexpected ? 1 : 0, "");
    }
    /** Reused original EntityLeaveLevelEvent callback; one selected native cleanup only. */
    static void leaving(EntityLeaveLevelEvent event) {
        if (mode != Mode.CLEANUP_FAULT || !inWhole || event.getEntity() != original) return;
        require(server.isSameThread() && leaveFaults == 0 && body.account.nativeCounts[P11ControlBudgets.Root.WORK.ordinal()] > 0,
                "FAULT_ONLY_IN_OWN_ACCEPTED_NATIVE_CLEANUP");
        leaveFaults++; throw OWNED_FAULT;
    }

    private static void record(String phase) throws IOException {
        require(facts.size() < 24, "SCALAR_RECORD_BOUND");
        var row = new LinkedHashMap<String,Object>();
        row.put("phase", phase); row.put("nativeTick", server.getTickCount()); row.put("mode", mode.name());
        row.put("selectedAuthOrder", selected + 1); row.put("lineage", openLineage); row.put("binding", openBinding);
        row.put("newLineage", newLineage); row.put("attemptMost", attemptMost); row.put("attemptLeast", attemptLeast);
        row.put("originalExpiry", expires); row.put("wholeEntries", wholeEntries); row.put("wholeReturns", wholeReturns);
        row.put("proofCalls", proofCalls); row.put("proofTrue", proofTrue); row.put("wholeNormal", wholeNormal);
        row.put("sameExpectedFault", sameFault); row.put("expectedCallerCaught", expectedCallerCaught);
        row.put("leaveFaults", leaveFaults); row.put("continuedAsOldA", continuedAsOldA); row.put("unexpectedPrimary", unexpected);
        row.put("oldCarrierRemoved", carrier != null && carrier.isRemoved());
        if (selected >= 0) { row.put("targetId", targets[selected].getId()); row.put("targetHealth", targets[selected].getHealth()); }
        if (original != null) {
            row.put("pseudonym", P11C4aEvidence.pseudonym(original.getUUID()));
            row.put("oldConnectionClosed", !connection.isConnected());
            row.put("oldStillInRoster", server.getPlayerList().getPlayer(original.getUUID()) == original);
            row.put("oldBodyCleanupUnknown", body.account.cleanupUnknown); row.put("oldBodyCopyCurrent", owner.canCopy(body));
            row.put("oldEnvelopePresent", body.envelope != null); row.put("oldPendingEnvelopePresent", body.pendingEnvelope != null);
            row.put("currentNativeWriterForAccount", P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(server, original.getUUID())));
            U0OrdinaryManaObservation.recordExisting(original, 5000 + facts.size(), selected + 1);
        }
        if (successor != null) {
            var own = P11NativeStorageBoundary.nativeSourceOwner(successor); var b = own == null ? null : own.body(successor);
            row.put("sameAccountNewActor", successor != original && successor.getUUID().equals(original.getUUID()));
            row.put("newPhysicalConnection", successor.connection.getConnection() != connection);
            row.put("newListener", successor.connection != listener); row.put("newNativeCurrent", current(successor));
            row.put("newBodyPresent", b != null); row.put("newBodyCopyCurrent", b != null && own.canCopy(b));
            row.put("newLoadObserved", b != null && b.loadObserved);
            row.put("newSourceInput", b == null ? "UNAVAILABLE" : b.inputKind.name());
            var data = b == null || b.cooldown == null ? null : b.cooldown.data;
            var entry = data == null ? null : data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
            row.put("newClockFloor", data == null ? -1L : data.clockFloor);
            row.put("newPolicyPresent", entry != null); row.put("newPolicyExpiry", entry == null ? -1L : entry.expiresAt);
            row.put("newPolicyAttemptMost", entry == null || entry.attemptId == null ? 0L : entry.attemptId.getMostSignificantBits());
            row.put("newPolicyAttemptLeast", entry == null || entry.attemptId == null ? 0L : entry.attemptId.getLeastSignificantBits());
            U0OrdinaryManaObservation.recordExisting(successor, 6000 + facts.size(), selected + 1);
        }
        facts.add(Map.copyOf(row));
        P11C4aEvidence.write(output, "logout-step-" + facts.size() + ".json", row);
    }

    /** Fixed selected game-world .dat read only; not an authentication store or generic reader API. */
    private static void physicalReadback(String leaf) throws IOException {
        var path = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(original.getUUID() + ".dat");
        var row = new LinkedHashMap<String,Object>(); row.put("format", "U0_LOGOUT_PHYSICAL_PD_V1");
        row.put("mode", mode.name()); row.put("nativeTick", server.getTickCount());
        row.put("originalWriter", P11C4aEvidence.sourceObservation(P11NativeStorageBoundary.diagnostics(server, original.getUUID())));
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || Files.size(path) < 1 || Files.size(path) > 32L * 1024 * 1024) {
            row.put("status", "MISSING_OR_UNSAFE_PRIMARY_NOT_PROVED");
        } else {
            var saved = NbtIo.readCompressed(path, NbtAccounter.create(32L * 1024 * 1024));
            var attachments = saved.getCompound("neoforge:attachments");
            var mana = attachments.getCompound("gramarye:player_mana");
            var tag = attachments.get("gramarye:cast_cooldowns");
            var data = tag == null ? null : P11CastCooldownCodec.read(tag);
            var entry = data == null ? null : data.entries.get(NormalSpellPolicy.BOOTSTRAP.key());
            row.put("status", "ACTUAL_PRIMARY_READ_NOT_SOURCE_PROOF");
            row.put("manaExactScalarTypes", mana.contains("schema_version", Tag.TAG_INT) && mana.contains("balance", Tag.TAG_LONG));
            row.put("manaBalance", mana.contains("balance", Tag.TAG_LONG) ? mana.getLong("balance") : -1L);
            row.put("cooldownMaterial", data == null ? "ABSENT" : data.kind.name());
            row.put("cooldownSchema", data == null ? 0 : data.schemaVersion);
            row.put("clockFloor", data == null ? -1L : data.clockFloor); row.put("policyPresent", entry != null);
            row.put("policyExpiry", entry == null ? -1L : entry.expiresAt);
            row.put("policyAttemptMost", entry == null || entry.attemptId == null ? 0L : entry.attemptId.getMostSignificantBits());
            row.put("policyAttemptLeast", entry == null || entry.attemptId == null ? 0L : entry.attemptId.getLeastSignificantBits());
            row.put("primaryBytes", Files.size(path));
        }
        P11C4aEvidence.write(output, leaf, row);
    }

    static void flushStopped(MinecraftServer exact) throws IOException { if (mode != null) { require(exact == server, "STOP_OWNER"); flush("STOP"); } }
    private static void flush(String why) throws IOException {
        if (written) return; written = true;
        P11C4aEvidence.write(output, "logout-result.json", Map.of("format", "U0_NATIVE_LOGOUT_COMPONENT_V1",
                "status", "OBSERVATIONS_NOT_U0_ROW_ACCEPTANCE", "flush", why, "mode", mode.name(),
                "stage", stage, "unexpectedPrimary", unexpected, "facts", List.copyOf(facts)));
    }
    static void stopped() {
        mode = null; server = null; output = null; actors = null; targets = null; original = null; peer = null; successor = null;
        listener = null; connection = null; owner = null; body = null; carrier = null; facts.clear();
        selected = -1; stage = 0; wholeEntries = wholeReturns = proofCalls = leaveFaults = 0;
        openLineage = openBinding = openEntity = newLineage = expires = attemptMost = attemptLeast = 0;
        inWhole = proofTrue = wholeNormal = sameFault = expectedCallerCaught = continuedAsOldA = unexpected = written = false;
    }
    private static boolean current(ServerPlayer a) {
        return a != null && a.getServer() == server && server.isSameThread() && a.connection != null
                && a.connection.player == a && a.connection.getConnection().getPacketListener() == a.connection
                && a.connection.getConnection().isConnected() && a.connection.getConnection().isEncrypted()
                && !a.connection.getConnection().isMemoryConnection() && !a.isFakePlayer() && a.isAlive() && !a.isRemoved()
                && server.getPlayerList().getPlayer(a.getUUID()) == a;
    }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException("U0_LOGOUT_" + code); }
}
