package com.yo1no.gramarye;

import com.electronwill.nightconfig.toml.TomlParser;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

/**
 * Excluded server-only companion. The controller must first prove both normal online-mode
 * hasJoined -> configuration -> PLAY chains. This helper neither authenticates nor creates
 * actors, source receipts, accounts, roots or accepted P5 work.
 */
@EventBusSubscriber(modid = Gramarye.MOD_ID)
public final class P11OnlineNativeContextProbe {
    private static final ResourceLocation BREAD = ResourceLocation.withDefaultNamespace("bread");
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.withDefaultNamespace("adventure/sleep_in_bed");
    private static final String ALPHA_TAG = "p11_online_context_alpha";
    private static final String BETA_TAG = "p11_online_context_beta";
    private static final String ALPHA_MARKER = "p11_online_context_alpha_marker";
    private static final String BETA_MARKER = "p11_online_context_beta_marker";
    private static State active;

    private P11OnlineNativeContextProbe() {}

    record Report(boolean passed, int expectedManaged, int actualManaged,
            List<String> observations, String failure) {
        Report { observations = List.copyOf(observations); }
        @Override public String toString() {
            return "P11-ONLINE-NATIVE-CONTEXT-V1\n"
                    + "scope=DEDICATED_TWO_REAL_ROSTER_ACTORS_NATIVE_COMPONENT\n"
                    + "authenticationEvidence=CONTROLLER_EXACT_HAS_JOINED_TO_PLAY_REQUIRED\n"
                    + "syntheticPlayers=false\nsourceReceiptsFabricated=false\n"
                    + "generalExtendedAdmission=CLOSED_PENDING_C4A\np5L1=false\n"
                    + "expectedManaged=" + expectedManaged + "\nactualManaged=" + actualManaged + '\n'
                    + String.join("\n", observations) + '\n'
                    + "failure=" + failure + "\nRESULT=" + (passed ? "PASS" : "FAIL") + '\n';
        }
    }

    static Report run(MinecraftServer server, Path output, boolean expectCapacityFull) {
        int expectedManaged = expectCapacityFull ? 1 : 2;
        var observations = new ArrayList<String>();
        State state = null;
        int actualManaged = 0;
        String failure = "NONE";
        try {
            require(active == null && server.isSameThread() && server.isDedicatedServer()
                            && server.usesAuthentication(), "requires isolated online-mode dedicated server thread");
            require(output.isAbsolute() && output.equals(output.normalize()) && output.getParent() != null
                            && Files.isDirectory(output.getParent(), LinkOption.NOFOLLOW_LINKS)
                            && !Files.isSymbolicLink(output.getParent()), "requires fresh owned evidence child");
            Files.createDirectory(output);
            var players = List.copyOf(server.getPlayerList().getPlayers());
            require(players.size() == 2, "requires exactly two genuine normal roster players");
            var alpha = players.get(0);
            var beta = players.get(1);
            requireOnline(server, alpha);
            requireOnline(server, beta);
            require(alpha != beta && !alpha.getUUID().equals(beta.getUUID())
                            && alpha.connection.getConnection() != beta.connection.getConnection()
                            && alpha.getStats() != beta.getStats() && alpha.getAdvancements() != beta.getAdvancements(),
                    "distinct online accounts must have distinct native owners and transports");
            var owner = P11NativeStorageBoundary.nativeSourceOwner(alpha);
            require(owner != null && owner == P11NativeStorageBoundary.nativeSourceOwner(beta),
                    "both actors must be observed by the same existing foundation root");
            var alphaBody = owner.nativeRecipient(alpha);
            var betaBody = owner.nativeRecipient(beta);
            actualManaged = (alphaBody == null ? 0 : 1) + (betaBody == null ? 0 : 1);
            require(alphaBody != null && owner.canSerialize(alphaBody)
                            && actualManaged == expectedManaged
                            && (expectCapacityFull ? !owner.hasAccount(beta.getUUID()) && betaBody == null
                                    : betaBody != null && owner.canSerialize(betaBody)),
                    "normal join order did not produce expected qualified/unmanaged enrollment");
            if (betaBody != null) {
                require(alphaBody.account != betaBody.account, "different UUIDs share an account owner");
            }
            verifyConfiguration(server, expectedManaged, observations);
            var before = owner.diagnostics(alpha.getUUID());
            require(before.resources().retainedUuids() == expectedManaged,
                    "real retained UUID count does not match the isolated startup capacity");
            requireNoContexts(alphaBody, betaBody);
            var advancement = server.getAdvancements().get(ADVANCEMENT);
            require(advancement != null && advancement.value().criteria().size() == 1,
                    "expected bounded vanilla advancement fixture is absent or changed");
            String runId = System.getProperty("gramarye.p11.online.runId", "");
            require(runId.matches("[A-Za-z0-9_-]{1,64}"), "missing bounded isolated run identity");
            for (int index = 0; index < players.size(); index++) {
                var actor = players.get(index);
                require(!actor.getRecipeBook().contains(BREAD)
                                && !actor.getAdvancements().getOrStartProgress(advancement).isDone()
                                && !actor.getTags().contains(ALPHA_TAG) && !actor.getTags().contains(BETA_TAG),
                        "native fixtures must be fresh; no revoke/reward replay setup is permitted");
                observations.add("role=" + (index == 0 ? "alpha" : "beta")
                        + ";actorHash=" + sha256(runId + '\n' + actor.getUUID())
                        + ";managed=" + (index == 0 || !expectCapacityFull));
            }
            requireNoMarkers(server);
            state = new State(server, alpha, beta, owner, alphaBody, betaBody, advancement,
                    expectedManaged, observations);
            active = state;
            execute(state);
            removeMarkers(server);
            observations.add("markerCleanup=ORIGINAL_NATIVE_DISCARD_BEFORE_SAVE");
            require(P11NativeOperationBoundary.observerFailureCount() == state.observerFailures,
                    "native operation observer reported a secondary failure");
            requireOnline(server, alpha);
            requireOnline(server, beta);
            verifyEnrollment(state);
            requireNoContexts(alphaBody, betaBody);
            write(output.resolve("before-save.txt"), owner.diagnostics(alpha.getUUID()) + "\n"
                    + owner.diagnostics(beta.getUUID()) + '\n');
            require(server.saveEverything(true, false, false), "original native saveEverything did not complete");
            verifySaved(state, output, alpha, "alpha", ALPHA_TAG, BETA_TAG, state.alphaStat + 2);
            verifySaved(state, output, beta, "beta", BETA_TAG, ALPHA_TAG, state.betaStat + 3);
            verifyEnrollment(state);
            requireNoContexts(alphaBody, betaBody);
            write(output.resolve("after-save.txt"), owner.diagnostics(alpha.getUUID()) + "\n"
                    + owner.diagnostics(beta.getUUID()) + '\n');
            observations.add("nativeSave=ORIGINAL_PER_UUID_PLAYER_STATS_PA_WRITERS_AND_PHYSICAL_READBACK");
            observations.add("enrollment=" + (expectCapacityFull
                    ? "T_FULL_ALPHA_RETAINED_BETA_UNMANAGED_NATIVE_COMMAND_REWARD_PA_STATS_AND_SAVE_SUCCEEDED"
                    : "TWO_DISTINCT_QUALIFIED_ACCOUNTS_SAME_QCTX_DEDUP_AND_FINALLY_RELEASE"));
        } catch (IOException | RuntimeException | Error problem) {
            failure = problem.getClass().getName() + ": " + problem.getMessage();
            if (failure.length() > 768) { failure = failure.substring(0, 768); }
        } finally {
            active = null;
            if (state != null) {
                try { removeMarkers(state.server); }
                catch (RuntimeException | Error cleanup) {
                    observations.add("markerCleanup=FAILED_" + cleanup.getClass().getName());
                    if ("NONE".equals(failure)) { failure = "owned native marker cleanup failed"; }
                }
            }
        }
        return new Report("NONE".equals(failure), expectedManaged, actualManaged, observations, failure);
    }

    private static void execute(State state) {
        var source = state.alpha.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        String alpha = state.alpha.getUUID().toString();
        String beta = state.beta.getUUID().toString();
        var function = CommandFunction.fromLines(ResourceLocation.fromNamespaceAndPath(
                        "gramarye_p11_engineering", "online_two_actor_context"),
                state.server.getCommands().getDispatcher(), source, List.of(
                        "execute as " + alpha + " run experience add @s 3 points",
                        "execute as " + beta + " run experience add @s 5 points",
                        "execute as " + beta + " run experience add @s 7 points",
                        "execute as " + alpha + " run tag @s add " + ALPHA_TAG,
                        "execute as " + beta + " run tag @s add " + BETA_TAG,
                        "execute as " + alpha + " at " + beta
                                + " positioned ~4 ~2 ~6 run summon minecraft:marker ~ ~ ~ {Tags:[\"" + ALPHA_MARKER + "\"]}",
                        "execute as " + beta + " at " + alpha
                                + " positioned ~-4 ~2 ~-6 run summon minecraft:marker ~ ~ ~ {Tags:[\"" + BETA_MARKER + "\"]}"));
        Commands.executeCommandInContext(source, context -> {
            state.context = context;
            require(root(state.alphaBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                            && root(state.betaBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                            && P11NativeOperationBoundary.observe(context).retainedBindings() == 1,
                    "initial context must bind only its exact original alpha before first enqueue");
            state.server.getFunctions().execute(function, source);
            require(state.xpCalls == 0 && state.alpha.totalExperience == state.alphaXp
                            && state.beta.totalExperience == state.betaXp
                            && !P11NativeOperationBoundary.observe(context).terminal(),
                    "reused native function invocation must enqueue without draining or finishing context");
            // This is a test observation/action in the original engine, never a production
            // cleanup sentinel: terminal checks occur only after the real outer method exits.
            context.queueNext(new CommandQueueEntry<>(
                    new Frame(0, CommandResultCallback.EMPTY, () -> {}), (queue, frame) -> {
                        require(queue == state.context && state.xpCalls == 3, "native commands did not execute before observation");
                        verifyContext(state);
                        requireMarkers(state);
                        AdvancementRewards.Builder.experience(11).addRecipe(BREAD).build().grant(state.alpha);
                        AdvancementRewards.Builder.experience(13).addRecipe(BREAD).build().grant(state.beta);
                        String criterion = state.advancement.value().criteria().keySet().iterator().next();
                        require(state.alpha.getAdvancements().award(state.advancement, criterion)
                                        && state.beta.getAdvancements().award(state.advancement, criterion),
                                "first native PA criterion mutation failed");
                        state.alpha.awardStat(Stats.JUMP, 2);
                        state.beta.awardStat(Stats.JUMP, 3);
                        verifyContext(state);
                        state.observations.add("beforeOuterFinally=" + P11NativeOperationBoundary.observe(context));
                    }));
        });
        var ended = P11NativeOperationBoundary.observe(state.context);
        require(ended.terminal() && ended.outerNormal() && "EMPTY".equals(ended.drain())
                        && ended.tracerClosed() && ended.retainedBindings() == 0,
                "actual owning Commands finally failed to release all context bindings");
        require(state.xpCalls == 5 && state.alpha.totalExperience == state.alphaXp + 14
                        && state.beta.totalExperience == state.betaXp + 25
                        && state.alpha.position().equals(state.alphaPosition) && state.beta.position().equals(state.betaPosition)
                        && state.alpha.getTags().contains(ALPHA_TAG) && !state.alpha.getTags().contains(BETA_TAG)
                        && state.beta.getTags().contains(BETA_TAG) && !state.beta.getTags().contains(ALPHA_TAG),
                "different-UUID native execution, reward recipient or native coordinates were rewritten");
        state.observations.add("contextTerminal=" + ended);
        state.observations.add("nativeDerivedSource=PARSED_EXECUTE_AS_DISTINCT_UUID_AT_OTHER_PLAYER_POSITIONED_NATIVE_MARKERS"
                + ";alphaXpDelta=14;betaXpDelta=25;repeatBetaContextBindings=ONE_OR_UNMANAGED_ZERO");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void xp(PlayerXpEvent.XpChange event) {
        var state = active;
        if (state == null || event.getAmount() == 0) { return; }
        require(event.getEntity() == state.alpha || event.getEntity() == state.beta,
                "unexpected third actor in isolated synchronous XP observation");
        int index = state.xpCalls++;
        require(index < 5, "unexpected native XP replay");
        ServerPlayer expectedActor = index == 0 || index == 3 ? state.alpha : state.beta;
        int expectedAmount = switch (index) { case 0 -> 3; case 1 -> 5; case 2 -> 7; case 3 -> 11; default -> 13; };
        require(event.getEntity() == expectedActor && event.getAmount() == expectedAmount,
                "actual native XP callback does not match its original execute-as/reward recipient");
        var body = expectedActor == state.alpha ? state.alphaBody : state.betaBody;
        require(body == null ? !state.owner.hasAccount(expectedActor.getUUID())
                        : root(body, P11ControlBudgets.Root.OPERATION) > 0
                                && root(body, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1,
                "actual recipient lacks its own scope or repeated target duplicated Qctx");
        int expectedBindings = index == 0 ? 1 : state.expectedManaged;
        require(P11NativeOperationBoundary.observe(state.context).retainedBindings() == expectedBindings,
                "derived target did not enroll exactly its own existing account in the actual context");
        state.observations.add("xpCallback=" + index + ";role=" + (expectedActor == state.alpha ? "alpha" : "beta")
                + ";amount=" + expectedAmount + ";ownFop=" + root(body, P11ControlBudgets.Root.OPERATION)
                + ";ownQctx=" + root(body, P11ControlBudgets.Root.COMMAND_CONTEXT)
                + ";contextBindings=" + expectedBindings);
    }

    private static void verifyContext(State state) {
        require(state.context != null && !P11NativeOperationBoundary.observe(state.context).terminal()
                        && P11NativeOperationBoundary.observe(state.context).retainedBindings() == state.expectedManaged
                        && root(state.alphaBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == 1
                        && root(state.betaBody, P11ControlBudgets.Root.COMMAND_CONTEXT) == (state.betaBody == null ? 0 : 1),
                "native context roots changed before owning outer finally");
        verifyEnrollment(state);
    }

    private static void verifyEnrollment(State state) {
        require(state.owner.nativeRecipient(state.alpha) == state.alphaBody
                        && state.owner.nativeRecipient(state.beta) == state.betaBody
                        && state.owner.diagnostics(state.alpha.getUUID()).resources().retainedUuids() == state.expectedManaged
                        && (state.betaBody != null || !state.owner.hasAccount(state.beta.getUUID())),
                "native work changed account enrollment or fabricated a managed target at capacity");
    }

    private static void verifySaved(State state, Path output, ServerPlayer actor, String role,
            String ownTag, String otherTag, int expectedStat) throws IOException {
        var playerPath = state.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(actor.getUUID() + ".dat");
        var statsPath = state.server.getWorldPath(LevelResource.PLAYER_STATS_DIR).resolve(actor.getUUID() + ".json");
        var advancementPath = state.server.getWorldPath(LevelResource.PLAYER_ADVANCEMENTS_DIR).resolve(actor.getUUID() + ".json");
        var saved = NbtIo.readCompressed(playerPath, NbtAccounter.create(32L * 1024 * 1024));
        require(saved.getInt("XpTotal") == actor.totalExperience
                        && saved.getList("Tags", Tag.TAG_STRING).contains(StringTag.valueOf(ownTag))
                        && !saved.getList("Tags", Tag.TAG_STRING).contains(StringTag.valueOf(otherTag))
                        && saved.getCompound("recipeBook").getList("recipes", Tag.TAG_STRING).contains(StringTag.valueOf(BREAD.toString())),
                "original " + role + " player file lost native per-actor mutation/reward");
        var stats = JsonParser.parseString(Files.readString(statsPath, StandardCharsets.UTF_8)).getAsJsonObject();
        require(actor.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)) == expectedStat
                        && stats.getAsJsonObject("stats").getAsJsonObject("minecraft:custom")
                                .get(Stats.JUMP.toString()).getAsInt() == expectedStat,
                "original " + role + " stats owner/file lost native mutation");
        var advancements = JsonParser.parseString(Files.readString(advancementPath, StandardCharsets.UTF_8)).getAsJsonObject();
        require(advancements.getAsJsonObject(ADVANCEMENT.toString()).get("done").getAsBoolean()
                        && actor.getAdvancements().getOrStartProgress(state.advancement).isDone(),
                "original " + role + " PA owner/file lost first completion");
        var body = state.owner.nativeRecipient(actor);
        if (body != null) {
            var diagnostic = state.owner.diagnostics(actor.getUUID());
            for (String kind : List.of("PLAYER_DATA", "STATISTICS", "ADVANCEMENTS")) {
                var writer = diagnostic.writers().stream().filter(value -> kind.equals(value.kind())).findFirst().orElseThrow();
                require(!writer.dirty() && "COMPLETED".equals(writer.terminal())
                                && "SUCCEEDED".equals(writer.encode()) && "SUCCEEDED".equals(writer.write())
                                && "SUCCEEDED".equals(writer.close()), "managed physical writer did not complete: " + kind);
            }
        } else {
            require("UNMANAGED".equals(state.owner.diagnostics(actor.getUUID()).sourceFault()),
                    "unmanaged native writer gained fabricated qualification");
        }
        Files.copy(playerPath, output.resolve(role + "-player.dat"));
        Files.copy(statsPath, output.resolve(role + "-stats.json"));
        Files.copy(advancementPath, output.resolve(role + "-advancements.json"));
        state.observations.add("readback=" + role + ";xp=" + actor.totalExperience
                + ";jump=" + expectedStat + ";bread=true;advancementDone=true;ownTagOnly=true;managed=" + (body != null));
    }

    private static void verifyConfiguration(MinecraftServer server, int expected, List<String> observations) throws IOException {
        var config = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(P5ServerRuntimeConfig.CONFIG_FILE_NAME);
        require(Files.isRegularFile(config, LinkOption.NOFOLLOW_LINKS) && Files.size(config) <= 65536,
                "owned server startup configuration is absent or unexpectedly large");
        byte[] bytes = Files.readAllBytes(config);
        var parsed = new TomlParser().parse(new java.io.StringReader(new String(bytes, StandardCharsets.UTF_8)));
        var decoded = P11ConfigurationValidation.decode(parsed);
        require(decoded instanceof P11StartupLoadState.Ready, "owned P11 startup configuration is not structurally valid");
        var limits = ((P11StartupLoadState.Ready) decoded).limits();
        require(limits.maxUuids() == expected && limits.dirtyUuidAdmissionWatermark() == expected,
                "cohort must use its immutable maxUuids/watermark configuration, not live budget mutation");
        observations.add("ownedConfigurationSha256=" + sha256(bytes) + ";maxUuids=" + limits.maxUuids()
                + ";dirtyWatermark=" + limits.dirtyUuidAdmissionWatermark()
                + ";startupIdentityEvidence=CONTROLLER_BEFORE_LAUNCH_SNAPSHOT_REQUIRED");
    }

    private static void requireOnline(MinecraftServer server, ServerPlayer actor) {
        require(actor != null && !actor.isFakePlayer() && actor.getServer() == server && !actor.isRemoved()
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.player == actor
                        && actor.connection.getConnection().isConnected()
                        && actor.connection.getConnection().getPacketListener() == actor.connection
                        && P11NativeStorageBoundary.nativeDeliveryEligible(actor),
                "actor is not the exact current normal PLAY roster/transport recipient");
    }

    private static long root(P11QualifiedSourceOwner.Body body, P11ControlBudgets.Root kind) {
        return body == null ? 0 : body.account.nativeCounts[kind.ordinal()];
    }

    private static void requireNoContexts(P11QualifiedSourceOwner.Body alpha, P11QualifiedSourceOwner.Body beta) {
        require(root(alpha, P11ControlBudgets.Root.OPERATION) == 0 && root(beta, P11ControlBudgets.Root.OPERATION) == 0
                        && root(alpha, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0
                        && root(beta, P11ControlBudgets.Root.COMMAND_CONTEXT) == 0,
                "native operation/context roots leaked beyond actual finally");
    }

    private static void requireMarkers(State state) {
        var alpha = state.beta.serverLevel().getEntities(EntityType.MARKER, entity -> entity.getTags().contains(ALPHA_MARKER));
        var beta = state.alpha.serverLevel().getEntities(EntityType.MARKER, entity -> entity.getTags().contains(BETA_MARKER));
        require(alpha.size() == 1 && beta.size() == 1
                        && alpha.getFirst().position().distanceToSqr(state.betaPosition.add(4, 2, 6)) < 1.0E-10
                        && beta.getFirst().position().distanceToSqr(state.alphaPosition.add(-4, 2, -6)) < 1.0E-10,
                "native execute-at/positioned coordinates or target dimension were rewritten");
    }

    private static void requireNoMarkers(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            require(level.getEntities(EntityType.MARKER, entity -> entity.getTags().contains(ALPHA_MARKER)
                            || entity.getTags().contains(BETA_MARKER)).isEmpty(), "owned native markers already exist");
        }
    }

    private static void removeMarkers(MinecraftServer server) {
        for (var level : server.getAllLevels()) {
            for (var marker : level.getEntities(EntityType.MARKER, entity -> entity.getTags().contains(ALPHA_MARKER)
                    || entity.getTags().contains(BETA_MARKER))) { marker.discard(); }
        }
        requireNoMarkers(server);
    }

    private static String sha256(String value) { return sha256(value.getBytes(StandardCharsets.UTF_8)); }
    private static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }
    private static void write(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    private static final class State {
        final MinecraftServer server;
        final ServerPlayer alpha, beta;
        final P11QualifiedSourceOwner owner;
        final P11QualifiedSourceOwner.Body alphaBody, betaBody;
        final AdvancementHolder advancement;
        final int expectedManaged, alphaXp, betaXp, alphaStat, betaStat;
        final Vec3 alphaPosition, betaPosition;
        final List<String> observations;
        final long observerFailures = P11NativeOperationBoundary.observerFailureCount();
        ExecutionContext<CommandSourceStack> context;
        int xpCalls;
        State(MinecraftServer server, ServerPlayer alpha, ServerPlayer beta, P11QualifiedSourceOwner owner,
                P11QualifiedSourceOwner.Body alphaBody, P11QualifiedSourceOwner.Body betaBody,
                AdvancementHolder advancement, int expectedManaged, List<String> observations) {
            this.server = server; this.alpha = alpha; this.beta = beta; this.owner = owner;
            this.alphaBody = alphaBody; this.betaBody = betaBody; this.advancement = advancement;
            this.expectedManaged = expectedManaged; this.observations = observations;
            alphaXp = alpha.totalExperience; betaXp = beta.totalExperience;
            alphaStat = alpha.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
            betaStat = beta.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
            alphaPosition = alpha.position(); betaPosition = beta.position();
        }
    }
}
