package com.yo1no.gramarye;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Excluded dedicated-server controller. Authentication receipts are observations, never grants. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
public final class P11OnlineServerHarness {
    private static final ResourceLocation ROOT = ResourceLocation.fromNamespaceAndPath(
            "gramarye_p11_engineering", "delivery_root");
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final IdentityHashMap<Connection, AuthReceipt> AUTH = new IdentityHashMap<>();
    private static final Map<UUID, Actor> ACTORS = new LinkedHashMap<>();
    private static volatile MinecraftServer server;
    private static volatile boolean authenticationOverflow;
    private static Path output;
    private static String runId;
    private static String cohort;
    private static boolean readyOnly;
    private static boolean fixtureDone;
    private static boolean finishIssued;
    private static boolean firstConnectIssued;
    private static boolean secondConnectIssued;
    private static volatile boolean terminal;
    private static long ticks;
    private static long phaseStart;
    private static int expected;
    private static AdvancementRewards fixtureRewards;

    private P11OnlineServerHarness() {}

    /** Called only after the original online hasJoinedServer result's profile() returns. */
    public static synchronized void authenticated(MinecraftServer exact, Connection connection, UUID id) {
        if (System.getProperty("gramarye.p11.online.output") == null || terminal) { return; }
        if (exact != server || connection == null || id == null || AUTH.size() >= 8
                || AUTH.containsKey(connection)) {
            authenticationOverflow = true;
            return;
        }
        AUTH.put(connection, new AuthReceipt(exact, connection, id));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void started(ServerStartedEvent event) {
        if (System.getProperty("gramarye.p11.online.output") == null) { return; }
        try {
            server = event.getServer();
            require(server.isDedicatedServer() && server.usesAuthentication()
                    && !server.isSingleplayer(), "NOT_ONLINE_DEDICATED");
            runId = System.getProperty("gramarye.p11.online.runId");
            require(runId != null && runId.matches("[a-zA-Z0-9_-]{8,64}"), "BAD_RUN_ID");
            cohort = System.getProperty("gramarye.p11.online.case");
            require(List.of("single", "qctx", "capacity").contains(cohort), "BAD_CASE");
            expected = cohort.equals("single") ? 1 : 2;
            readyOnly = Boolean.parseBoolean(System.getProperty("gramarye.p11.online.readyOnly"));
            var root = Path.of(System.getProperty("gramarye.p11.online.output")).toAbsolutePath().normalize();
            require(Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(root), "BAD_OUTPUT");
            output = Files.createDirectory(root.resolve("server"));
            var productHash = P11OnlineInputs.verifyFrozenJar();
            var holder = server.getAdvancements().get(ROOT);
            require(holder != null && holder.value().criteria().containsKey("manual"), "FIXTURE_MISSING");
            fixtureRewards = holder.value().rewards();
            require(fixtureRewards != AdvancementRewards.EMPTY && fixtureRewards.experience() == 0,
                    "FIXTURE_REWARD_MUST_BE_EXPLICIT_DISTINCT_ZERO");
            write("ready.json", Map.of("status", "ONLINE_DEDICATED_READY_NO_AUTH_CLAIM",
                    "case", cohort, "runId", runId, "productionJarSha256", productHash,
                    "onlineMode", true, "integrated", false, "expectedPlayers", expected,
                    "configurationSha256", hash(Files.readAllBytes(server.getWorldPath(LevelResource.ROOT)
                            .resolve("serverconfig/gramarye-server.toml")))));
        } catch (Exception | LinkageError failure) { fail("STARTUP", failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (server == null || terminal || !(event.getEntity() instanceof ServerPlayer player)) { return; }
        try {
            require(player.getServer() == server && server.isSameThread() && !player.isFakePlayer(), "BAD_ACTOR");
            var connection = player.connection.getConnection();
            AuthReceipt receipt;
            synchronized (P11OnlineServerHarness.class) { receipt = AUTH.remove(connection); }
            require(receipt != null && receipt.server == server && receipt.connection == connection
                    && receipt.id.equals(player.getUUID()) && !connection.isMemoryConnection()
                    && connection.isConnected() && connection.isEncrypted(), "NO_EXACT_ONLINE_AUTH_RECEIPT");
            var state = ACTORS.get(player.getUUID());
            if (state == null) {
                require(ACTORS.size() < expected && !fixtureDone, "UNEXPECTED_EXTRA_ACTOR");
                String role = cohort.equals("single") ? "single" : ACTORS.isEmpty() ? "a" : "b";
                state = new Actor(role, player);
                ACTORS.put(player.getUUID(), state);
            } else {
                require(fixtureDone && state.logins == 1 && state.logouts == 1 && state.logoutConfirmed
                        && state.first != player && state.firstConnection != connection, "RECONNECT_ORDER");
                state.current = player;
            }
            state.logins++;
            write(state.role + "-auth-" + state.logins + ".json", Map.of("status", "ONLINE_HAS_JOINED_TO_EXACT_PLAY",
                    "identity", identity(player.getUUID()), "ordinal", state.logins, "realNetwork", true,
                    "encrypted", true, "hasJoinedNonNullProfile", true, "sameExactConnection", true,
                    "newConnection", state.logins == 2));
        } catch (IOException | RuntimeException failure) { fail("LOGIN_PROVENANCE", failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (server == null || terminal || !(event.getEntity() instanceof ServerPlayer player)) { return; }
        var state = ACTORS.get(player.getUUID());
        if (state == null || state.current != player) { fail("UNEXPECTED_LOGOUT", null); return; }
        state.logouts++;
    }

    @SubscribeEvent
    static void earned(AdvancementEvent.AdvancementEarnEvent event) {
        if (server == null || terminal || !event.getAdvancement().id().equals(ROOT)) { return; }
        var state = ACTORS.get(event.getEntity().getUUID());
        if (state != null) { state.progressEvents++; }
    }

    /** Identity comparison only; no reward objects or actors escape the live engineering cohort. */
    public static void grantObserved(AdvancementRewards rewards, ServerPlayer player) {
        if (server == null || terminal || rewards != fixtureRewards || player.getServer() != server) { return; }
        var state = ACTORS.get(player.getUUID());
        if (state != null) { state.rewardCalls++; }
    }

    @SubscribeEvent
    static void tick(ServerTickEvent.Post event) {
        if (server == null || terminal || event.getServer() != server) { return; }
        try {
            ticks++;
            require(!authenticationOverflow, "AUTH_OBSERVER_BOUNDARY");
            if (readyOnly && ticks >= 20) {
                write("readiness-result.json", Map.of("status", "READINESS_ONLY_NO_AUTHENTICATED_PLAYER_TEST",
                        "onlineMode", true, "worldAndProductionLoaded", true));
                terminal = true;
                server.halt(false);
                return;
            }
            // The account holder may take time in the private authorization window. This is
            // an engineering wait bound, not the game's admission deadline or a retry policy.
            require(ticks - phaseStart < 24_000, "COHORT_WAIT_EXPIRED");
            if (!firstConnectIssued) {
                var parent = output.getParent();
                boolean armed = expected == 1 ? Files.isRegularFile(parent.resolve("client-single/inputs.json"), LinkOption.NOFOLLOW_LINKS)
                        : Files.isRegularFile(parent.resolve("client-a/inputs.json"), LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(parent.resolve("client-b/inputs.json"), LinkOption.NOFOLLOW_LINKS);
                if (armed) {
                    cue((expected == 1 ? "single" : "a") + "-connect.ready");
                    firstConnectIssued = true;
                    phaseStart = ticks;
                }
            }
            if (expected == 2 && !secondConnectIssued && ACTORS.size() == 1
                    && ACTORS.values().stream().allMatch(P11OnlineServerHarness::materialReady)) {
                cue("b-connect.ready");
                secondConnectIssued = true;
            }
            if (!fixtureDone && ACTORS.size() == expected && allPresent(1)) {
                if (!ACTORS.values().stream().allMatch(P11OnlineServerHarness::materialReady)) { return; }
                if (expected == 2) {
                    var report = P11OnlineNativeContextProbe.run(server, output.resolve("native-context"), cohort.equals("capacity"));
                    write("native-context-result.json", Map.of("passed", report.passed(),
                            "expectedManaged", report.expectedManaged(), "actualManaged", report.actualManaged(),
                            "observations", report.observations(), "failure", report.failure()));
                    require(report.passed(), "NATIVE_CONTEXT_PROBE_FAILED");
                }
                for (var state : ACTORS.values()) { prepareFixture(state); }
                require(server.saveEverything(false, true, true), "FIRST_NATIVE_SAVE_FAILED");
                for (var state : ACTORS.values()) {
                    state.savedXp = state.current.totalExperience;
                    state.savedEpoch = P11NativeStorageBoundary.diagnostics(server, state.current.getUUID()).sourceEpoch();
                    write(state.role + "-first-native.json", nativeFacts(state));
                    cue(state.role + "-reconnect.ready");
                }
                fixtureDone = true;
                phaseStart = ticks;
            }
            if (!fixtureDone) { return; }
            for (var state : ACTORS.values()) {
                if (state.logouts == 1 && state.logins == 1 && !state.logoutConfirmed
                        && server.getPlayerList().getPlayer(state.first.getUUID()) == null
                        && !state.firstConnection.isConnected()) {
                    state.logoutConfirmed = true;
                    cue(state.role + "-logout.ready");
                }
            }
            if (!finishIssued && allPresent(2)) {
                if (!ACTORS.values().stream().allMatch(P11OnlineServerHarness::materialReady)) { return; }
                for (var state : ACTORS.values()) {
                    require(state.rewardCalls == 1 && state.progressEvents == 1
                            && state.current.totalExperience == state.savedXp, "RECONNECT_REPLAY_OR_LOSS");
                    require(state.current.getAdvancements().getOrStartProgress(server.getAdvancements().get(ROOT)).isDone()
                            && state.current.getRecipeBook().contains(BREAD), "RECONNECT_MISSING_NATIVE_STATE");
                    var owner = P11NativeStorageBoundary.nativeSourceOwner(state.current);
                    var managed = owner != null && owner.hasAccount(state.current.getUUID());
                    if (managed) {
                        require(state.current.getAdvancements() == state.first.getAdvancements()
                                && state.current.getStats() == state.first.getStats(), "CANONICAL_IDENTITY_CHANGED");
                        require(P11NativeStorageBoundary.diagnostics(server, state.current.getUUID()).sourceEpoch()
                                > state.savedEpoch, "RECONNECT_SOURCE_EPOCH_NOT_NEW");
                    }
                }
                require(server.saveEverything(false, true, true), "RECONNECT_SAVE_FAILED");
                for (var state : ACTORS.values()) {
                    write(state.role + "-reconnect-native.json", nativeFacts(state));
                    cue(state.role + "-finish.ready");
                }
                finishIssued = true;
                phaseStart = ticks;
            }
            if (finishIssued && server.getPlayerList().getPlayers().isEmpty()
                    && ACTORS.values().stream().allMatch(s -> s.logouts == 2 && !s.current.connection.getConnection().isConnected())) {
                require(server.saveEverything(false, true, true), "FINAL_SAVE_FAILED");
                write("result.json", Map.of("status", "PASS_AUTHENTICATED_NATIVE_SERVER_COHORT",
                        "case", cohort, "authenticatedDistinctPlayers", ACTORS.size(), "loginsPerIdentity", 2,
                        "fixtureProgressPerIdentity", 1, "fixtureRewardCallsPerIdentity", 1,
                        "normalLogoutPerIdentity", 2, "mainIntegrated", false));
                terminal = true;
                server.halt(false);
            }
        } catch (Exception | LinkageError failure) { fail("NATIVE_COHORT", failure); }
    }

    private static boolean allPresent(int ordinal) {
        return ACTORS.size() == expected && ACTORS.values().stream().allMatch(s -> s.logins == ordinal
                && server.getPlayerList().getPlayer(s.current.getUUID()) == s.current && s.current.connection.isAcceptingMessages());
    }

    private static boolean materialReady(Actor state) {
        var owner = P11NativeStorageBoundary.nativeSourceOwner(state.current);
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, state.current.getUUID());
        if (owner == null || !diagnostic.active()) { return false; }
        boolean expectedManaged = !cohort.equals("capacity") || !state.role.equals("b");
        require(owner.hasAccount(state.current.getUUID()) == expectedManaged, "ROLE_ENROLLMENT_CHANGED");
        if (!expectedManaged) {
            require(owner.nativeRecipient(state.current) == null && diagnostic.sourceFault().equals("UNMANAGED")
                    && diagnostic.sourceEpoch() == 0 && !diagnostic.bodyComplete() && !diagnostic.candidatePresent(),
                    "CAPACITY_SECOND_ACTOR_NOT_UNMANAGED");
            return true;
        }
        var body = owner.nativeRecipient(state.current);
        return body != null && body.actor == state.current && owner.canSerialize(body)
                && diagnostic.bodyComplete() && !diagnostic.candidatePresent();
    }

    private static void prepareFixture(Actor state) {
        var actor = state.current;
        require(actor.getAdvancements().award(server.getAdvancements().get(ROOT), "manual"), "FIXTURE_ALREADY_AWARDED");
        if (expected == 1) { actor.awardRecipes(List.of(server.getRecipeManager().byKey(BREAD).orElseThrow())); }
        require(actor.getRecipeBook().contains(BREAD) && state.progressEvents == 1 && state.rewardCalls == 1,
                "FIXTURE_NATIVE_CONSUMERS");
        actor.getAdvancements().flushDirty(actor);
    }

    private static Map<String, Object> nativeFacts(Actor state) throws IOException {
        var actor = state.current;
        require(materialReady(state), "NATIVE_FACTS_MATERIAL_NOT_CURRENT");
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        boolean managed = !diagnostic.sourceFault().equals("UNMANAGED");
        var facts = new LinkedHashMap<String, Object>();
        facts.put("identity", identity(actor.getUUID()));
        facts.put("logins", state.logins);
        facts.put("managed", managed);
        facts.put("bodyComplete", diagnostic.bodyComplete());
        facts.put("sourceEpoch", diagnostic.sourceEpoch());
        facts.put("progressEvents", state.progressEvents);
        facts.put("fixtureRewardCalls", state.rewardCalls);
        facts.put("xp", actor.totalExperience);
        facts.put("breadKnown", actor.getRecipeBook().contains(BREAD));
        var file = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) < 32L * 1024 * 1024,
                "ENGINEERING_PLAYER_FILE_MISSING_OR_UNEXPECTED_SIZE");
        var nbt = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.create(32L * 1024 * 1024));
        require(nbt.getInt("XpTotal") == actor.totalExperience, "NATIVE_PLAYER_READBACK_XP");
        require(nbt.getCompound("recipeBook").getList("recipes", net.minecraft.nbt.Tag.TAG_STRING)
                .contains(net.minecraft.nbt.StringTag.valueOf(BREAD.toString())), "NATIVE_PLAYER_READBACK_BREAD");
        var advancementFile = server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(actor.getUUID() + ".json");
        require(Files.isRegularFile(advancementFile, LinkOption.NOFOLLOW_LINKS)
                && Files.size(advancementFile) > 0 && Files.size(advancementFile) <= 1024L * 1024,
                "ENGINEERING_PA_FILE_MISSING_OR_UNEXPECTED_SIZE");
        var advancementJson = JsonParser.parseString(Files.readString(advancementFile, StandardCharsets.UTF_8)).getAsJsonObject();
        require(advancementJson.has(ROOT.toString())
                && advancementJson.getAsJsonObject(ROOT.toString()).get("done").getAsBoolean(),
                "NATIVE_PA_READBACK_DELIVERY_ROOT");
        if (managed) {
            for (String kind : List.of("PLAYER_DATA", "STATISTICS", "ADVANCEMENTS")) {
                var writer = diagnostic.writers().stream().filter(value -> kind.equals(value.kind())).findFirst().orElseThrow();
                require(!writer.dirty() && writer.terminal().equals("COMPLETED")
                        && writer.encode().equals("SUCCEEDED") && writer.write().equals("SUCCEEDED")
                        && writer.close().equals("SUCCEEDED")
                        && (!kind.equals("PLAYER_DATA") || writer.replace().equals("SUCCEEDED")),
                        "CURRENT_NATIVE_WRITER_NOT_COMPLETED_" + kind);
            }
            facts.put("currentManagedWriters", diagnostic.writers());
        }
        facts.put("originalPlayerFileSha256", hash(Files.readAllBytes(file)));
        facts.put("originalAdvancementFileSha256", hash(Files.readAllBytes(advancementFile)));
        facts.put("nativeXpReadback", nbt.getInt("XpTotal"));
        facts.put("nativeBreadReadback", true);
        facts.put("nativeDeliveryRootReadback", true);
        return facts;
    }

    private static void cue(String leaf) throws IOException { Files.createFile(output.resolve(leaf)); }

    private static void write(String leaf, Map<String, ?> facts) throws IOException {
        var text = new GsonBuilder().setPrettyPrinting().create().toJson(facts) + "\n";
        Files.writeString(output.resolve(leaf), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static String identity(UUID id) { return hash((runId + "\n" + id).getBytes(StandardCharsets.UTF_8)); }

    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static void require(boolean condition, String code) {
        if (!condition) { throw new HarnessFault(code); }
    }

    private static void fail(String code, Throwable failure) {
        terminal = true;
        if (output != null) {
            try { write("failure.json", Map.of("status", "FAIL", "stage", code,
                    "failureClass", failure == null ? "BOUNDARY" : failure.getClass().getName(),
                    "failureCode", failure instanceof HarnessFault fault ? fault.code : "UNEXPECTED")); }
            catch (IOException ignored) { /* Original local private console remains outside evidence collection. */ }
        }
        var exact = server;
        if (exact != null) { exact.halt(false); }
    }

    @SubscribeEvent
    static void stopped(ServerStoppedEvent event) {
        if (event.getServer() != server) { return; }
        if (output != null) {
            try { write("stopped.json", Map.of("nativeServerStopped", true, "terminalObserved", terminal)); }
            catch (IOException ignored) { }
        }
        synchronized (P11OnlineServerHarness.class) { AUTH.clear(); }
        ACTORS.clear();
        fixtureRewards = null;
        server = null;
    }

    private record AuthReceipt(MinecraftServer server, Connection connection, UUID id) {}
    private static final class HarnessFault extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String code;
        HarnessFault(String code) { super(code); this.code = code; }
    }
    private static final class Actor {
        final String role;
        final ServerPlayer first;
        final Connection firstConnection;
        ServerPlayer current;
        int logins;
        int logouts;
        int progressEvents;
        int rewardCalls;
        int savedXp;
        long savedEpoch;
        boolean logoutConfirmed;
        Actor(String role, ServerPlayer player) {
            this.role = role; first = player; current = player; firstConnection = player.connection.getConnection();
        }
    }
}
