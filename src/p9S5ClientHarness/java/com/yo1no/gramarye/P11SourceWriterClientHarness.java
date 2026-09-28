package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.jar.JarFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Excluded, opt-in actual-client cohort for the native source/writer slice. There is no
 * synthetic actor, replacement writer, reflection, direct submission port or replayed grant.
 * Every artifact is CREATE_NEW in the explicitly supplied, isolated evidence directory.
 */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
final class P11SourceWriterClientHarness {
    static final String OUTPUT_PROPERTY = "gramarye.p11.sourceWriter.output";
    private static final String CASE_PROPERTY = "gramarye.p11.sourceWriter.case";
    private static final String WORLD = "p11-source-writer-owned-world";
    private static final long SEED = 0x503131534f555243L;
    private static final String COMMAND = "gramarye starter";
    private static final int DEADLINE_TICKS = 2_400;
    // This bounds only the tiny engineering fixture reader; it is not a product data limit.
    private static final long FIXTURE_FILE_BOUND = 32L * 1024 * 1024;
    private static final List<String> EVENTS = new ArrayList<>();
    private static volatile Throwable asynchronousFailure;
    private static volatile Phase phase = Phase.BOOTSTRAP;
    private static volatile MinecraftServer activeServer;
    private static volatile int commandCount;
    private static volatile int commandCompletions;
    private static volatile int loginCount;
    private static volatile int logoutCount;
    private static volatile Connection latestLoginTransport;
    private static volatile ClientPacketListener latestLoginListener;
    private static volatile LocalPlayer latestLoginPlayer;
    private static volatile int unownedWorldCleanupCount;
    private static volatile int shutdownOrdinal;
    private static volatile int stopWriterSnapshots;
    private static boolean disconnectInProgress;
    private static volatile boolean serverWorkComplete;
    private static volatile String equippedReference;
    private static MinecraftServer firstServer;
    private static Connection firstTransport;
    private static UUID playerId;
    private static Path output;
    private static Path gameDirectory;
    private static Path worldDirectory;
    private static Path productionJar;
    private static String productionJarSha256;
    private static String configurationSha256;
    private static volatile net.neoforged.fml.config.ModConfig observedConfiguration;
    private static long phaseTicks;
    private static boolean serverWorkScheduled;
    private static boolean terminal;
    private static boolean outputReserved;
    private static CompoundTag firstSavedSkills;
    private static CohortCase cohortCase = CohortCase.POSITIVE;
    private static volatile boolean statisticsFaultInjected;
    private static volatile long failedStatsAttempt;
    private static volatile long failedPlayerAttempt;
    private static volatile boolean playerReplaceFaultInjected;
    private static volatile boolean materialMismatchObserved;
    private static volatile int expectedSlotFailures;
    private static String malformedStatsSha256;
    private static final long P4_OBSERVATION_CASE = 0xB11L;
    private static P4E2QualificationFacade p4Facade;
    private static P4E2QualificationFacade.Session p4Session;
    private static MinecraftServer p4ObservedServer;
    private static UUID p4ObservedPlayer;
    private static volatile int p4LoginArms;
    private static volatile int p4LoginObservations;
    private static volatile int p4RecoveryEntriesCleared;
    private static volatile int p4RecoveryStepsReplayed;
    private static volatile int p4E2SetDataAttempts;
    private static volatile int p4E2SetDataSuccesses;
    private static volatile ServerPlayer handoffOriginal;
    private static volatile Connection handoffServerTransport;
    private static volatile boolean configurationReturned;
    private static volatile boolean handoffCompleted;
    private static volatile int clientHandoffs;
    private static volatile int constructorFaultClientDisconnects;
    private static volatile int clientRespawns;
    private static volatile int serverCloneEvents;
    private static volatile int serverRespawnEvents;
    private static volatile ServerPlayer respawnOriginal;
    private static volatile ServerPlayer respawnClone;
    private static long respawnSourceEpoch;
    private static final String RESPAWN_MARKER = "P11OwnedLatestNativeCopy";
    private static long priorSourceEpoch;
    private static net.minecraft.world.phys.Vec3 logoutPosition;
    private static final String HANDOFF_MARKER = "P11OwnedLatestDetachedBody";

    private P11SourceWriterClientHarness() {}

    private static boolean enabled() { return System.getProperty(OUTPUT_PROPERTY) != null; }

    /** Arms only the existing observation cell before the ordinary NORMAL recovery listener. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void observeNativeP4Login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || terminal || !(event.getEntity() instanceof ServerPlayer actor)) { return; }
        try {
            var server = actor.getServer();
            boolean restart = phase == Phase.REOPEN_PLAY;
            boolean handoff = phase == Phase.HANDOFF_PLAY;
            require(!(actor instanceof FakePlayer) && server != null && server.isSameThread()
                            && (phase == Phase.FIRST_PLAY || restart || handoff)
                            && p4Session == null && p4LoginArms == p4LoginObservations
                            && p4LoginArms == (restart ? (handoffCompleted ? 2 : 1) : handoff ? 1 : 0),
                    "unexpected real P4 login observation scope");
            if (restart || handoff) {
                require(actor.getUUID().equals(playerId), "P4 restart observation changed account");
            }
            if (handoff) {
                var selected = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
                writeArtifact("03-handoff-live-reservations.txt", selected.toString() + "\n");
                boolean memory = cohortCase == CohortCase.MEMORY_HANDOFF;
                require(selected.bodyComplete() && !selected.candidatePresent()
                                && selected.sourceInput().equals(memory ? "MEMORY" : "PRIMARY")
                                && selected.resources().sealedSnapshots() == (memory ? 2 : 0)
                                && (memory ? selected.resources().sealedBytes() > 0
                                        : selected.resources().sealedBytes() == 0),
                        "native login did not retain the exact selected-input reservations through its caller");
            }
            var exactFacade = net.neoforged.fml.ModList.get()
                    .getModContainerById(Gramarye.MOD_ID).orElseThrow()
                    .getCustomExtension(P4E2QualificationFacade.class).orElseThrow();
            require(p4Facade == null || p4Facade == exactFacade,
                    "production P4 qualification facade identity changed");
            p4Facade = exactFacade;
            p4ObservedServer = server;
            p4ObservedPlayer = actor.getUUID();
            p4Session = exactFacade.arm(server,
                    p4ObservedPlayer.getMostSignificantBits(), p4ObservedPlayer.getLeastSignificantBits(),
                    P4_OBSERVATION_CASE, restart || handoff ? P4E2QualificationFacade.Phase.READY_RESTART
                            : P4E2QualificationFacade.Phase.READY_FIRST);
            p4LoginArms = Math.incrementExact(p4LoginArms);
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    /** Aggregate observations of the actual single starter chain; no direct recovery invocation. */
    private static void consumeP4Login(MinecraftServer server, boolean restart) {
        boolean handoff = phase == Phase.HANDOFF_READY;
        boolean physicalHandoff = handoff && cohortCase == CohortCase.SYNCHRONOUS_HANDOFF;
        boolean alreadyCleared = restart && handoffCompleted
                && cohortCase == CohortCase.SYNCHRONOUS_HANDOFF;
        require(server.isSameThread() && p4Session != null && p4ObservedServer == server
                        && p4ObservedPlayer.equals(playerId)
                        && p4LoginArms == p4LoginObservations + 1,
                "completed P4 observation is not bound to this actual native login");
        var snapshot = p4Facade.consume(p4Session);
        p4Session = null;
        p4ObservedServer = null;
        p4ObservedPlayer = null;
        p4LoginObservations = Math.incrementExact(p4LoginObservations);
        p4RecoveryEntriesCleared = Math.addExact(p4RecoveryEntriesCleared, snapshot.entriesCleared());
        p4RecoveryStepsReplayed = Math.addExact(p4RecoveryStepsReplayed, snapshot.stepsReplayed());
        p4E2SetDataAttempts = Math.addExact(p4E2SetDataAttempts, snapshot.setDataAttempts());
        p4E2SetDataSuccesses = Math.addExact(p4E2SetDataSuccesses, snapshot.setDataSuccesses());
        writeArtifact(handoff ? "03-handoff-p4-observation.txt" : restart
                        ? "06-reopened-p4-observation.txt" : "02-first-login-p4-observation.txt",
                "scope=ACTUAL_NATIVE_LOGIN_SINGLE_STARTER_CHAIN_AGGREGATE\n"
                        + "uuid=" + playerId + "\n" + snapshot + "\n"
                        + "recoveryPublicationCount=" + snapshot.stepsReplayed() + "\n"
                        + "e2SetDataAttempts=" + snapshot.setDataAttempts() + "\n"
                        + "e2SetDataSuccesses=" + snapshot.setDataSuccesses() + "\n"
                        + "ordinaryStarterPublicationCount=NOT_MEASURED_BY_THIS_CELL\n"
                        + "perChainRemainingJournalEntries=NOT_PROJECTED_BY_THIS_CELL\n");
        require(snapshot.caseId() == P4_OBSERVATION_CASE
                        && snapshot.phase() == (restart || handoff ? P4E2QualificationFacade.Phase.READY_RESTART
                                : P4E2QualificationFacade.Phase.READY_FIRST)
                        && snapshot.recoveryDetail() == P4E2QualificationFacade.RecoveryDetail.NONE
                        && snapshot.reconciliationDetail() == P4E2QualificationFacade.ReconciliationDetail.NONE
                        && snapshot.continuationCalls() == 1,
                "P4 native recovery/continuation did not complete exactly once");
        // The fresh world has no journal chain. The sole successful starter command appends one
        // entry before Attachment publication; it does not clear that entry. The later actual
        // native persisted read therefore clears exactly that entry without reinstalling it.
        var expectedVariant = handoff && !physicalHandoff
                ? P4E2QualificationFacade.RecoveryVariant.CURRENT_PUBLICATION
                : (restart && !alreadyCleared) || physicalHandoff
                        ? P4E2QualificationFacade.RecoveryVariant.CLEARED
                        : P4E2QualificationFacade.RecoveryVariant.NO_PENDING;
        boolean clear = (restart && !alreadyCleared) || physicalHandoff;
        require(snapshot.recoveryVariant() == expectedVariant
                        && snapshot.entriesCleared() == (clear ? 1 : 0)
                        && snapshot.stepsReplayed() == 0
                        && snapshot.reconciliationVariant() == (clear
                                ? P4E2QualificationFacade.ReconciliationVariant.RECOVERY_CHANGED
                                : P4E2QualificationFacade.ReconciliationVariant.NO_CHANGES)
                        && snapshot.setDataAttempts() == 0 && snapshot.setDataSuccesses() == 0,
                "native P4 readback clear or zero replay/E2 publication contract failed");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void clientTick(ClientTickEvent.Post ignored) {
        if (!enabled() || terminal || disconnectInProgress) { return; }
        var minecraft = Minecraft.getInstance();
        try {
            require(minecraft.isSameThread(), "controller is not on the client thread");
            if (asynchronousFailure != null) {
                throw new IllegalStateException("server-side cohort observation failed", asynchronousFailure);
            }
            require(++phaseTicks <= DEADLINE_TICKS, "phase timeout: " + phase);
            switch (phase) {
                case BOOTSTRAP -> bootstrap(minecraft);
                case FIRST_PLAY -> firstPlay(minecraft);
                case FIRST_READY -> firstReady(minecraft);
                case COMMAND -> commandCompleted(minecraft);
                case HANDOFF_PLAY -> handoffPlay(minecraft);
                case HANDOFF_READY -> handoffReady(minecraft);
                case RESPAWN_ARM -> respawnArmed(minecraft);
                case RESPAWN_PLAY -> respawnPlay(minecraft);
                case RESPAWN_READY -> respawnReady(minecraft);
                case STATS_IO_FAULT -> statsIoFaultComplete(minecraft);
                case MATERIAL_MISMATCH -> materialMismatchComplete(minecraft);
                case PLAYER_REPLACE_FAULT -> playerReplaceFaultComplete(minecraft);
                case FIRST_SAVE -> firstSaveComplete(minecraft);
                case FIRST_STOP -> firstStopComplete(minecraft);
                case REOPEN_PLAY -> reopenPlay(minecraft);
                case REOPEN_READY -> reopenReady(minecraft);
                case SECOND_SAVE -> secondSaveComplete(minecraft);
                case SECOND_STOP -> secondStopComplete(minecraft);
                case TERMINAL -> { }
            }
        } catch (RuntimeException | Error failure) {
            finish(minecraft, failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void loggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!enabled() || terminal) { return; }
        try {
            var minecraft = Minecraft.getInstance();
            var player = event.getPlayer();
            var transport = event.getConnection();
            require(minecraft.isSameThread() && player != null && transport != null
                            && minecraft.player == player && minecraft.getSingleplayerServer() != null
                            && minecraft.getConnection() == player.connection
                            && player.connection.getConnection() == transport
                            && transport.getPacketListener() == player.connection,
                    "PLAY event did not identify the actual integrated client player/listener/transport");
            if (phase == Phase.HANDOFF_PLAY) {
                // Locked clearClientLevel does not emit LoggingOut during reconfiguration.
                // Observe the actual new PLAY objects on the same still-connected transport.
                require(clientHandoffs == 0 && latestLoginPlayer != null
                                && latestLoginPlayer != player && latestLoginListener != player.connection
                                && latestLoginTransport == transport && loginCount == logoutCount + 1,
                        "reconfiguration did not replace exactly one PLAY actor/listener on its transport");
                clientHandoffs = 1;
            } else {
                require((phase == Phase.FIRST_PLAY || phase == Phase.REOPEN_PLAY)
                                && latestLoginPlayer == null && loginCount == logoutCount + clientHandoffs,
                        "unexpected or overlapping actual PLAY login");
            }
            loginCount = Math.incrementExact(loginCount);
            latestLoginPlayer = player;
            latestLoginTransport = transport;
            latestLoginListener = player.connection;
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        if (!enabled() || terminal) { return; }
        try {
            // Locked ClientHooks.firePlayerLogout is also called by world creation with a
            // null LocalPlayer and null Connection. That is not this cohort's PLAY logout.
            if (event.getPlayer() == null && event.getConnection() == null) {
                if (cohortCase == CohortCase.CONSTRUCTOR_FAILURE && P11NativeConstructorFaultProbe.fired()
                        && phase == Phase.HANDOFF_PLAY) {
                    // clearClientLevel already retired the PLAY view. The later original
                    // CONFIG failure disconnect therefore has no LocalPlayer to report.
                    require(latestLoginTransport == firstTransport && !firstTransport.isConnected()
                                    && Minecraft.getInstance().getSingleplayerServer() == null
                                    && constructorFaultClientDisconnects == 0,
                            "constructor-fault disconnect did not retire the retained exact client transport");
                    constructorFaultClientDisconnects = 1;
                    latestLoginPlayer = null;
                    latestLoginTransport = null;
                    latestLoginListener = null;
                    return;
                }
                unownedWorldCleanupCount = Math.incrementExact(unownedWorldCleanupCount);
                return;
            }
            require(Minecraft.getInstance().isSameThread() && latestLoginPlayer != null
                            && event.getPlayer() == latestLoginPlayer
                            && event.getConnection() == latestLoginTransport
                            && event.getPlayer().connection == latestLoginListener
                            && latestLoginListener.getConnection() == latestLoginTransport
                            && loginCount == logoutCount + clientHandoffs + 1,
                    "logout did not retire the exact previously observed PLAY player/listener/transport");
            logoutCount = Math.incrementExact(logoutCount);
            if (cohortCase == CohortCase.CONSTRUCTOR_FAILURE && P11NativeConstructorFaultProbe.fired()) {
                require(constructorFaultClientDisconnects == 0, "duplicate constructor-fault client disconnect");
                constructorFaultClientDisconnects = 1;
            }
            latestLoginPlayer = null;
            latestLoginTransport = null;
            latestLoginListener = null;
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void clientRespawned(ClientPlayerNetworkEvent.Clone event) {
        if (!enabled() || terminal || cohortCase != CohortCase.NATIVE_RESPAWN) { return; }
        try {
            var minecraft = Minecraft.getInstance();
            require(minecraft.isSameThread() && phase == Phase.RESPAWN_PLAY && clientRespawns == 0
                            && event.getOldPlayer() == latestLoginPlayer
                            && event.getNewPlayer() != event.getOldPlayer()
                            && event.getNewPlayer() == minecraft.player
                            && event.getNewPlayer().connection == latestLoginListener
                            && event.getConnection() == firstTransport
                            && latestLoginTransport == firstTransport && firstTransport.isConnected(),
                    "native client respawn did not replace only the exact player on its existing connection");
            latestLoginPlayer = event.getNewPlayer();
            clientRespawns = 1;
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void serverCloned(PlayerEvent.Clone event) {
        if (!enabled() || terminal || cohortCase != CohortCase.NATIVE_RESPAWN) { return; }
        try {
            require(activeServer.isSameThread() && phase == Phase.RESPAWN_PLAY && serverCloneEvents == 0
                            && event.getOriginal() == respawnOriginal && !event.isWasDeath()
                            && event.getEntity() instanceof ServerPlayer next && next != respawnOriginal
                            && next.getServer() == activeServer && next.getUUID().equals(playerId),
                    "native server Clone did not identify this exact non-death successor");
            respawnClone = (ServerPlayer) event.getEntity();
            serverCloneEvents = 1;
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void serverRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (!enabled() || terminal || cohortCase != CohortCase.NATIVE_RESPAWN) { return; }
        try {
            require(activeServer.isSameThread() && (phase == Phase.RESPAWN_PLAY || phase == Phase.RESPAWN_READY)
                            && serverCloneEvents == 1 && serverRespawnEvents == 0
                            && event.getEntity() == respawnClone,
                    "native respawn event did not follow the exact observed Clone");
            serverRespawnEvents = 1;
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void finalNativeWriters(ServerStoppedEvent event) {
        if (!enabled() || terminal || event.getServer() != activeServer) { return; }
        try {
            // Root registers its slot retirement at NORMAL. This excluded observer reads the
            // final original stop writers first; it neither changes nor delays their outcome.
            var diagnostic = P11NativeStorageBoundary.diagnostics(event.getServer(), playerId);
            if (shutdownOrdinal != stopWriterSnapshots + 1 || shutdownOrdinal < 1 || shutdownOrdinal > 2) {
                writeArtifact("unexpected-stop-writers-" + stopWriterSnapshots + ".txt",
                        "phase=" + phase + "\n" + diagnostic + "\n");
            }
            require(event.getServer().isSameThread() && shutdownOrdinal == stopWriterSnapshots + 1
                            && shutdownOrdinal >= 1 && shutdownOrdinal <= 2,
                    "native stop writer observation has no exact owned shutdown");
            writeArtifact(shutdownOrdinal == 1 ? "05-first-stop-final-writers.txt"
                    : "08-second-stop-final-writers.txt", diagnostic.toString() + "\n");
            if (cohortCase == CohortCase.CONSTRUCTOR_FAILURE) {
                P11NativeConstructorFaultProbe.assertFinalWriters(event.getServer());
                writeArtifact("03-constructor-fault-unwind.txt",
                        P11NativeConstructorFaultProbe.afterFailureReport().toString());
            } else if (cohortCase == CohortCase.MALFORMED_STATS && expectedSlotFailures > 0) {
                requireIndependentStatsFault(diagnostic, false);
            } else {
                requireSource(diagnostic, false);
                requireWriters(diagnostic);
            }
            stopWriterSnapshots = shutdownOrdinal;
        } catch (RuntimeException | Error failure) {
            // Preserve the native stop's primary behavior; the client reports our observation.
            recordFailure(failure);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void command(CommandEvent event) {
        if (!enabled() || terminal || !COMMAND.equals(event.getParseResults().getReader().getString())) {
            return;
        }
        try {
            var source = event.getParseResults().getContext().getSource();
            var server = activeServer;
            require(server != null && source.getServer() == server && server.isSameThread(),
                    "starter command has the wrong exact server");
            var actor = currentActor(server);
            require(source.getEntity() == actor && source.source == actor,
                    "starter did not arrive from the exact native player command source");
            require(phase == Phase.COMMAND && ++commandCount == 1,
                    "provisioning was duplicated or reached the wrong phase");
            var node = event.getParseResults().getContext().getLastChild();
            var original = node.getCommand();
            require(original != null, "registered production command executor is absent");
            node.withCommand(context -> {
                int result = original.run(context);
                try {
                    require(result == 1 && ++commandCompletions == 1,
                            "production starter submission/equip did not return exact success");
                    var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
                    writeArtifact("03-command-diagnostics.txt", diagnostic.toString() + "\n");
                    equippedReference = requireReference(diagnostic);
                } catch (RuntimeException | Error failure) { recordFailure(failure); }
                return result;
            });
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    private static void bootstrap(Minecraft minecraft) {
        var configured = System.getProperty(OUTPUT_PROPERTY);
        require(configured != null && !configured.isBlank(), "P11 output path is blank");
        var supplied = Path.of(configured);
        require(supplied.isAbsolute(), "P11 output path must be absolute");
        output = supplied.normalize();
        gameDirectory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
        require(!output.startsWith(gameDirectory) && !gameDirectory.startsWith(output),
                "evidence directory and owned game directory must be disjoint");
        try {
            if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) { Files.createDirectory(output); }
            require(Files.isDirectory(output, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(output),
                    "output is not a regular owned directory");
            try (var entries = Files.list(output)) {
                require(entries.findAny().isEmpty(), "output directory already contains evidence");
            }
            outputReserved = true;
        } catch (IOException failure) { throw new IllegalStateException("output reservation failed", failure); }
        cohortCase = switch (System.getProperty(CASE_PROPERTY, "positive")) {
            case "positive" -> CohortCase.POSITIVE;
            case "malformed-stats" -> CohortCase.MALFORMED_STATS;
            case "stats-io-fault" -> CohortCase.STATS_IO_FAULT;
            case "player-replace-false" -> CohortCase.PLAYER_REPLACE_FALSE;
            case "memory-handoff" -> CohortCase.MEMORY_HANDOFF;
            case "synchronous-handoff" -> CohortCase.SYNCHRONOUS_HANDOFF;
            case "constructor-failure" -> CohortCase.CONSTRUCTOR_FAILURE;
            case "material-mismatch" -> CohortCase.MATERIAL_MISMATCH;
            case "native-respawn" -> CohortCase.NATIVE_RESPAWN;
            default -> throw new IllegalStateException("unknown P11 native cohort case");
        };
        require(minecraft.level == null && minecraft.player == null
                        && minecraft.getConnection() == null && minecraft.getSingleplayerServer() == null,
                "cohort did not start outside a world");
        // Process-local setting in this owned game directory; no user options are saved.
        minecraft.options.pauseOnLostFocus = false;
        worldDirectory = gameDirectory.resolve("saves").resolve(WORLD);
        require(!Files.exists(worldDirectory, LinkOption.NOFOLLOW_LINKS), "owned test world is not fresh");
        captureProductionJar();
        writeConfigurationFixture();
        net.neoforged.fml.ModList.get().getModContainerById(Gramarye.MOD_ID).orElseThrow()
                .getEventBus().addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
                    var config = event.getConfig();
                    if (config.getType() == net.neoforged.fml.config.ModConfig.Type.SERVER
                            && Gramarye.MOD_ID.equals(config.getModId())
                            && P5ServerRuntimeConfig.CONFIG_FILE_NAME.equals(config.getFileName())) {
                        observedConfiguration = config;
                    }
                });
        writeArtifact("00-owned-inputs.txt", "P11-NATIVE-SOURCE-WRITER-V1\nworld=" + worldDirectory
                + "\ncase=" + cohortCase
                + "\nseed=" + SEED + "\nproductionJar=" + productionJar
                + "\nproductionJarSha256=" + productionJarSha256
                + "\nconfigurationSha256=" + configurationSha256 + "\n");
        event("FRESH_FIXED_WORLD_REQUESTED");
        transition(Phase.FIRST_PLAY);
        var settings = new LevelSettings("P11 Source Writer Engineering Cohort", GameType.CREATIVE,
                false, Difficulty.PEACEFUL, false, new GameRules(), WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings,
                new WorldOptions(SEED, false, false), access -> access.registryOrThrow(
                        net.minecraft.core.registries.Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), minecraft.screen);
    }

    private static void writeConfigurationFixture() {
        var fixture = new StringBuilder("# P11 owned engineering fixture; not production defaults.\n");
        for (var key : P5RuntimeLimitKey.values()) {
            fixture.append(P5RawServerConfigSpec.rawPath(key)).append(" = ")
                    .append(P5RuntimeLimitValidation.defaultValue(key)).append('\n');
        }
        // All twelve values are deliberately explicit engineering inputs read by real startup.
        fixture.append("""
                p11.retention.maxUuids = 4
                p11.control.maxWaitingConnections = 4
                p11.control.admissionWaitMillis = 30000
                p11.control.tryBurst = 4
                p11.control.tryRefillPerSecond = 2
                p11.control.statusBurst = 4
                p11.control.statusRefillPerSecond = 2
                p11.control.mainQuantaPerTick = 4
                p11.save.maxSealedSnapshots = 1
                p11.save.maxSealedBytes = 1
                p11.save.dirtyUuidAdmissionWatermark = 4
                p11.save.oldestDirtyWarnMillis = 30000
                """);
        String selectedFixture = fixture.toString();
        if (cohortCase == CohortCase.MEMORY_HANDOFF || cohortCase == CohortCase.CONSTRUCTOR_FAILURE) {
            selectedFixture = selectedFixture.replace("p11.save.maxSealedSnapshots = 1",
                            "p11.save.maxSealedSnapshots = 4")
                    .replace("p11.save.maxSealedBytes = 1", "p11.save.maxSealedBytes = 67108864");
        }
        var bytes = selectedFixture.getBytes(StandardCharsets.UTF_8);
        try {
            var defaults = gameDirectory.resolve("defaultconfigs");
            Files.createDirectories(defaults);
            require(!Files.isSymbolicLink(defaults), "defaultconfigs must not be a symbolic link");
            Files.write(defaults.resolve(P5ServerRuntimeConfig.CONFIG_FILE_NAME), bytes,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            configurationSha256 = sha256(bytes);
            writeArtifact("01-startup-fixture.toml", selectedFixture);
        } catch (IOException failure) { throw new IllegalStateException("owned startup fixture write failed", failure); }
    }

    private static boolean playReady(Minecraft minecraft) {
        var connection = minecraft.getConnection();
        return minecraft.getSingleplayerServer() != null && minecraft.level != null
                && minecraft.player != null && connection != null && minecraft.screen == null
                && connection.getCommands().getRoot().getChild("gramarye") != null;
    }

    private static void verifyClientTransport(Minecraft minecraft) {
        var listener = minecraft.getConnection();
        require(listener != null && latestLoginListener == listener
                        && latestLoginTransport == listener.getConnection()
                        && latestLoginTransport.isConnected()
                        && latestLoginTransport.getPacketListener() == listener,
                "actual CONFIG-to-PLAY login did not identify the exact live transport/listener");
    }

    private static void firstPlay(Minecraft minecraft) {
        if (!playReady(minecraft)) { return; }
        verifyClientTransport(minecraft);
        require(loginCount == 1 && logoutCount == 0, "unexpected initial client lifecycle count");
        firstServer = minecraft.getSingleplayerServer();
        activeServer = firstServer;
        firstTransport = latestLoginTransport;
        playerId = minecraft.player.getUUID();
        writeArtifact("02-owned-player.txt", "uuid=" + playerId + "\nworld=" + WORLD
                + "\nplayerdata=" + playerId + ".dat\nstats=" + playerId
                + ".json\nadvancements=" + playerId + ".json\n");
        transition(Phase.FIRST_READY);
        scheduleServerWork(() -> {
            var server = activeServer;
            currentActor(server);
            require(server.overworld().getSeed() == SEED, "first world seed differs");
            require(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(worldDirectory),
                    "server world path escaped the owned fixture");
            requireCopiedConfiguration();
            var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
            requireSource(diagnostic, false);
            require("ABSENT".equals(diagnostic.sourceInput()),
                    "fresh owned world did not observe true native source absence");
            consumeP4Login(server, false);
            writeArtifact("02-first-play-diagnostics.txt", diagnostic.toString() + "\n");
            if (cohortCase == CohortCase.POSITIVE) {
                var guards = P11NativeGuardProbe.run(server, currentActor(server),
                        output.resolve("native-guards"));
                writeArtifact("02-managed-native-guards.txt", guards.toString() + "\n");
                require(guards.passed(), "actual managed native guard probe failed: " + guards.failure());
                var primitive = P11NativeIoProbe.run(output.resolve("native-primitives"));
                writeArtifact("02-unmanaged-native-primitives.txt",
                        "layer=UNMANAGED_PRIMITIVE_IO\nnotManagedConsumerQualification=true\n"
                                + primitive + "\n");
            }
        });
    }

    private static void firstReady(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        event("NORMAL_NON_OP_PLAYER_READY");
        transition(Phase.COMMAND);
        minecraft.getConnection().sendCommand(COMMAND);
    }

    private static void commandCompleted(Minecraft minecraft) {
        if (commandCompletions != 1 || equippedReference == null) { return; }
        event("PRODUCTION_STARTER_SUBMISSION_EQUIPPED " + equippedReference);
        if (cohortCase == CohortCase.NATIVE_RESPAWN) {
            transition(Phase.RESPAWN_ARM);
            scheduleServerWork(() -> {
                var actor = currentActor(activeServer);
                require(!actor.wonGame, "native respawn precondition was already set unexpectedly");
                respawnOriginal = actor;
                respawnSourceEpoch = P11NativeStorageBoundary.diagnostics(activeServer, playerId).sourceEpoch();
                var persisted = actor.getPersistentData().getCompound(ServerPlayer.PERSISTED_NBT_TAG);
                persisted.putString(RESPAWN_MARKER, "latest-before-original-native-copy");
                actor.getPersistentData().put(ServerPlayer.PERSISTED_NBT_TAG, persisted);
                // Owned engineering precondition only. The genuine client packet invokes the
                // original handler's respawn(A,true,CHANGED_DIMENSION) and returned-B assignment.
                // This does not qualify End travel, credits, advancement criteria, or rewards.
                actor.wonGame = true;
                writeArtifact("03-native-respawn-input.txt", "fixture=OWNED_WON_GAME_PRECONDITION_ONLY\n"
                        + "entry=ACTUAL_CLIENT_PERFORM_RESPAWN_PACKET\nendOrRewardQualification=false\n"
                        + P11NativeStorageBoundary.diagnostics(activeServer, playerId) + "\n");
            });
            return;
        }
        if (cohortCase == CohortCase.MEMORY_HANDOFF || cohortCase == CohortCase.SYNCHRONOUS_HANDOFF
                || cohortCase == CohortCase.CONSTRUCTOR_FAILURE) {
            transition(Phase.HANDOFF_PLAY);
            scheduleServerWork(() -> {
                var actor = currentActor(activeServer);
                handoffOriginal = actor;
                handoffServerTransport = actor.connection.getConnection();
                logoutPosition = actor.position();
                priorSourceEpoch = P11NativeStorageBoundary.diagnostics(activeServer, playerId).sourceEpoch();
                if (cohortCase == CohortCase.CONSTRUCTOR_FAILURE) {
                    P11NativeConstructorFaultProbe.arm(activeServer, actor);
                    shutdownOrdinal = 1; // Original CONFIG failure owns the ensuing native stop.
                }
                actor.connection.switchToConfig();
                require(actor.isRemoved() && activeServer.getPlayerList().getPlayer(playerId) == null,
                        "original native reconfiguration did not complete normal logout cleanup");
                // Engineering-only mutation of the actual detached source tests that later body
                // stays latest while only Pos/RootVehicle/Brain retain normal-logout custody.
                actor.getPersistentData().putString(HANDOFF_MARKER, "latest-after-original-logout-save");
                actor.setPos(logoutPosition.x + 100, logoutPosition.y + 3, logoutPosition.z + 100);
                writeArtifact("03-detached-before-handoff.txt",
                        P11NativeStorageBoundary.diagnostics(activeServer, playerId).toString() + "\n");
            });
            return;
        }
        if (cohortCase == CohortCase.STATS_IO_FAULT) {
            transition(Phase.STATS_IO_FAULT);
            scheduleServerWork(P11SourceWriterClientHarness::exerciseStatsIoFailure);
            return;
        }
        if (cohortCase == CohortCase.MATERIAL_MISMATCH) {
            transition(Phase.MATERIAL_MISMATCH);
            scheduleServerWork(() -> {
                var report = P11NativeMaterialProbe.run(activeServer, currentActor(activeServer),
                        output.resolve("native-material"));
                writeArtifact("04-native-material-mismatch.txt", report.toString());
                require(report.passed() && report.restoredExactState(),
                        "actual native material mismatch probe failed: " + report.failure());
                expectedSlotFailures = Math.addExact(expectedSlotFailures,
                        Math.toIntExact(report.historicalFaultCount()));
                materialMismatchObserved = true;
            });
            return;
        }
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-first-save-diagnostics.txt");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void returnNativeConfiguration(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!enabled() || terminal || phase != Phase.HANDOFF_PLAY || configurationReturned
                || event.getServer() != activeServer || handoffServerTransport == null) { return; }
        try {
            var listener = handoffServerTransport.getPacketListener();
            if (!(listener instanceof ServerConfigurationPacketListenerImpl configuration)) { return; }
            require(activeServer.isSameThread() && configuration.getMainThreadEventLoop() == activeServer
                            && configuration.getOwner().getId().equals(playerId)
                            && handoffServerTransport.isConnected(), "native CONFIG owner changed");
            configurationReturned = true;
            writeArtifact("03-native-config-before-source-selection.txt",
                    P11NativeStorageBoundary.diagnostics(activeServer, playerId).toString() + "\n");
            // NeoForge's native start performs required channel negotiation before JoinWorldTask.
            // Bare returnToWorld skips that handshake on the newly installed client listener.
            configuration.startConfiguration();
        } catch (RuntimeException | Error failure) { recordFailure(failure); }
    }

    private static void handoffPlay(Minecraft minecraft) {
        if (cohortCase == CohortCase.CONSTRUCTOR_FAILURE) {
            constructorFailureComplete(minecraft);
            return;
        }
        if (clientHandoffs != 1 || !playReady(minecraft)) { return; }
        verifyClientTransport(minecraft);
        require(minecraft.getSingleplayerServer() == firstServer && latestLoginTransport == firstTransport
                        && loginCount == 2 && logoutCount == 0 && configurationReturned,
                "native same-server/same-transport handoff did not finish");
        transition(Phase.HANDOFF_READY);
        scheduleServerWork(() -> {
            var actor = currentActor(activeServer);
            require(actor != handoffOriginal && actor.getUUID().equals(handoffOriginal.getUUID()),
                    "handoff did not construct a distinct actual B for the same account");
            var diagnostic = P11NativeStorageBoundary.diagnostics(activeServer, playerId);
            writeArtifact("03-handoff-source.txt", diagnostic.toString() + "\n");
            requireSource(diagnostic, false);
            require(diagnostic.sourceEpoch() > priorSourceEpoch
                            && diagnostic.sourceInput().equals(cohortCase == CohortCase.MEMORY_HANDOFF
                                    ? "MEMORY" : "PRIMARY"),
                    "handoff selected the wrong source or reused old actor authority");
            require(equippedReference.equals(requireReference(diagnostic))
                            && "latest-after-original-logout-save".equals(actor.getPersistentData().getString(HANDOFF_MARKER)),
                    "handoff lost latest detached body or provisioned again");
            require(actor.position().distanceToSqr(logoutPosition) < 0.01,
                    "later detached body replaced the protected normal-logout Pos envelope");
            consumeP4Login(activeServer, false);
            handoffOriginal = null;
            handoffServerTransport = null;
            handoffCompleted = true;
        });
    }

    private static void handoffReady(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        event("ACTUAL_NATIVE_CONFIG_PLAY_HANDOFF_" + cohortCase);
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-first-save-diagnostics.txt");
    }

    private static void respawnArmed(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        verifyClientTransport(minecraft);
        require(minecraft.player == latestLoginPlayer && loginCount == 1 && logoutCount == 0,
                "respawn packet has no exact current client player");
        transition(Phase.RESPAWN_PLAY);
        minecraft.getConnection().send(new ServerboundClientCommandPacket(
                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
    }

    private static void respawnPlay(Minecraft minecraft) {
        if (clientRespawns != 1 || !playReady(minecraft)) { return; }
        verifyClientTransport(minecraft);
        require(minecraft.getSingleplayerServer() == firstServer && latestLoginTransport == firstTransport
                        && minecraft.player == latestLoginPlayer && loginCount == 1 && logoutCount == 0
                        && clientHandoffs == 0, "respawn replaced the native login or transport");
        transition(Phase.RESPAWN_READY);
        scheduleServerWork(() -> {
            var actor = currentActor(activeServer);
            var prior = respawnOriginal;
            require(prior != null && actor == respawnClone && actor != prior
                            && actor.getUUID().equals(prior.getUUID()) && prior.isRemoved()
                            && actor.connection == prior.connection && actor.connection.player == actor
                            && actor.getStats() == prior.getStats()
                            && actor.getAdvancements() == prior.getAdvancements()
                            && serverCloneEvents == 1 && serverRespawnEvents == 1 && !actor.wonGame,
                    "original respawn caller did not install returned B with exact native owners");
            var diagnostic = P11NativeStorageBoundary.diagnostics(activeServer, playerId);
            writeArtifact("03-native-respawn-qualified-source.txt", diagnostic + "\n"
                    + "sameNativeListenerAndTransport=true\nsameCanonicalStatsAndAdvancements=true\n"
                    + "originalCallerInstalledReturnedB=true\nendOrRewardQualification=false\n");
            requireSource(diagnostic, false);
            require(diagnostic.sourceEpoch() > respawnSourceEpoch && "MEMORY".equals(diagnostic.sourceInput())
                            && diagnostic.resources().dirtyUuids() == 1
                            && equippedReference.equals(requireReference(diagnostic))
                            && "latest-before-original-native-copy".equals(actor.getPersistentData()
                                    .getCompound(ServerPlayer.PERSISTED_NBT_TAG).getString(RESPAWN_MARKER)),
                    "native COPY failed to qualify latest material under a new exact source");
            require(commandCount == 1 && commandCompletions == 1 && p4LoginArms == 1
                            && p4LoginObservations == 1 && p4Session == null && p4RecoveryEntriesCleared == 0
                            && p4RecoveryStepsReplayed == 0 && p4E2SetDataAttempts == 0,
                    "volatile native COPY ran another provisioning/login/persisted-recovery chain");
            respawnOriginal = null;
            respawnClone = null;
        });
    }

    private static void respawnReady(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        event("ACTUAL_NATIVE_NONDEATH_RESPAWN_COPY_RETURNED_B_QUALIFIED_WITH_SAME_CONNECTION");
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-first-save-diagnostics.txt");
    }

    private static void constructorFailureComplete(Minecraft minecraft) {
        // isStopped becomes true before stopServer and ServerStopped. Wait for the actual
        // original server thread to exit, so this controller never cuts its final writers short.
        if (!P11NativeConstructorFaultProbe.fired() || !activeServer.isShutdown()) { return; }
        require(configurationReturned && clientHandoffs == 0 && loginCount == 1
                        && constructorFaultClientDisconnects == 1 && !firstTransport.isConnected()
                        && minecraft.getSingleplayerServer() == null && minecraft.level == null
                        && minecraft.player == null && minecraft.getConnection() == null,
                "actual constructor failure did not terminate its exact CONFIG transport without admitting B");
        require(commandCount == 1 && commandCompletions == 1 && p4LoginArms == 1
                        && p4LoginObservations == 1 && p4Session == null
                        && p4RecoveryEntriesCleared == 0 && p4RecoveryStepsReplayed == 0
                        && p4E2SetDataAttempts == 0 && p4E2SetDataSuccesses == 0
                        && stopWriterSnapshots == 1,
                "constructor failure admitted another gameplay/login consumer or lost native stop observation");
        var summary = P11NativeConstructorFaultProbe.assertStopped();
        writeArtifact("05-constructor-fault-native-stop.txt", summary + "\nobserverFailures="
                + P11NativeStorageBoundary.observerFailureCount() + "\n"
                + "reason=ACTUAL_CONSTRUCTOR_FAILURE_NOT_HEALTHY_CAPACITY_FALLBACK\n");
        require(productionJarSha256.equals(hashFile(productionJar)), "loaded production JAR changed");
        event("ACTUAL_NATIVE_CONSTRUCTOR_THROW_PARTIAL_MATERIAL_DENIED_DIRTY_RETAINED");
        event("ORIGINAL_CONFIG_FAILURE_DISCONNECT_STOP_PROTECTED_PRIOR_FILES_AND_CACHE");
        activeServer = null;
        handoffOriginal = null;
        handoffServerTransport = null;
        finish(minecraft, null);
    }

    private static void statsIoFaultComplete(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        require(failedStatsAttempt > 0, "real stats failure had no captured physical attempt");
        event("REAL_STATS_IO_FAILURE_RETAINED_DIRTY_AND_OTHER_WRITERS_COMPLETED");
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-first-save-diagnostics.txt");
    }

    private static void materialMismatchComplete(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        require(materialMismatchObserved && expectedSlotFailures >= 3,
                "native material mismatch did not observe each refused whole-writer duty");
        event("ACTUAL_NATIVE_MANA_MATERIAL_MISMATCH_REFUSED_WHOLE_WRITES_AND_RESTORED_EXACT_OWNER");
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-first-save-diagnostics.txt");
    }

    private static void scheduleSave(String artifact) {
        scheduleServerWork(() -> {
            var server = activeServer;
            currentActor(server);
            // The normal synchronous original owners perform every write. Neither the boolean
            // return nor files alone is used as a substitute for actual writer outcomes.
            boolean saved = server.saveEverything(true, false, false);
            var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
            writeArtifact(artifact, "saveEverythingReturn=" + saved + "\n" + diagnostic + "\n");
            requireSource(diagnostic, false);
            requireWriters(diagnostic);
            if (cohortCase == CohortCase.STATS_IO_FAULT && expectedSlotFailures > 0) {
                require(writer(diagnostic, "STATISTICS").attempt() > failedStatsAttempt,
                        "I/O recovery did not use a fresh stats attempt");
            }
            if (cohortCase == CohortCase.PLAYER_REPLACE_FALSE && expectedSlotFailures > 0) {
                require(writer(diagnostic, "PLAYER_DATA").attempt() > failedPlayerAttempt,
                        "replace recovery did not use a fresh playerdata attempt");
            }
            require(equippedReference.equals(requireReference(diagnostic)), "save changed equipped reference");
        });
    }

    private static void firstSaveComplete(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        if (cohortCase == CohortCase.PLAYER_REPLACE_FALSE && !playerReplaceFaultInjected) {
            transition(Phase.PLAYER_REPLACE_FAULT);
            scheduleServerWork(P11SourceWriterClientHarness::exercisePlayerReplaceFalse);
            return;
        }
        event("FIRST_ORIGINAL_NATIVE_WRITERS_COMPLETED");
        transition(Phase.FIRST_STOP);
        stopWorld(minecraft);
    }

    private static void playerReplaceFaultComplete(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        require(failedPlayerAttempt > 0, "replace-false had no real captured playerdata attempt");
        event("REAL_PLAYER_REPLACE_FALSE_PRESERVED_PRIMARY_AND_DIRTY");
        transition(Phase.FIRST_SAVE);
        scheduleSave("04-player-replace-retry-diagnostics.txt");
    }

    private static void stopWorld(Minecraft minecraft) {
        var server = activeServer;
        require(server != null && minecraft.getSingleplayerServer() == server,
                "disconnect has wrong integrated server");
        require(phase == Phase.FIRST_STOP || phase == Phase.SECOND_STOP,
                "normal stop has no owned terminal phase");
        shutdownOrdinal = phase == Phase.FIRST_STOP ? 1 : 2;
        disconnectInProgress = true;
        try {
            server.halt(false);
            minecraft.disconnect();
        } finally { disconnectInProgress = false; }
        require(minecraft.level == null && minecraft.player == null
                        && minecraft.getSingleplayerServer() == null && minecraft.getConnection() == null,
                "normal halt/disconnect did not release the client world");
    }

    private static void firstStopComplete(Minecraft minecraft) {
        if (!activeServer.isStopped()) { return; }
        require(logoutCount == 1 && !firstTransport.isConnected(), "first normal transport did not terminate");
        requireNormalStop("05-first-stop-diagnostics.txt");
        firstSavedSkills = observeFiles("05-after-first-stop");
        event("FIRST_NORMAL_STOP_AND_REAL_FILES_OBSERVED");
        if (cohortCase == CohortCase.MALFORMED_STATS) { installMalformedStats(); }
        activeServer = null;
        transition(Phase.REOPEN_PLAY);
        minecraft.createWorldOpenFlows().openWorld(WORLD,
                () -> recordFailure(new IllegalStateException("owned world reopen failed")));
    }

    private static void reopenPlay(Minecraft minecraft) {
        if (!playReady(minecraft)) { return; }
        verifyClientTransport(minecraft);
        var server = minecraft.getSingleplayerServer();
        require(server != firstServer && latestLoginTransport != firstTransport,
                "restart reused the old server or transport");
        require(minecraft.player.getUUID().equals(playerId)
                        && loginCount == 2 + clientHandoffs && logoutCount == 1,
                "restart identity or actual PLAY lifecycle is not exact");
        require(commandCount == 1 && commandCompletions == 1, "restart reprovisioned the player");
        firstServer = null;
        firstTransport = null;
        expectedSlotFailures = 0;
        activeServer = server;
        transition(Phase.REOPEN_READY);
        scheduleServerWork(() -> {
            var actor = currentActor(server);
            requireCopiedConfiguration();
            var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
            requireSource(diagnostic, true);
            require(equippedReference.equals(requireReference(diagnostic)),
                    "native restart did not recover the same equipped revision");
            consumeP4Login(server, true);
            if (cohortCase == CohortCase.MALFORMED_STATS) {
                require(actor.getStats() instanceof P11IndependentMaterialWitness witness
                                && !witness.p11$materialComplete(),
                        "real canonical stats read did not retain malformed input as incomplete");
                requireMalformedStatsFile();
            }
            writeArtifact("06-reopened-diagnostics.txt", diagnostic.toString() + "\n");
        });
    }

    private static void reopenReady(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        event("SAME_WORLD_NATIVE_READBACK_WITHOUT_REPROVISION");
        transition(Phase.SECOND_SAVE);
        if (cohortCase == CohortCase.MALFORMED_STATS) {
            scheduleServerWork(() -> {
                var server = activeServer;
                currentActor(server);
                server.saveEverything(true, false, false);
                var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
                writeArtifact("07-second-save-diagnostics.txt", diagnostic.toString() + "\n");
                requireIndependentStatsFault(diagnostic, false);
                requireMalformedStatsFile();
                expectedSlotFailures = 1;
            });
        } else { scheduleSave("07-second-save-diagnostics.txt"); }
    }

    private static void secondSaveComplete(Minecraft minecraft) {
        if (!serverWorkComplete) { return; }
        transition(Phase.SECOND_STOP);
        stopWorld(minecraft);
    }

    private static void secondStopComplete(Minecraft minecraft) {
        if (!activeServer.isStopped()) { return; }
        require(logoutCount == 2 && commandCount == 1 && commandCompletions == 1,
                "terminal lifecycle or provisioning count changed");
        require(p4LoginArms == 2 + clientHandoffs && p4LoginObservations == 2 + clientHandoffs && p4Session == null
                        && p4RecoveryEntriesCleared == 1 && p4RecoveryStepsReplayed == 0
                        && p4E2SetDataAttempts == 0 && p4E2SetDataSuccesses == 0,
                "native P4 login observation counters did not close exactly");
        requireNormalStop("08-second-stop-diagnostics.txt");
        if (cohortCase == CohortCase.NATIVE_RESPAWN) {
            require(clientRespawns == 1 && serverCloneEvents == 1 && serverRespawnEvents == 1,
                    "native respawn event counts did not remain exact through restart");
        }
        var skills = observeFiles("08-after-second-stop");
        require(firstSavedSkills.equals(skills), "restart changed the persisted player skill carrier");
        require(productionJarSha256.equals(hashFile(productionJar)), "loaded production JAR changed");
        event("SECOND_NORMAL_STOP_AND_REAL_FILES_OBSERVED");
        if (cohortCase == CohortCase.MALFORMED_STATS) {
            requireMalformedStatsFile();
            event("MALFORMED_STATS_NEVER_REPLACED_OTHER_ORIGINAL_WRITERS_INDEPENDENT");
        }
        activeServer = null;
        firstSavedSkills = null;
        finish(minecraft, null);
    }

    private static ServerPlayer currentActor(MinecraftServer server) {
        require(server != null && server.isSameThread(), "native observation is off owning server thread");
        var actor = server.getPlayerList().getPlayer(playerId);
        require(actor != null && !(actor instanceof FakePlayer) && actor.getServer() == server
                        && actor.serverLevel().getServer() == server && actor.isAddedToLevel()
                        && !actor.isRemoved() && actor.isAlive() && actor.connection != null
                        && actor.connection.isAcceptingMessages()
                        && server.getPlayerList().getPlayer(actor.getUUID()) == actor,
                "source is not the exact live normal player");
        var source = actor.createCommandSourceStack();
        require(!server.getWorldData().isAllowCommands()
                        && !server.getPlayerList().isOp(actor.getGameProfile())
                        && server.getProfilePermissions(actor.getGameProfile()) == 0
                        && source.hasPermission(0) && !source.hasPermission(1),
                "engineering normal player unexpectedly has operator authority");
        return actor;
    }

    private static void scheduleServerWork(Runnable work) {
        require(!serverWorkScheduled, "server work duplicated in one phase");
        serverWorkScheduled = true;
        activeServer.execute(() -> {
            try { work.run(); serverWorkComplete = true; }
            catch (RuntimeException | Error failure) { recordFailure(failure); }
        });
    }

    private static void requireCopiedConfiguration() {
        var config = observedConfiguration;
        require(config != null && config.getLoadedConfig() != null,
                "native SERVER configuration loading was not observed");
        var path = config.getFullPath().toAbsolutePath().normalize();
        require(path.startsWith(gameDirectory), "loaded configuration is outside the owned game directory");
        require(configurationSha256.equals(hashFile(path)), "real server configuration differs from explicit fixture");
    }

    private static void requireSource(P11QualifiedSourceOwner.Diagnostics diagnostic, boolean reopened) {
        boolean expectedWriteIncident = (statisticsFaultInjected || playerReplaceFaultInjected
                        || (cohortCase == CohortCase.MATERIAL_MISMATCH && materialMismatchObserved))
                && cohortCase != CohortCase.POSITIVE && "WRITE".equals(diagnostic.sourceFault());
        require(diagnostic.active() && diagnostic.bodyComplete() && !diagnostic.candidatePresent()
                        && diagnostic.sourceEpoch() > 0 && diagnostic.sourceVersion() >= 0
                        && ("NONE".equals(diagnostic.sourceFault()) || expectedWriteIncident),
                "native source is not a complete current qualified body: " + diagnostic);
        require(diagnostic.resources().retainedUuids() == 1
                        && diagnostic.resources().waitingConnections() == 0
                        && diagnostic.resources().sealedSnapshots() == 0
                        && diagnostic.resources().sealedBytes() == 0
                        && diagnostic.resources().inFlight() == 0,
                "normal synchronous native path leaked source/save reservations");
        if (reopened) {
            // Integrated host selection is proven by the production read boundary. Comparing
            // files below is supplementary; it is not itself a P4 persisted-read capability.
            require("HOST_PRIMARY".equals(diagnostic.sourceInput()),
                    "reopened integrated host did not select the actual level.dat Player read");
        }
    }

    private static String requireReference(P11QualifiedSourceOwner.Diagnostics diagnostic) {
        requireSource(diagnostic, false);
        var reference = diagnostic.equippedSlot0();
        require(reference != null && !reference.isBlank()
                        && !"ABSENT".equals(reference) && !"UNAVAILABLE".equals(reference),
                "production equipped slot 0 has no exact reference");
        return reference;
    }

    private static void requireWriters(P11QualifiedSourceOwner.Diagnostics diagnostic) {
        for (var kind : List.of("PLAYER_DATA", "LEVEL_PLAYER", "STATISTICS", "ADVANCEMENTS")) {
            requireSuccessfulWriter(writer(diagnostic, kind));
        }
        requireSuccessfulCache(writer(diagnostic, "CACHE"));
        require(diagnostic.resources().dirtyUuids() == 0,
                "all required original writer proofs did not discharge aggregate dirty duty");
    }

    private static void requireNormalStop(String artifact) {
        require(stopWriterSnapshots == shutdownOrdinal,
                "native stop lacks the exact final per-writer outcome snapshot");
        var summary = P11NativeStorageBoundary.terminalDiagnostics();
        require(summary != null, "stopped slot has no native terminal summary");
        long observerFailures = P11NativeStorageBoundary.observerFailureCount();
        writeArtifact(artifact, summary.toString() + "\nobserverFailures=" + observerFailures + "\n");
        boolean unresolvedStats = cohortCase == CohortCase.MALFORMED_STATS && expectedSlotFailures > 0;
        boolean expectedFailures = expectedSlotFailures == 0 ? summary.failures() == 0
                : summary.failures() >= expectedSlotFailures;
        require(summary.nativeStopNormal() && expectedFailures && observerFailures == 0
                        && summary.resources().dirtyUuids() == (unresolvedStats ? 1 : 0)
                        && summary.resources().sealedSnapshots() == 0
                        && summary.resources().sealedBytes() == 0
                        && summary.resources().inFlight() == 0,
                "native stop failed or discarded uncompleted source/save obligations: " + summary);
    }

    private static P11QualifiedSourceOwner.WriterDiagnostic writer(
            P11QualifiedSourceOwner.Diagnostics diagnostic, String kind) {
        var matches = diagnostic.writers().stream().filter(value -> kind.equals(value.kind())).toList();
        require(matches.size() == 1, "missing or duplicate original physical writer outcome: " + kind);
        return matches.getFirst();
    }

    private static void requireSuccessfulWriter(P11QualifiedSourceOwner.WriterDiagnostic writer) {
        require(writer.attempt() > 0 && !writer.dirty() && "COMPLETED".equals(writer.terminal())
                        && "SUCCEEDED".equals(writer.encode()) && "SUCCEEDED".equals(writer.write())
                        && "SUCCEEDED".equals(writer.close()),
                "native encode/write/close did not complete: " + writer);
        boolean json = "STATISTICS".equals(writer.kind()) || "ADVANCEMENTS".equals(writer.kind());
        require((json ? "NOT_OBSERVED" : "SUCCEEDED").equals(writer.replace())
                        && "NOT_OBSERVED".equals(writer.cacheAssignment()),
                "native writer-specific replace contract differs: " + writer);
    }

    private static void requireSuccessfulCache(P11QualifiedSourceOwner.WriterDiagnostic cache) {
        require(cache.attempt() > 0 && !cache.dirty() && "COMPLETED".equals(cache.terminal())
                        && "SUCCEEDED".equals(cache.encode()) && "SUCCEEDED".equals(cache.cacheAssignment())
                        && "NOT_OBSERVED".equals(cache.write()) && "NOT_OBSERVED".equals(cache.close())
                        && "NOT_OBSERVED".equals(cache.replace()),
                "native host cache assignment is missing or fabricated physical persistence: " + cache);
    }

    private static void requireIndependentStatsFault(
            P11QualifiedSourceOwner.Diagnostics diagnostic, boolean ioAttempt) {
        requireSource(diagnostic, false);
        require("WRITE".equals(diagnostic.sourceFault()) && diagnostic.resources().dirtyUuids() == 1,
                "native stats failure did not retain its account dirty responsibility");
        for (var kind : List.of("PLAYER_DATA", "LEVEL_PLAYER", "ADVANCEMENTS")) {
            requireSuccessfulWriter(writer(diagnostic, kind));
        }
        requireSuccessfulCache(writer(diagnostic, "CACHE"));
        var stats = writer(diagnostic, "STATISTICS");
        require(stats.dirty() && "NOT_OBSERVED".equals(stats.replace())
                        && "NOT_OBSERVED".equals(stats.cacheAssignment()),
                "failed/ineligible JSON stats source invented replacement or completion");
        if (ioAttempt) {
            require(stats.attempt() > 0 && "FAILED".equals(stats.terminal())
                            && "SUCCEEDED".equals(stats.encode())
                            && "UNKNOWN".equals(stats.write()) && "UNKNOWN".equals(stats.close()),
                    "FileUtils joint write/close failure was not recorded at its true evidence granularity");
            failedStatsAttempt = stats.attempt();
        } else {
            require("OPEN".equals(stats.terminal()) && "NOT_OBSERVED".equals(stats.encode())
                            && "NOT_OBSERVED".equals(stats.write()) && "NOT_OBSERVED".equals(stats.close()),
                    "incomplete canonical stats was permitted to begin a physical writer");
        }
    }

    private static Path statsPath() {
        return worldDirectory.resolve("stats").resolve(playerId + ".json");
    }

    private static void installMalformedStats() {
        var path = statsPath();
        try {
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "stats corruption target is not owned regular data");
            Files.move(path, output.resolve("05-original-stats-before-corruption.json"));
            var malformed = "{\"stats\": [\n";
            Files.writeString(path, malformed, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            malformedStatsSha256 = hashFile(path);
            statisticsFaultInjected = true;
            writeArtifact("05-malformed-stats-input.txt", "path=" + path
                    + "\nsha256=" + malformedStatsSha256 + "\n" + malformed);
            requireMalformedStatsFile();
        } catch (IOException failure) { throw new IllegalStateException("owned stats corruption fixture failed", failure); }
    }

    private static void requireMalformedStatsFile() {
        require(malformedStatsSha256 != null && malformedStatsSha256.equals(hashFile(statsPath())),
                "native writer replaced malformed unqualified stats material");
        boolean rejected = false;
        try { JsonParser.parseString(Files.readString(statsPath())); }
        catch (JsonParseException expected) { rejected = true; }
        catch (IOException failure) { throw new IllegalStateException("malformed stats fixture could not be read", failure); }
        require(rejected, "malformed stats fixture unexpectedly parsed");
    }

    private static void exerciseStatsIoFailure() {
        var server = activeServer;
        var actor = currentActor(server);
        require(actor.getStats() instanceof P11IndependentMaterialWitness witness && witness.p11$materialComplete(),
                "I/O fault fixture did not start with complete canonical stats");
        var path = statsPath();
        var backup = output.resolve("04-original-stats-before-io-fault.json");
        boolean displaced = false;
        boolean blockerOwned = false;
        Throwable primaryFailure = null;
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS), "stats I/O target is not a regular owned file");
                Files.move(path, backup);
                displaced = true;
            }
            Files.createDirectory(path);
            blockerOwned = true;
            statisticsFaultInjected = true;
            expectedSlotFailures = 1;
            writeArtifact("04-io-fault-input.txt", "path=" + path + "\nblocker=OWNED_EMPTY_DIRECTORY\n"
                    + "originalDisplaced=" + displaced + "\n");
            server.saveEverything(true, false, false);
            var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
            writeArtifact("04-stats-io-failure-diagnostics.txt", diagnostic.toString() + "\n");
            requireIndependentStatsFault(diagnostic, true);
            require(commandCount == 1 && commandCompletions == 1,
                    "native I/O fault retriggered the original gameplay command");
        } catch (IOException failure) {
            var wrapped = new IllegalStateException("owned stats I/O fixture failed", failure);
            primaryFailure = wrapped;
            throw wrapped;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        }
        finally {
            try {
                if (blockerOwned) {
                    require(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS), "owned stats blocker identity changed");
                    try (var entries = Files.list(path)) {
                        require(entries.findAny().isEmpty(), "owned stats blocker gained unknown content");
                    }
                    Files.delete(path); // Only the exact, just-created, still-empty fixture directory.
                }
                if (displaced) { Files.copy(backup, path); } // Retain the original evidence backup.
            } catch (IOException | RuntimeException | Error failure) {
                if (primaryFailure != null) { primaryFailure.addSuppressed(failure); }
                else if (failure instanceof RuntimeException runtimeFailure) { throw runtimeFailure; }
                else if (failure instanceof Error error) { throw error; }
                else { throw new IllegalStateException("owned stats fixture restore failed", failure); }
            }
        }
    }

    private static void exercisePlayerReplaceFalse() {
        var server = activeServer;
        currentActor(server);
        var primary = worldDirectory.resolve("playerdata").resolve(playerId + ".dat");
        var old = primary.resolveSibling(playerId + ".dat_old");
        var archive = output.resolve("04-original-playerdata-old-before-replace-false.dat");
        var sentinel = old.resolve("p11-owned-replace-blocker.txt");
        final String sentinelBytes = "P11 owned nonempty backup blocker\n";
        require(Files.isRegularFile(primary, LinkOption.NOFOLLOW_LINKS),
                "replace-false fixture requires a previously successful native primary");
        String before = hashFile(primary);
        boolean displaced = false;
        boolean blockerOwned = false;
        boolean sentinelOwned = false;
        Throwable primaryFailure = null;
        try {
            Files.copy(primary, output.resolve("04-primary-before-replace-false.dat"));
            if (Files.exists(old, LinkOption.NOFOLLOW_LINKS)) {
                require(Files.isRegularFile(old, LinkOption.NOFOLLOW_LINKS),
                        "replace-false backup target is not owned regular data");
                Files.move(old, archive);
                displaced = true;
            }
            Files.createDirectory(old);
            blockerOwned = true;
            Files.writeString(sentinel, sentinelBytes, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            sentinelOwned = true;
            playerReplaceFaultInjected = true;
            expectedSlotFailures = 1;
            writeArtifact("04-player-replace-false-input.txt", "primary=" + primary
                    + "\nprimarySha256=" + before + "\nbackup=" + old
                    + "\nblocker=OWNED_NONEMPTY_DIRECTORY\noriginalBackupDisplaced=" + displaced + "\n");
            server.getPlayerList().saveAll();
            var diagnostic = P11NativeStorageBoundary.diagnostics(server, playerId);
            writeArtifact("04-player-replace-false-diagnostics.txt", diagnostic.toString() + "\n");
            requireSource(diagnostic, false);
            require("WRITE".equals(diagnostic.sourceFault()) && diagnostic.resources().dirtyUuids() == 1,
                    "real replace-false did not retain source dirty responsibility");
            var player = writer(diagnostic, "PLAYER_DATA");
            require(player.attempt() > 0 && player.dirty() && "FAILED".equals(player.terminal())
                            && "SUCCEEDED".equals(player.encode()) && "SUCCEEDED".equals(player.write())
                            && "SUCCEEDED".equals(player.close()) && "FAILED".equals(player.replace())
                            && "NOT_OBSERVED".equals(player.cacheAssignment()),
                    "actual original Util false result was not consumed by the playerdata writer: " + player);
            failedPlayerAttempt = player.attempt();
            requireSuccessfulWriter(writer(diagnostic, "STATISTICS"));
            requireSuccessfulWriter(writer(diagnostic, "ADVANCEMENTS"));
            requireSuccessfulCache(writer(diagnostic, "CACHE"));
            require(before.equals(hashFile(primary)), "replace-false modified the previously successful primary");
            Files.copy(primary, output.resolve("04-primary-after-replace-false.dat"));
            require(commandCount == 1 && commandCompletions == 1,
                    "replace-false retriggered the original gameplay command");
        } catch (IOException failure) {
            var wrapped = new IllegalStateException("owned playerdata replace-false fixture failed", failure);
            primaryFailure = wrapped;
            throw wrapped;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            try {
                if (sentinelOwned) {
                    require(Files.isRegularFile(sentinel, LinkOption.NOFOLLOW_LINKS)
                                    && sentinelBytes.equals(Files.readString(sentinel)),
                            "owned replacement sentinel changed unexpectedly");
                    Files.delete(sentinel); // Exact, verified engineering sentinel only.
                }
                if (blockerOwned) {
                    require(Files.isDirectory(old, LinkOption.NOFOLLOW_LINKS), "owned replacement blocker changed");
                    try (var entries = Files.list(old)) {
                        require(entries.findAny().isEmpty(), "owned replacement blocker gained unknown content");
                    }
                    Files.delete(old); // Exact, just-created empty directory, never recursive.
                }
                if (displaced) { Files.copy(archive, old); }
            } catch (IOException | RuntimeException | Error failure) {
                if (primaryFailure != null) { primaryFailure.addSuppressed(failure); }
                else if (failure instanceof RuntimeException runtimeFailure) { throw runtimeFailure; }
                else if (failure instanceof Error error) { throw error; }
                else { throw new IllegalStateException("owned playerdata fixture restore failed", failure); }
            }
        }
    }

    private static CompoundTag observeFiles(String label) {
        try {
            var directory = output.resolve(label);
            Files.createDirectory(directory);
            var primary = worldDirectory.resolve("playerdata").resolve(playerId + ".dat");
            var level = worldDirectory.resolve("level.dat");
            var stats = worldDirectory.resolve("stats").resolve(playerId + ".json");
            var advancements = worldDirectory.resolve("advancements").resolve(playerId + ".json");
            var manifest = new StringBuilder();
            for (var path : List.of(primary, level, stats, advancements)) {
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                                && Files.size(path) > 0 && Files.size(path) <= FIXTURE_FILE_BOUND,
                        "native output is absent, unsafe or exceeds this fixture reader bound: " + path);
                var relative = worldDirectory.relativize(path).toString();
                var target = directory.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(path, target);
                manifest.append(relative).append("\tbytes=").append(Files.size(path))
                        .append("\tsha256=").append(hashFile(path)).append('\n');
            }
            var dat = NbtIo.readCompressed(primary, NbtAccounter.create(FIXTURE_FILE_BOUND));
            var levelRoot = NbtIo.readCompressed(level, NbtAccounter.create(FIXTURE_FILE_BOUND));
            require(levelRoot.get("Data") instanceof CompoundTag, "level.dat has no native Data compound");
            var data = levelRoot.getCompound("Data");
            require(data.get("Player") instanceof CompoundTag, "native host Player payload is missing");
            var host = data.getCompound("Player");
            require(dat.hasUUID("UUID") && host.hasUUID("UUID") && dat.getUUID("UUID").equals(playerId)
                            && host.getUUID("UUID").equals(playerId), "physical player sources have wrong UUID");
            var datSkills = skills(dat);
            require(datSkills.equals(skills(host)), "playerdata and host Player skills differ");
            var equipped = datSkills.getList("equipped_slots", Tag.TAG_COMPOUND);
            require(!equipped.isEmpty() && equipped.getCompound(0).getInt("slot") == 0,
                    "real files do not contain the submitted slot-0 skill");
            if (cohortCase == CohortCase.MALFORMED_STATS && malformedStatsSha256 != null) {
                requireMalformedStatsFile();
            } else {
                require(JsonParser.parseString(Files.readString(stats)).isJsonObject(), "stats is not a JSON object");
            }
            require(JsonParser.parseString(Files.readString(advancements)).isJsonObject(),
                    "advancements is not a JSON object");
            writeArtifact(label + "/manifest.txt", manifest.toString());
            writeArtifact(label + "/player-skills.snbt", datSkills.toString() + "\n");
            return datSkills.copy();
        } catch (IOException failure) { throw new IllegalStateException("real native file observation failed", failure); }
    }

    private static CompoundTag skills(CompoundTag root) {
        require(root.get(AttachmentHolder.ATTACHMENTS_NBT_KEY) instanceof CompoundTag,
                "native player root has no Attachments");
        var attachments = root.getCompound(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        require(attachments.get("gramarye:player_skills") instanceof CompoundTag,
                "native player root has no player_skills carrier");
        return attachments.getCompound("gramarye:player_skills");
    }

    private static void captureProductionJar() {
        var configured = System.getProperty("gramarye.p10.frozenJar");
        require(configured != null && !configured.isBlank(), "frozen production JAR path is absent");
        productionJar = Path.of(configured).toAbsolutePath().normalize();
        require(Files.isRegularFile(productionJar, LinkOption.NOFOLLOW_LINKS), "production JAR is not regular");
        var roots = Arrays.stream(System.getProperty("fml.modFolders", "").split(
                        java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
                .filter(value -> value.startsWith("p9S5ClientRuntimeHarness%%"))
                .map(value -> Path.of(value.substring("p9S5ClientRuntimeHarness%%".length()))
                        .toAbsolutePath().normalize()).toList();
        require(roots.contains(productionJar), "actual harness mod roots do not load the frozen production JAR");
        var rootClass = "com/yo1no/gramarye/Gramarye.class";
        for (var value : System.getProperty("java.class.path", "").split(
                java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (!value.isBlank()) {
                var root = Path.of(value).toAbsolutePath().normalize();
                require(!Files.isDirectory(root) || !Files.exists(root.resolve(rootClass)),
                        "exploded production classes are on JVM runtime classpath");
            }
        }
        for (var root : roots) {
            require(root.equals(productionJar) || !Files.exists(root.resolve(rootClass)),
                    "mod roots shadow production JAR with exploded classes");
        }
        try (var jar = new JarFile(productionJar.toFile());
                var loaded = Gramarye.class.getResourceAsStream("/" + rootClass)) {
            require(jar.getEntry("com/yo1no/gramarye/P11SourceWriterClientHarness.class") == null,
                    "engineering controller leaked into production JAR");
            require(jar.getEntry("com/yo1no/gramarye/P9S5ClientRuntimeHarness.class") == null,
                    "existing engineering controller leaked into production JAR");
            require(jar.getEntry("com/yo1no/gramarye/P11NativeConstructorFaultProbe.class") == null,
                    "constructor-fault engineering helper leaked into production JAR");
            require(jar.getEntry("com/yo1no/gramarye/P11NativeMaterialProbe.class") == null,
                    "material-mismatch engineering helper leaked into production JAR");
            var entry = jar.getJarEntry(rootClass);
            require(entry != null && loaded != null, "root production class is missing");
            try (var archived = jar.getInputStream(entry)) {
                require(Arrays.equals(loaded.readAllBytes(), archived.readAllBytes()),
                        "loaded production class bytes differ from frozen JAR");
            }
        } catch (IOException failure) { throw new IllegalStateException("JAR load identity observation failed", failure); }
        productionJarSha256 = hashFile(productionJar);
    }

    private static String hashFile(Path path) {
        try (var stream = Files.newInputStream(path)) {
            var digest = MessageDigest.getInstance("SHA-256");
            var buffer = new byte[8192];
            for (int count; (count = stream.read(buffer)) >= 0;) { digest.update(buffer, 0, count); }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException("artifact hash failed: " + path, failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static void writeArtifact(String relative, String contents) {
        require(outputReserved, "evidence directory was not reserved");
        require(contents.getBytes(StandardCharsets.UTF_8).length <= 65_536,
                "bounded scalar evidence exceeded its engineering limit");
        try {
            Files.writeString(output.resolve(relative), contents, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException failure) { throw new IllegalStateException("new evidence write failed: " + relative, failure); }
    }

    private static void transition(Phase replacement) {
        phase = replacement;
        phaseTicks = 0;
        serverWorkScheduled = false;
        serverWorkComplete = false;
    }

    private static void event(String value) {
        EVENTS.add(value);
        Gramarye.LOGGER.info("P11-SOURCE-WRITER-CLIENT {}", value);
    }

    private static void finish(Minecraft minecraft, Throwable failure) {
        if (terminal) { return; }
        var stoppedAt = phase;
        terminal = true;
        phase = Phase.TERMINAL;
        try {
            if (failure != null) { Gramarye.LOGGER.error("P11 native source/writer cohort failed", failure); }
            var result = new StringBuilder("P11-NATIVE-SOURCE-WRITER-V1\n");
            result.append("case=").append(switch (cohortCase) {
                case POSITIVE -> "positive";
                case MALFORMED_STATS -> "malformed-stats";
                case STATS_IO_FAULT -> "stats-io-fault";
                case PLAYER_REPLACE_FALSE -> "player-replace-false";
                case MEMORY_HANDOFF -> "memory-handoff";
                case SYNCHRONOUS_HANDOFF -> "synchronous-handoff";
                case CONSTRUCTOR_FAILURE -> "constructor-failure";
                case MATERIAL_MISMATCH -> "material-mismatch";
                case NATIVE_RESPAWN -> "native-respawn";
            }).append('\n');
            result.append("cohort=").append(cohortCase).append('\n');
            for (var value : EVENTS) { result.append(value).append('\n'); }
            result.append("terminalPhase=").append(stoppedAt).append('\n')
                    .append("commandCount=").append(commandCount).append('\n')
                    .append("commandCompletions=").append(commandCompletions).append('\n')
                    .append("loginCount=").append(loginCount).append('\n')
                    .append("logoutCount=").append(logoutCount).append('\n')
                    .append("unownedWorldCleanupCount=").append(unownedWorldCleanupCount).append('\n')
                    .append("clientNativeReconfigurations=").append(clientHandoffs).append('\n')
                    .append("constructorFaultClientDisconnects=").append(constructorFaultClientDisconnects).append('\n')
                    .append("nativeClientRespawns=").append(clientRespawns).append('\n')
                    .append("nativeServerCloneEvents=").append(serverCloneEvents).append('\n')
                    .append("nativeServerRespawnEvents=").append(serverRespawnEvents).append('\n')
                    .append("finalStopWriterSnapshots=").append(stopWriterSnapshots).append('\n');
            result.append("p4LoginArms=").append(p4LoginArms).append('\n')
                    .append("p4LoginObservations=").append(p4LoginObservations).append('\n')
                    .append("p4RecoveryEntriesCleared=").append(p4RecoveryEntriesCleared).append('\n')
                    .append("p4RecoveryStepsReplayed=").append(p4RecoveryStepsReplayed).append('\n')
                    .append("p4E2SetDataAttempts=").append(p4E2SetDataAttempts).append('\n')
                    .append("p4E2SetDataSuccesses=").append(p4E2SetDataSuccesses).append('\n');
            if (failure != null) {
                result.append("failure=").append(failure.getClass().getName()).append(':')
                        .append(String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ')).append('\n');
            }
            result.append(failure == null ? "RESULT=PASS\n" : "RESULT=FAIL\n");
            if (outputReserved) { writeArtifact("result.txt", result.toString()); }
        } finally { minecraft.stop(); }
    }

    private static void require(boolean value, String message) {
        if (!value) { throw new IllegalStateException(message); }
    }

    private static synchronized void recordFailure(Throwable failure) {
        if (asynchronousFailure == null) { asynchronousFailure = failure; }
        else if (asynchronousFailure != failure) { asynchronousFailure.addSuppressed(failure); }
    }

    private enum Phase {
        BOOTSTRAP, FIRST_PLAY, FIRST_READY, COMMAND, HANDOFF_PLAY, HANDOFF_READY,
        RESPAWN_ARM, RESPAWN_PLAY, RESPAWN_READY,
        STATS_IO_FAULT, MATERIAL_MISMATCH, PLAYER_REPLACE_FAULT, FIRST_SAVE, FIRST_STOP,
        REOPEN_PLAY, REOPEN_READY, SECOND_SAVE, SECOND_STOP, TERMINAL
    }

    private enum CohortCase {
        POSITIVE, MALFORMED_STATS, STATS_IO_FAULT, PLAYER_REPLACE_FALSE, MEMORY_HANDOFF, SYNCHRONOUS_HANDOFF,
        CONSTRUCTOR_FAILURE, MATERIAL_MISMATCH, NATIVE_RESPAWN
    }
}
