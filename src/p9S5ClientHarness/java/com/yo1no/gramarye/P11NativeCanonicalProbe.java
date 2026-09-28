package com.yo1no.gramarye;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonWriter;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/** Excluded actual PA/manager/client-packet consumers. This is not a whole-server datapack reload. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeCanonicalProbe {
    private static final ResourceLocation ROOT = id("canonical_root");
    private static final ResourceLocation OLD_TICK = id("obsolete_tick");
    private static final ResourceLocation NEW_TICK = id("current_tick");
    private static final ResourceLocation AUTO = id("automatic");
    private static final ResourceLocation CAPTURED = id("captured_same_id");
    private static final ResourceLocation NESTED_CURRENT = id("nested_current_award");
    private static volatile State active;

    private P11NativeCanonicalProbe() {}

    record Report(boolean passed, List<String> observations, String failure) {
        Report { observations = List.copyOf(observations); }
        @Override public String toString() {
            return "P11-NATIVE-CANONICAL-PROBE-V1\n"
                    + "scope=NATIVE_PA_SUBSYSTEM_AND_REAL_CLIENT_PACKET_HANDLERS\n"
                    + "wholeServerDatapackReload=false\nsyntheticPlayer=false\nreflection=false\n"
                    + "manualSourceReceipts=false\n" + String.join("\n", observations) + '\n'
                    + "failure=" + failure + '\n' + "RESULT=" + (passed ? "PASS" : "FAIL") + '\n';
        }
    }

    /** Called on the real server thread before the companion starts polling both sides. */
    static void start(MinecraftServer server, ServerPlayer actor, Path output) throws IOException {
        require(active == null && server.isSameThread() && actor.getServer() == server
                        && !actor.isFakePlayer() && server.isSingleplayerOwner(actor.getGameProfile())
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && P11NativeStorageBoundary.isManagedCanonicalAdvancements(actor.getAdvancements(), actor)
                        && P11NativeStorageBoundary.nativeDeliveryEligible(actor),
                "canonical probe requires exact managed authenticated integrated host");
        require(output.isAbsolute() && output.equals(output.normalize())
                        && Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS),
                "canonical evidence must be a fresh owned child");
        Files.createDirectory(output);
        var state = new State(server, actor, output);
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var holder : state.original.getAllAdvancements()) {
            state.base.put(holder.id(), Advancement.CODEC.encodeStart(ops, holder.value()).getOrThrow());
        }
        require(!state.base.containsKey(ROOT) && !state.base.containsKey(OLD_TICK)
                        && !state.base.containsKey(NEW_TICK) && !state.base.containsKey(AUTO)
                        && !state.base.containsKey(CAPTURED) && !state.base.containsKey(NESTED_CURRENT),
                "engineering fixture IDs overlap existing definitions");
        active = state;
    }

    /** Called from the existing companion client tick; no product channel or packet replay. */
    static void clientObserve(Minecraft minecraft) {
        var state = active;
        if (state == null || minecraft.getConnection() == null) { return; }
        var advancements = minecraft.getConnection().getAdvancements();
        if (state.finished) {
            if (state.clientAttached) { advancements.setListener(null); state.clientAttached = false; }
            return;
        }
        if (!state.clientAttached) {
            if (minecraft.screen != null) { return; }
            advancements.setListener(new ClientObserver(state));
            state.clientAttached = true;
            state.clientReady = true;
        }
        var phase = state.phase;
        boolean cleared = state.clientClears > state.requiredClear;
        if (phase == Phase.FIRST && cleared && advancements.get(ROOT) != null
                && ROOT.equals(state.clientSelected) && state.clientRootDone) {
            state.clientCompleted = phase;
        } else if (phase == Phase.RESET && cleared && advancements.get(ROOT) != null
                && state.clientSelected == null && state.clientRootDone) {
            state.clientCompleted = phase;
        } else if (phase == Phase.ZERO && cleared && advancements.getTree().nodes().isEmpty()
                && state.clientSelected == null) {
            state.clientCompleted = phase;
        } else if (phase == Phase.RESTORED && cleared && advancements.get(ROOT) == null) {
            state.clientCompleted = phase;
        }
    }

    /** Returns null while awaiting the real client handler, then one bounded final report. */
    static Report serverStep(MinecraftServer server) {
        var state = active;
        if (state == null || state.server != server) { return null; }
        if (state.report != null) { return state.report; }
        try {
            require(server.isSameThread() && server.getPlayerList().getPlayer(state.actor.getUUID()) == state.actor
                            && P11NativeStorageBoundary.nativeDeliveryEligible(state.actor),
                    "canonical probe lost its exact live actor/connection");
            require(++state.polls <= 600, "actual client canonical packet observation timed out");
            var pa = state.actor.getAdvancements();
            switch (state.phase) {
                case WAIT_CLIENT -> {
                    if (!state.clientReady) { return null; }
                    var first = manager(state, rootDefinitions(state, "kept"), 0);
                    pa.reload(first);
                    require(pa.award(first.get(ROOT), "kept"), "first native criterion did not change");
                    next(state, Phase.FIRST);
                    pa.flushDirty(state.actor);
                    pa.setSelectedTab(first.get(ROOT));
                    state.firstManager = first;
                }
                case FIRST -> {
                    if (state.clientCompleted != Phase.FIRST) { return null; }
                    state.observations.add("clientFirst=NATIVE_RESET_ROOT_PROGRESS_AND_SELECTED_TAB");
                    exerciseReloads(state);
                    next(state, Phase.RESET);
                    state.failResetSend = true;
                    try {
                        pa.flushDirty(state.actor);
                        throw new IllegalStateException("native reset send injection was not reached");
                    } catch (SendFault expected) {
                        require(expected == state.sendFault && state.sendFaults == 1,
                                "actual reset send did not preserve exact injected failure");
                    }
                    pa.flushDirty(state.actor);
                }
                case RESET -> {
                    if (state.clientCompleted != Phase.RESET) { return null; }
                    state.observations.add("clientRetry=CURRENT_RESET_AFTER_REAL_SEND_FAILURE_AND_NULL_TAB");
                    var hidden = new LinkedHashMap<ResourceLocation, JsonElement>();
                    state.currentDefinitions.forEach((id, json) -> {
                        var copy = json.deepCopy().getAsJsonObject();
                        copy.remove("display");
                        // Native visibility uses completed-self/descendant as an unconditional
                        // visible result even without a display. Add one never-triggered
                        // requirement to each definition; existing criterion progress survives
                        // and native restore later drops only this temporary criterion.
                        var criteria = copy.getAsJsonObject("criteria");
                        require(!criteria.has("p11_engineering_zero_visible"), "zero-visibility criterion collision");
                        criteria.add("p11_engineering_zero_visible",
                                JsonParser.parseString("{\"trigger\":\"minecraft:impossible\"}"));
                        if (copy.has("requirements")) {
                            var required = new JsonArray();
                            required.add("p11_engineering_zero_visible");
                            copy.getAsJsonArray("requirements").add(required);
                        }
                        hidden.put(id, copy);
                    });
                    pa.reload(manager(state, hidden, 0));
                    next(state, Phase.ZERO);
                    pa.flushDirty(state.actor);
                }
                case ZERO -> {
                    if (state.clientCompleted != Phase.ZERO) { return null; }
                    state.observations.add("clientZero=NATIVE_RESET_WITH_ZERO_VISIBLE_CLEARS_OLD_TREE_AND_TAB");
                    pa.reload(state.original);
                    next(state, Phase.RESTORED);
                    pa.flushDirty(state.actor);
                }
                case RESTORED -> {
                    if (state.clientCompleted != Phase.RESTORED) { return null; }
                    state.observations.add("restore=ORIGINAL_MANAGER_NATIVE_RELOAD_AND_CLIENT_RESET");
                    return finish(state, null);
                }
            }
            return null;
        } catch (IOException | RuntimeException | Error failure) {
            try { state.actor.getAdvancements().reload(state.original); }
            catch (RuntimeException | Error restoreFailure) { failure.addSuppressed(restoreFailure); }
            return finish(state, failure);
        }
    }

    private static void exerciseReloads(State state) throws IOException {
        var pa = state.actor.getAdvancements();
        var before = pa.getOrStartProgress(state.firstManager.get(ROOT));
        var criterion = before.getCriterion("kept");
        failNativeWrite(state);
        var changed = manager(state, rootDefinitions(state, "kept", "new"), 0);
        state.failCopy = true;
        try {
            pa.reload(changed);
            throw new IllegalStateException("native reload copy fault injection was not reached");
        } catch (CopyFault expected) {
            require(expected == state.copyFault && state.copyFaults == 1,
                    "native copy did not preserve exact injected failure");
        }
        var preserved = pa.getOrStartProgress(state.firstManager.get(ROOT));
        require(preserved == before && preserved.getCriterion("kept") == criterion
                        && criterion.isDone() && preserved.isDone(),
                "failed reload copy destructively cleared or replaced original native progress");
        state.observations.add("copyFault=REAL_CODEC_RETURN_FAULT_BEFORE_CLEAR_OR_LISTENER_STOP_ORIGINAL_IDENTITIES_UNCHANGED");
        pa.reload(changed);
        var after = pa.getOrStartProgress(changed.get(ROOT));
        require(before != after && criterion != after.getCriterion("kept")
                        && after.getCriterion("kept").isDone() && !after.getCriterion("new").isDone()
                        && !after.isDone(), "native dirty-memory reload lost progress, aliased it, or copied done authority");
        before.revokeProgress("kept");
        require(after.getCriterion("kept").isDone(), "old native progress alias mutated current canonical progress");
        state.observations.add("reload=LATEST_UNSAVED_MEMORY_AFTER_NATIVE_JSON_WRITE_FAILURE_DEEP_COPY_NEW_REQUIREMENTS");

        var removed = manager(state, rootDefinitions(state, "new"), 0);
        pa.reload(removed);
        require(pa.getOrStartProgress(removed.get(ROOT)).getCriterion("kept") == null,
                "removed criterion survived native requirements update");
        pa.award(removed.get(ROOT), "new");
        pa.reload(manager(state, state.base, 0));
        var reintroduced = manager(state, rootDefinitions(state, "new"), 0);
        pa.reload(reintroduced);
        require(!pa.getOrStartProgress(reintroduced.get(ROOT)).hasProgress(),
                "missing ID was silently restored from old disk progress");
        state.observations.add("definitions=NATIVE_CRITERION_REMOVAL_AND_MISSING_ID_DISCARD");

        exerciseCapturedListeners(state, false);
        exerciseCapturedListeners(state, true);

        var outerDefinitions = rootDefinitions(state, "kept");
        outerDefinitions.put(OLD_TICK, tickDefinition());
        var innerDefinitions = rootDefinitions(state, "kept");
        innerDefinitions.put(NEW_TICK, tickDefinition());
        var outer = manager(state, outerDefinitions, 1);
        var inner = manager(state, innerDefinitions, 2);
        state.currentDefinitions = innerDefinitions;
        state.nested = inner;
        state.nestedArmed = true;
        int xp = state.actor.totalExperience;
        pa.reload(outer);
        require(!state.nestedArmed && state.automaticEvents == 2 && state.actor.totalExperience == xp + 3,
                "actual nested automatic reload did not execute native rewards exactly on both calls");
        require(pa.award(inner.get(ROOT), "kept"), "nested current tree rejected its own criterion");
        CriteriaTriggers.TICK.trigger(state.actor);
        require(state.oldTickEvents == 0 && state.newTickEvents == 1,
                "old reload tail reattached obsolete listeners or lost current listeners");
        state.observations.add("nested=REAL_XP_CALLBACK_RELOAD_AUTOMATIC_1_PLUS_2_OLD_LISTENER_ZERO_CURRENT_ONE");
    }

    private static void exerciseCapturedListeners(State state, boolean reuseManager) {
        var pa = state.actor.getAdvancements();
        // Real missing-ID reload gives each case fresh progress, without a test overlay.
        pa.reload(manager(state, rootDefinitions(state, "kept"), 0));
        var definitions = rootDefinitions(state, "kept");
        definitions.put(CAPTURED, JsonParser.parseString("""
                {"criteria":{"first":{"trigger":"minecraft:tick"},
                 "second":{"trigger":"minecraft:tick"}},"rewards":{"experience":5}}
                """));
        definitions.put(NESTED_CURRENT, JsonParser.parseString(
                "{\"criteria\":{\"current\":{\"trigger\":\"minecraft:impossible\"}}}"));
        var old = manager(state, definitions, 0);
        var next = reuseManager ? old : manager(state, definitions, 0);
        pa.reload(old);
        require((old.get(CAPTURED) == next.get(CAPTURED)) == reuseManager,
                "captured-listener case does not exercise the requested native holder identity");
        state.capturedNext = next;
        state.capturedEvents = 0;
        int xp = state.actor.totalExperience;
        try {
            // Native trigger captures both listeners before the first progress event reloads.
            CriteriaTriggers.TICK.trigger(state.actor);
            var canonical = pa.getOrStartProgress(next.get(CAPTURED));
            String remaining = "first".equals(state.capturedFirst) ? "second" : "first";
            require(state.capturedEvents == 2 && state.capturedSecond != null
                            && state.capturedSecond != canonical && state.capturedSecond.isDone()
                            && canonical.getCriterion(state.capturedFirst).isDone()
                            && !canonical.getCriterion(remaining).isDone() && !canonical.isDone()
                            && state.actor.totalExperience == xp + 5,
                    "second captured same-ID callback changed current canonical progress or skipped native reward");
            require(pa.getOrStartProgress(next.get(NESTED_CURRENT)).isDone(),
                    "old listener scope discarded legitimate nested current-tree award");
            // A separate real trigger must still own its new-generation listener, even
            // when the old callback's unregister uses the very same holder object.
            CriteriaTriggers.TICK.trigger(state.actor);
            require(state.capturedEvents == 3 && canonical.isDone() && state.actor.totalExperience == xp + 10,
                    "old listener removed current listener or new generation failed to complete once");
            state.observations.add("capturedSameId=" + (reuseManager ? "SAME_MANAGER_AND_HOLDER" : "DISTINCT_HOLDER")
                    + ";oldSecond=DETACHED_NATIVE_COMPLETION_XP5;currentAfterOld=INCOMPLETE"
                    + ";nestedCurrentAward=PUBLISHED;separateCurrentTrigger=COMPLETED_XP5_ONCE");
        } finally {
            state.capturedNext = null;
            state.capturedSecond = null;
            state.capturedFirst = null;
        }
    }

    private static void failNativeWrite(State state) throws IOException {
        var path = state.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR)
                .resolve(state.actor.getUUID() + ".json");
        var backup = state.output.resolve("prior-advancements.json");
        Files.createDirectories(path.getParent());
        boolean existed = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
        require(existed || Files.notExists(path, LinkOption.NOFOLLOW_LINKS), "native progress path is not a regular file");
        if (existed) { Files.move(path, backup); }
        boolean blocker = false;
        try {
            Files.createDirectory(path);
            blocker = true;
            state.actor.getAdvancements().save(); // Native method logs/swallow IOException; writer witness is required.
            var diagnostic = P11NativeStorageBoundary.diagnostics(state.server, state.actor.getUUID());
            var facts = diagnostic.writers().stream().filter(writer -> writer.kind().equals("ADVANCEMENTS"))
                    .findFirst().orElseThrow();
            require(facts.dirty() && facts.terminal().equals("FAILED"),
                    "native writer falsely cleared dirty responsibility after the directory write failure");
            state.observations.add("nativeWriteFault=" + facts.terminal() + "/" + facts.write());
        } finally {
            if (blocker) { Files.delete(path); }
            if (existed) { Files.move(backup, path); }
        }
    }

    /** Exact excluded send callsite only; never replaces the real transport or its listener. */
    public static void beforeSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var state = active;
        if (state != null && !state.finished && listener == state.actor.connection
                && packet instanceof ClientboundUpdateAdvancementsPacket update && update.shouldReset()) {
            state.resetSendAttempts++;
        }
        if (state != null && state.failResetSend && listener == state.actor.connection
                && packet instanceof ClientboundUpdateAdvancementsPacket advancements && advancements.shouldReset()) {
            state.failResetSend = false;
            state.sendFaults++;
            throw state.sendFault;
        }
    }

    /** Normal native send return is local submission, not a client acknowledgement. */
    public static void afterSend(ServerCommonPacketListenerImpl listener, Packet<?> packet) {
        var state = active;
        if (state != null && !state.finished && listener == state.actor.connection
                && packet instanceof ClientboundUpdateAdvancementsPacket update && update.shouldReset()) {
            state.resetSubmissions++;
            state.resetSubmittedAdded += update.getAdded().size();
            state.resetSubmittedRemoved += update.getRemoved().size();
            state.resetSubmittedProgress += update.getProgress().size();
        }
    }

    /** Only scalar observations survive the real call. The copied native Data is never retained. */
    public static CopyMeasurement beginCopy(Object input) {
        var state = active;
        if (state == null || state.finished || !state.server.isSameThread()
                || !(input instanceof PlayerAdvancements.Data)) { return null; }
        state.copyCalls++;
        state.copyScopes++;
        state.copyPeakScopes = Math.max(state.copyPeakScopes, state.copyScopes);
        return new CopyMeasurement(state);
    }

    public static void endCopy(CopyMeasurement measurement, boolean normal) {
        if (measurement == null) { return; }
        var state = measurement.state;
        long elapsed = Math.max(0, System.nanoTime() - measurement.started
                - (state.copySizeNanos - measurement.sizeNanosBefore));
        state.copyNanos += elapsed;
        state.copyMaxNanos = Math.max(state.copyMaxNanos, elapsed);
        if (!normal) { state.copyFailures++; }
        state.copyScopes--;
    }

    /** Measures the actual intermediate JSON's UTF-8 representation, not Java graph heap size. */
    public static void copyEncoded(Object encoded) {
        var state = active;
        if (state == null || state.copyScopes == 0 || !state.server.isSameThread()
                || !(encoded instanceof JsonElement json)) { return; }
        long started = System.nanoTime();
        var bytes = new CountingOutput();
        try (var writer = new JsonWriter(new OutputStreamWriter(bytes, StandardCharsets.UTF_8))) {
            Streams.write(json, writer);
        } catch (IOException impossible) {
            throw new IllegalStateException("in-memory counting writer failed", impossible);
        } finally { state.copySizeNanos += System.nanoTime() - started; }
        state.copyEncodedBytes += bytes.count;
        state.copyMaxEncodedBytes = Math.max(state.copyMaxEncodedBytes, bytes.count);
        if (state.reloadMeasurement != null) {
            state.reloadMeasurement.bytes += bytes.count;
            state.liveCopyBytes += bytes.count;
            state.peakLiveCopyBytes = Math.max(state.peakLiveCopyBytes, state.liveCopyBytes);
        }
    }

    public static ReloadMeasurement beginReload(PlayerAdvancements canonical) {
        var state = active;
        if (!measured(state, canonical)) { return null; }
        var scope = new ReloadMeasurement(state, state.reloadMeasurement);
        state.reloadMeasurement = scope;
        state.reloadScopes++;
        state.reloadPeakScopes = Math.max(state.reloadPeakScopes, state.reloadScopes);
        return scope;
    }

    public static void endReload(ReloadMeasurement scope) {
        if (scope == null) { return; }
        var state = scope.state;
        state.reloadNanos += Math.max(0, System.nanoTime() - scope.started);
        state.liveCopyBytes -= scope.bytes;
        state.reloadScopes--;
        state.reloadMeasurement = scope.previous;
    }

    public static void resetBuilt(PlayerAdvancements canonical, boolean reset,
            ClientboundUpdateAdvancementsPacket packet, long nanos) {
        var state = active;
        if (!reset || !measured(state, canonical)) { return; }
        state.resetBuildAttempts++;
        state.resetBuildNanos += Math.max(0, nanos);
        if (packet == null) { state.resetBuildFailures++; return; }
        state.resetBuilds++;
        state.resetBuiltAdded += packet.getAdded().size();
        state.resetBuiltRemoved += packet.getRemoved().size();
        state.resetBuiltProgress += packet.getProgress().size();
    }

    private static boolean measured(State state, PlayerAdvancements canonical) {
        return state != null && !state.finished && state.server.isSameThread()
                && state.actor.getAdvancements() == canonical;
    }

    public static final class CopyMeasurement {
        private final State state;
        private final long started = System.nanoTime();
        private final long sizeNanosBefore;
        private CopyMeasurement(State state) { this.state = state; sizeNanosBefore = state.copySizeNanos; }
    }

    public static final class ReloadMeasurement {
        private final State state;
        private final ReloadMeasurement previous;
        private final long started = System.nanoTime();
        private long bytes;
        private ReloadMeasurement(State state, ReloadMeasurement previous) { this.state = state; this.previous = previous; }
    }

    private static final class CountingOutput extends OutputStream {
        private long count;
        @Override public void write(int value) { count++; }
        @Override public void write(byte[] values, int offset, int length) { count += length; }
    }

    /** The real native Data codec has returned, but the PA reload has not entered its clear/stop body. */
    public static void afterCopy(Object input, Object output) {
        var state = active;
        if (state != null && state.failCopy && state.server.isSameThread()
                && input instanceof net.minecraft.server.PlayerAdvancements.Data
                && output instanceof net.minecraft.server.PlayerAdvancements.Data && input != output) {
            state.failCopy = false;
            state.copyFaults++;
            throw state.copyFault;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onXp(PlayerXpEvent.XpChange event) {
        var state = active;
        if (state == null || state.finished || event.getEntity() != state.actor || state.nested == null) { return; }
        if (event.getAmount() == 1 || event.getAmount() == 2) { state.automaticEvents++; }
        if (state.nestedArmed && event.getAmount() == 1) {
            state.nestedArmed = false;
            state.actor.getAdvancements().reload(state.nested);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onProgress(AdvancementEvent.AdvancementProgressEvent event) {
        var state = active;
        if (state == null || state.finished || event.getEntity() != state.actor) { return; }
        if (event.getAdvancement().id().equals(OLD_TICK)) { state.oldTickEvents++; }
        if (event.getAdvancement().id().equals(NEW_TICK)) { state.newTickEvents++; }
        if (state.capturedNext != null && event.getAdvancement().id().equals(CAPTURED)) {
            state.capturedEvents++;
            if (state.capturedEvents == 1) {
                state.capturedFirst = event.getCriterionName();
                var pa = state.actor.getAdvancements();
                pa.reload(state.capturedNext);
                require(pa.award(state.capturedNext.get(NESTED_CURRENT), "current"),
                        "nested current-tree award did not run original native body");
            } else if (state.capturedEvents == 2) {
                state.capturedSecond = event.getAdvancementProgress();
            }
        }
    }

    private static FixtureManager manager(State state, Map<ResourceLocation, JsonElement> definitions, int automaticXp) {
        return new FixtureManager(state.server, definitions, automaticXp);
    }

    private static LinkedHashMap<ResourceLocation, JsonElement> rootDefinitions(State state, String... criteria) {
        var definitions = new LinkedHashMap<>(state.base);
        var json = JsonParser.parseString("""
                {"display":{"icon":{"id":"minecraft:stone"},"title":"P11 canonical",
                 "description":"Excluded native probe","frame":"task","show_toast":false,
                 "announce_to_chat":false,"hidden":false},"criteria":{}}
                """).getAsJsonObject();
        var entries = json.getAsJsonObject("criteria");
        for (var name : criteria) { entries.add(name, JsonParser.parseString("{\"trigger\":\"minecraft:impossible\"}")); }
        definitions.put(ROOT, json);
        return definitions;
    }

    private static JsonElement tickDefinition() {
        return JsonParser.parseString("{\"criteria\":{\"tick\":{\"trigger\":\"minecraft:tick\"}}}");
    }

    private static void next(State state, Phase phase) {
        state.requiredClear = state.clientClears;
        state.clientRootDone = false;
        state.clientCompleted = null;
        state.phase = phase;
    }

    private static Report finish(State state, Throwable failure) {
        state.finished = true;
        state.observations.add("measurementScope=EXACT_PROBE_ACTOR_NATIVE_RELOAD_COPY_RESET_UNTIL_REPORT");
        state.observations.add("copyBytesMeaning=ACTUAL_INTERMEDIATE_JSON_COMPACT_UTF8_REPRESENTATION_NOT_HEAP_CAP");
        state.observations.add("copyCalls=" + state.copyCalls + ";failures=" + state.copyFailures
                + ";encodedUtf8BytesTotal=" + state.copyEncodedBytes + ";max=" + state.copyMaxEncodedBytes
                + ";liveEquivalentBytes=" + state.liveCopyBytes + ";peak=" + state.peakLiveCopyBytes);
        state.observations.add("copyScopePeak=" + state.copyPeakScopes + ";live=" + state.copyScopes
                + ";nanosExcludingUtf8Counting=" + state.copyNanos + ";max=" + state.copyMaxNanos
                + ";utf8CountingNanos=" + state.copySizeNanos);
        state.observations.add("reloadScopePeak=" + state.reloadPeakScopes + ";live=" + state.reloadScopes
                + ";instrumentedNanosTotal=" + state.reloadNanos);
        state.observations.add("resetBuildAttempts=" + state.resetBuildAttempts + ";completed=" + state.resetBuilds
                + ";failed=" + state.resetBuildFailures + ";constructorNanos=" + state.resetBuildNanos
                + ";added=" + state.resetBuiltAdded + ";removed=" + state.resetBuiltRemoved
                + ";progress=" + state.resetBuiltProgress);
        state.observations.add("resetNativeSendAttempts=" + state.resetSendAttempts
                + ";normalSubmissions=" + state.resetSubmissions + ";added=" + state.resetSubmittedAdded
                + ";removed=" + state.resetSubmittedRemoved + ";progress=" + state.resetSubmittedProgress);
        String detail = failure == null ? "NONE" : failure.getClass().getName() + ": " + failure.getMessage();
        if (detail.length() > 768) { detail = detail.substring(0, 768); }
        var report = new Report(failure == null, state.observations, detail);
        state.report = report;
        try { Files.writeString(state.output.resolve("report.txt"), report.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW); }
        catch (IOException outputFailure) {
            state.report = new Report(false, state.observations, "EVIDENCE_WRITE: " + outputFailure.getClass().getName());
        }
        return state.report;
    }

    private static final class FixtureManager extends ServerAdvancementManager {
        private final AdvancementHolder automatic;
        FixtureManager(MinecraftServer server, Map<ResourceLocation, JsonElement> definitions, int xp) {
            super(server.registryAccess());
            injectContext(ICondition.IContext.EMPTY, server.registryAccess());
            super.apply(definitions, server.getResourceManager(), InactiveProfiler.INSTANCE);
            require(getAllAdvancements().size() == definitions.size(), "native manager rejected a fixture definition");
            automatic = xp == 0 ? null : Advancement.Builder.advancement()
                    .rewards(AdvancementRewards.Builder.experience(xp)).build(AUTO);
            if (automatic != null) { tree().addAll(List.of(automatic)); }
        }
        @Override public AdvancementHolder get(ResourceLocation id) {
            return automatic != null && automatic.id().equals(id) ? automatic : super.get(id);
        }
        @Override public Collection<AdvancementHolder> getAllAdvancements() {
            if (automatic == null) { return super.getAllAdvancements(); }
            var values = new ArrayList<>(super.getAllAdvancements());
            values.add(automatic);
            return values;
        }
    }

    private static final class ClientObserver implements ClientAdvancements.Listener {
        private final State state;
        ClientObserver(State state) { this.state = state; }
        @Override public void onAddAdvancementRoot(AdvancementNode node) {}
        @Override public void onRemoveAdvancementRoot(AdvancementNode node) {}
        @Override public void onAddAdvancementTask(AdvancementNode node) {}
        @Override public void onRemoveAdvancementTask(AdvancementNode node) {}
        @Override public void onAdvancementsCleared() { state.clientClears++; state.clientRootDone = false; }
        @Override public void onUpdateAdvancementProgress(AdvancementNode node, AdvancementProgress progress) {
            if (node.holder().id().equals(ROOT)) { state.clientRootDone = progress.isDone(); }
        }
        @Override public void onSelectedTabChanged(AdvancementHolder holder) {
            state.clientSelected = holder == null ? null : holder.id();
        }
    }

    private enum Phase { WAIT_CLIENT, FIRST, RESET, ZERO, RESTORED }
    private static final class State {
        final MinecraftServer server;
        final ServerPlayer actor;
        final Path output;
        final ServerAdvancementManager original;
        final Map<ResourceLocation, JsonElement> base = new LinkedHashMap<>();
        final List<String> observations = new ArrayList<>();
        final SendFault sendFault = new SendFault();
        final CopyFault copyFault = new CopyFault();
        volatile Phase phase = Phase.WAIT_CLIENT, clientCompleted;
        volatile boolean clientReady, clientAttached, clientRootDone, finished;
        volatile int clientClears, requiredClear;
        volatile ResourceLocation clientSelected;
        FixtureManager firstManager, nested;
        FixtureManager capturedNext;
        AdvancementProgress capturedSecond;
        String capturedFirst;
        int capturedEvents;
        Map<ResourceLocation, JsonElement> currentDefinitions;
        boolean nestedArmed, failResetSend, failCopy;
        int polls, automaticEvents, oldTickEvents, newTickEvents, sendFaults, copyFaults;
        long copyCalls, copyFailures, copyScopes, copyPeakScopes, copyNanos, copyMaxNanos, copySizeNanos;
        long copyEncodedBytes, copyMaxEncodedBytes, liveCopyBytes, peakLiveCopyBytes;
        long reloadScopes, reloadPeakScopes, reloadNanos;
        long resetBuildAttempts, resetBuilds, resetBuildFailures, resetBuildNanos;
        long resetBuiltAdded, resetBuiltRemoved, resetBuiltProgress;
        long resetSendAttempts, resetSubmissions, resetSubmittedAdded, resetSubmittedRemoved, resetSubmittedProgress;
        ReloadMeasurement reloadMeasurement;
        Report report;
        State(MinecraftServer server, ServerPlayer actor, Path output) {
            this.server = server; this.actor = actor; this.output = output; original = server.getAdvancements();
        }
    }

    private static final class SendFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        SendFault() { super("P11_ENGINEERING_EXACT_RESET_SEND_FAILURE"); }
    }
    private static final class CopyFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        CopyFault() { super("P11_ENGINEERING_EXACT_NATIVE_COPY_FAILURE"); }
    }
    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("gramarye_p11_engineering", path);
    }
    private static void require(boolean condition, String detail) {
        if (!condition) { throw new IllegalStateException(detail); }
    }
}
