package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SkillRuntimeAuthenticatedCastIngressTest {
    private static final Path SERVICE_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/SkillRuntimeService.java");

    @TempDir
    Path temporary;

    @Test
    void authenticatedIngressHasTheExactPackagePrivateTokenSafeShape() throws Exception {
        var method = SkillRuntimeService.class.getDeclaredMethod(
                "admitAuthenticatedPlayerCast",
                MinecraftServer.class,
                ServerPlayer.class,
                SkillReference.class,
                CastGeometryExecutionDataV0.class);

        assertAll(
                () -> assertFalse(Modifier.isPublic(method.getModifiers())),
                () -> assertFalse(Modifier.isProtected(method.getModifiers())),
                () -> assertFalse(Modifier.isPrivate(method.getModifiers())),
                () -> assertFalse(Modifier.isStatic(method.getModifiers())),
                () -> assertEquals(RuntimeAdmissionResult.class, method.getReturnType()),
                () -> assertEquals(
                        List.of(
                                MinecraftServer.class,
                                ServerPlayer.class,
                                SkillReference.class,
                                CastGeometryExecutionDataV0.class),
                        List.of(method.getParameterTypes())),
                () -> assertEquals(0, method.getExceptionTypes().length),
                () -> assertEquals(
                        1,
                        java.util.Arrays.stream(SkillRuntimeService.class.getDeclaredMethods())
                                .filter(candidate -> candidate.getName()
                                        .equals("admitAuthenticatedPlayerCast"))
                                .count()));
    }

    @Test
    void authenticatedIngressBuildsOneMemoryOnlyNoTargetRootFromTheExactReference()
            throws Exception {
        var source = Files.readString(SERVICE_SOURCE);
        var ingress = section(
                source,
                "RuntimeAdmissionResult admitAuthenticatedPlayerCast(",
                "void requestP9ReloadInvalidation(");
        var compact = ingress.replaceAll("\\s+", "");
        var wrongThread = ingress.indexOf(
                "return new RuntimeAdmissionResult.WrongThread();");
        var slotRead = ingress.indexOf("var slot = slots.get(server);");
        var rootValidation = section(
                source,
                "private static Optional<InvalidEventReason> validateRootStableShape(",
                "private static Optional<InvalidEventReason> validateRootDefinitionShape(");
        var genericRootAdmission = section(
                source,
                "RuntimeAdmissionResult admitRoot(",
                "private RuntimeAdmissionResult admitRootWithP9Actor(");
        var actorRootAdmission = section(
                source,
                "private RuntimeAdmissionResult admitRootWithP9Actor(",
                "private RuntimeAdmissionResult acquireAndPublishRoot(");

        assertAll(
                () -> assertTrue(wrongThread >= 0),
                () -> assertTrue(slotRead > wrongThread),
                () -> assertFalse(ingress.substring(0, wrongThread).contains("slots.")),
                () -> assertTrue(ingress.indexOf("Objects.requireNonNull(geometry, \"geometry\")")
                        < ingress.indexOf("server.isSameThread()")),
                () -> assertTrue(ingress.indexOf("p9ReloadCloseRequested.get()") > slotRead),
                () -> assertEquals(
                        1, occurrences(ingress, "return admitRootWithP9Actor(")),
                () -> assertEquals(1, occurrences(ingress, "new RuntimeRootEventSpec(")),
                () -> assertEquals(0, occurrences(ingress, "latestReference(")),
                () -> assertEquals(0, occurrences(ingress, "new EventId(")),
                () -> assertEquals(0, occurrences(ingress, "RuntimeEventToken")),
                () -> assertEquals(0, occurrences(ingress, "RuntimeCancellationToken")),
                () -> assertEquals(0, occurrences(ingress, "executionPort")),
                () -> assertTrue(compact.contains(
                        "newRuntimeRootEventSpec(exactReference,0,")),
                () -> assertTrue(compact.contains(
                        "newRuntimeScheduleSpec(0,100,RuntimeSchedulePersistence.MEMORY_ONLY)")),
                () -> assertTrue(compact.contains(
                        "newPlayerRuntimeBudgetAttribution(slot.token,playerId)")),
                () -> assertTrue(compact.contains(
                        "newPlayerOrigin(slot.token,actorLevel.dimension(),playerId)")),
                () -> assertEquals(1, occurrences(ingress, "Optional.empty()")),
                () -> assertTrue(compact.contains(
                        "ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID,"
                                + "\"active_cast\")")),
                () -> assertTrue(compact.endsWith("geometry),actor);}")),
                () -> assertFalse(ingress.contains("NoRuntimeExecutionData.INSTANCE")),
                () -> assertTrue(genericRootAdmission.contains(
                        "spec.executionData() instanceof CastGeometryExecutionDataV0")),
                () -> assertTrue(genericRootAdmission.contains(
                        "InvalidEventReason.INVALID_EXECUTION_DATA")),
                () -> assertTrue(genericRootAdmission.contains(
                        "return admitRootWithP9Actor(server, spec, null)")),
                () -> assertTrue(actorRootAdmission.contains(
                        "if (isP9ExecutionData(spec.executionData()) "
                                + "&& p9ReloadCloseRequested.get())")),
                () -> assertTrue(actorRootAdmission.contains(
                        "baseTick = Math.addExact(slot.runtimeTick, 1L);")),
                () -> assertTrue(actorRootAdmission.contains(
                        "deadlineTick = Math.addExact(baseTick, schedule.deadlineHorizonTicks());")),
                () -> assertTrue(rootValidation.contains(
                        "!slot.token.equals(spec.budgetAttribution().server())")),
                () -> assertTrue(rootValidation.contains(
                        "!geometry.dimension().equals(playerOrigin.dimension().location())")),
                () -> assertTrue(rootValidation.contains(
                        "spec.schedule().delayTicks() != 0")),
                () -> assertTrue(rootValidation.contains(
                        "spec.schedule().deadlineHorizonTicks() != 100")),
                () -> assertTrue(rootValidation.contains(
                        "spec.schedule().persistence()")),
                () -> assertTrue(rootValidation.contains("!validCastGeometry(geometry)")));
    }

    @Test
    void exactProductionIngressMethodExecutesTokenSafeCoordinatesAndEarlyFailures()
            throws Exception {
        var productionSource = Files.readString(SERVICE_SOURCE);
        var exactMethod = section(
                productionSource,
                "    RuntimeAdmissionResult admitAuthenticatedPlayerCast(",
                "    void requestP9ReloadInvalidation(");
        var exactInstanceState = section(
                productionSource,
                "    static final class InstanceState {",
                "    static final class P9ActiveDiagnostic {");
        var sourceRoot = Files.createDirectories(temporary.resolve("source"));
        var outputRoot = Files.createDirectories(temporary.resolve("classes"));

        var resourceLocation = write(sourceRoot,
                "net/minecraft/resources/ResourceLocation.java", """
                package net.minecraft.resources;

                public final class ResourceLocation {
                    private final String namespace;
                    private final String path;

                    private ResourceLocation(String namespace, String path) {
                        this.namespace = namespace;
                        this.path = path;
                    }

                    public static ResourceLocation fromNamespaceAndPath(
                            String namespace, String path) {
                        return new ResourceLocation(namespace, path);
                    }

                    public String getNamespace() {
                        return namespace;
                    }

                    public String getPath() {
                        return path;
                    }
                }
                """);
        var server = write(sourceRoot, "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                import java.util.HashMap;
                import java.util.Map;
                import java.util.UUID;
                import net.minecraft.server.level.ServerPlayer;

                public final class MinecraftServer {
                    private final boolean sameThread;
                    private final PlayerList playerList = new PlayerList();
                    private boolean running = true;
                    private boolean stopped;
                    private int sameThreadCalls;

                    public MinecraftServer(boolean sameThread) {
                        this.sameThread = sameThread;
                    }

                    public boolean isSameThread() {
                        sameThreadCalls++;
                        return sameThread;
                    }

                    public int sameThreadCalls() {
                        return sameThreadCalls;
                    }

                    public boolean isRunning() {
                        return running;
                    }

                    public boolean isStopped() {
                        return stopped;
                    }

                    public PlayerList getPlayerList() {
                        return playerList;
                    }

                    public static final class PlayerList {
                        private final Map<UUID, ServerPlayer> players = new HashMap<>();

                        public ServerPlayer getPlayer(UUID playerId) {
                            return players.get(playerId);
                        }

                        public void register(UUID playerId, ServerPlayer player) {
                            players.put(playerId, player);
                        }
                    }
                }
                """);
        var serverLevel = write(sourceRoot,
                "net/minecraft/server/level/ServerLevel.java", """
                package net.minecraft.server.level;

                import net.minecraft.server.MinecraftServer;

                public final class ServerLevel {
                    private final MinecraftServer server;
                    private final Dimension dimension;
                    private int dimensionCalls;

                    public ServerLevel(MinecraftServer server, Object dimension) {
                        this.server = server;
                        this.dimension = new Dimension(dimension);
                    }

                    public MinecraftServer getServer() {
                        return server;
                    }

                    public Dimension dimension() {
                        dimensionCalls++;
                        return dimension;
                    }

                    public int dimensionCalls() {
                        return dimensionCalls;
                    }

                    public record Dimension(Object location) {
                    }
                }
                """);
        var serverPlayer = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;

                public final class ServerPlayer {
                    private final UUID playerId;
                    private final ServerLevel level;
                    public final Connection connection = new Connection();
                    private boolean removed;
                    private boolean alive = true;
                    private int uuidCalls;
                    private int levelCalls;

                    public ServerPlayer(UUID playerId, ServerLevel level) {
                        this.playerId = playerId;
                        this.level = level;
                    }

                    public UUID getUUID() {
                        uuidCalls++;
                        return playerId;
                    }

                    public ServerLevel serverLevel() {
                        levelCalls++;
                        return level;
                    }

                    public MinecraftServer getServer() {
                        return level.getServer();
                    }

                    public boolean isRemoved() {
                        return removed;
                    }

                    public boolean isAlive() {
                        return alive;
                    }

                    public int uuidCalls() {
                        return uuidCalls;
                    }

                    public int levelCalls() {
                        return levelCalls;
                    }

                    public static final class Connection {
                        private boolean acceptingMessages = true;

                        public boolean isAcceptingMessages() {
                            return acceptingMessages;
                        }

                        public void setAcceptingMessages(boolean acceptingMessages) {
                            this.acceptingMessages = acceptingMessages;
                        }
                    }
                }
                """);
        var skillReference = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/document/SkillReference.java", """
                package com.yo1no.gramarye.magic.definition.document;

                public final class SkillReference {
                }
                """);
        var skillInstanceId = write(sourceRoot,
                "com/yo1no/gramarye/magic/api/id/SkillInstanceId.java", """
                package com.yo1no.gramarye.magic.api.id;

                import java.util.UUID;

                public record SkillInstanceId(UUID value) {
                }
                """);
        var triggerEventKind = write(sourceRoot,
                "com/yo1no/gramarye/magic/capability/TriggerEventKind.java", """
                package com.yo1no.gramarye.magic.capability;

                import net.minecraft.resources.ResourceLocation;

                public record TriggerEventKind(ResourceLocation id) {
                    public TriggerEventKind {
                        if (id == null) {
                            throw new NullPointerException("id");
                        }
                    }
                }
                """);
        var serviceShell = write(sourceRoot,
                "com/yo1no/gramarye/SkillRuntimeService.java", """
                package com.yo1no.gramarye;

                import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
                import com.yo1no.gramarye.magic.capability.TriggerEventKind;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import java.util.IdentityHashMap;
                import java.util.Objects;
                import java.util.Optional;
                import java.util.UUID;
                import java.util.concurrent.atomic.AtomicBoolean;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                final class SkillRuntimeService {
                    private final IdentityHashMap<MinecraftServer, ServerSlot> slots =
                            new IdentityHashMap<>();
                    private final AtomicBoolean p9ReloadCloseRequested = new AtomicBoolean();
                    private RuntimeRootEventSpec captured;
                    private ServerPlayer capturedActor;
                    private int admitRootCalls;
                """ + exactMethod + """
                    private RuntimeAdmissionResult admitRootWithP9Actor(
                            MinecraftServer server,
                            RuntimeRootEventSpec spec,
                            ServerPlayer actor) {
                        admitRootCalls++;
                        captured = spec;
                        capturedActor = actor;
                        if (!spec.executionData().dimension().equals(
                                spec.origin().dimension().location())) {
                            return new RuntimeAdmissionResult.InvalidEvent();
                        }
                        return RuntimeAdmissionResult.Accepted.INSTANCE;
                    }

                    void install(MinecraftServer server, RuntimeServerToken token) {
                        slots.put(server, new ServerSlot(token));
                    }

                    void requestReload() {
                        p9ReloadCloseRequested.set(true);
                    }

                    RuntimeRootEventSpec captured() {
                        return captured;
                    }

                    ServerPlayer capturedActor() {
                        return capturedActor;
                    }

                    int admitRootCalls() {
                        return admitRootCalls;
                    }
                }

                final class Gramarye {
                    static final String MOD_ID = "gramarye";
                }

                final class ServerSlot {
                    final RuntimeServerToken token;

                    ServerSlot(RuntimeServerToken token) {
                        this.token = Objects.requireNonNull(token, "token");
                    }
                """ + exactInstanceState + """
                    static final class P9ActiveDiagnostic {
                    }
                }

                final class RuntimeProjectileContinuationPermit {
                }

                final class RuntimeRevisionLease {
                }

                record RuntimeSkillInstanceSequence(long value) {
                }

                interface RuntimeBudgetAttribution {
                }

                record RuntimeServerToken(long value) {
                }

                record RuntimePlayerId(UUID value) {
                    RuntimePlayerId {
                        Objects.requireNonNull(value, "value");
                    }
                }

                enum RuntimeSchedulePersistence {
                    MEMORY_ONLY
                }

                record RuntimeScheduleSpec(
                        long delayTicks,
                        long deadlineHorizonTicks,
                        RuntimeSchedulePersistence persistence) {
                }

                record PlayerRuntimeBudgetAttribution(
                        RuntimeServerToken serverToken,
                        RuntimePlayerId playerId) implements RuntimeBudgetAttribution {
                }

                record PlayerOrigin(
                        RuntimeServerToken serverToken,
                        net.minecraft.server.level.ServerLevel.Dimension dimension,
                        RuntimePlayerId playerId) {
                }

                record RootTriggerCause(TriggerEventKind kind) {
                }

                record CastGeometryExecutionDataV0(
                        Object dimension,
                        double originX,
                        double originY,
                        double originZ,
                        int directionXQ15,
                        int directionYQ15,
                        int directionZQ15,
                        int profileCode) {
                }

                record RuntimeRootEventSpec(
                        SkillReference skill,
                        int nodeIndex,
                        RuntimeScheduleSpec schedule,
                        PlayerRuntimeBudgetAttribution budgetAttribution,
                        PlayerOrigin origin,
                        Optional<Object> target,
                        RootTriggerCause cause,
                        CastGeometryExecutionDataV0 executionData) {
                }

                enum RuntimeReferenceFailureReason {
                    WRONG_SERVER,
                    WRONG_DIMENSION,
                    MISSING
                }

                interface RuntimeAdmissionResult {
                    record WrongThread() implements RuntimeAdmissionResult {
                    }

                    record ServerNotRunning() implements RuntimeAdmissionResult {
                    }

                    record ServerStopping() implements RuntimeAdmissionResult {
                    }

                    record InvalidEvent() implements RuntimeAdmissionResult {
                    }

                    record InvalidRuntimeReference(RuntimeReferenceFailureReason reason)
                            implements RuntimeAdmissionResult {
                    }

                    enum Accepted implements RuntimeAdmissionResult {
                        INSTANCE
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/P7AuthenticatedIngressMethodHarness.java", """
                package com.yo1no.gramarye;

                import com.yo1no.gramarye.magic.api.id.SkillInstanceId;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import java.util.UUID;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.server.level.ServerPlayer;

                public final class P7AuthenticatedIngressMethodHarness {
                    private P7AuthenticatedIngressMethodHarness() {
                    }

                    public static String run() {
                        var service = new SkillRuntimeService();
                        var playerId = UUID.fromString(
                                "00000000-0000-0000-0000-0000000007a1");
                        var dimension = new Object();
                        var runningServer = new MinecraftServer(true);
                        var level = new ServerLevel(runningServer, dimension);
                        var actor = new ServerPlayer(playerId, level);
                        runningServer.getPlayerList().register(playerId, actor);
                        var reference = new SkillReference();
                        var geometry = new CastGeometryExecutionDataV0(
                                dimension, 1.25, 64.5, -2.75, 32767, 0, 0, 0);

                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                null, actor, reference, geometry), "null server");
                        var validationServer = new MinecraftServer(true);
                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                validationServer, null, reference, geometry), "null actor");
                        check(validationServer.sameThreadCalls() == 0,
                                "actor validation must precede thread access");
                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                validationServer, actor, null, geometry), "null reference");
                        check(validationServer.sameThreadCalls() == 0,
                                "reference validation must precede thread access");
                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                validationServer, actor, reference, null), "null geometry");
                        check(validationServer.sameThreadCalls() == 0,
                                "geometry validation must precede thread access");

                        var wrongThreadServer = new MinecraftServer(false);
                        var wrongThread = service.admitAuthenticatedPlayerCast(
                                wrongThreadServer, actor, reference, geometry);
                        check(wrongThread instanceof RuntimeAdmissionResult.WrongThread,
                                "wrong-thread result");
                        check(wrongThreadServer.sameThreadCalls() == 1,
                                "wrong-thread check count");
                        check(actor.uuidCalls() == 0 && actor.levelCalls() == 0,
                                "wrong-thread actor access");
                        check(service.admitRootCalls() == 0 && service.captured() == null,
                                "wrong-thread mutation");

                        var missingSlotServer = new MinecraftServer(true);
                        var unavailable = service.admitAuthenticatedPlayerCast(
                                missingSlotServer, actor, reference, geometry);
                        check(unavailable instanceof RuntimeAdmissionResult.ServerNotRunning,
                                "missing-slot result");
                        check(actor.uuidCalls() == 0 && actor.levelCalls() == 0,
                                "missing-slot actor access");
                        check(service.admitRootCalls() == 0 && service.captured() == null,
                                "missing-slot root admission");

                        var reloadService = new SkillRuntimeService();
                        var reloadServer = new MinecraftServer(true);
                        reloadService.install(reloadServer, new RuntimeServerToken(40L));
                        reloadService.requestReload();
                        var reloading = reloadService.admitAuthenticatedPlayerCast(
                                reloadServer, actor, reference, geometry);
                        check(reloading instanceof RuntimeAdmissionResult.ServerStopping,
                                "reload-close result");
                        check(actor.uuidCalls() == 0 && actor.levelCalls() == 0,
                                "reload-close actor access");
                        check(reloadService.admitRootCalls() == 0
                                        && reloadService.captured() == null,
                                "reload-close root admission");

                        var token = new RuntimeServerToken(41L);
                        service.install(runningServer, token);
                        var accepted = service.admitAuthenticatedPlayerCast(
                                runningServer, actor, reference, geometry);
                        check(accepted == RuntimeAdmissionResult.Accepted.INSTANCE,
                                "tail result identity");
                        check(service.admitRootCalls() == 1, "admitRoot exact once");
                        check(service.capturedActor() == actor,
                                "exact ingress actor must reach actor-aware root admission");
                        check(actor.uuidCalls() == 1 && actor.levelCalls() == 1,
                                "actor scalar reads exact once");
                        check(level.dimensionCalls() == 2, "dimension read exact twice");

                        var spec = service.captured();
                        var witnessState = new ServerSlot.InstanceState(
                                new SkillInstanceId(UUID.fromString(
                                        "00000000-0000-0000-0000-0000000009a1")),
                                new RuntimeSkillInstanceSequence(1L),
                                spec.budgetAttribution(),
                                new RuntimeRevisionLease(),
                                actor);
                        var replacement = new ServerPlayer(playerId, level);
                        check(witnessState.hasP9AuthenticatedActorWitness(actor),
                                "constructor must bind the exact actor object");
                        check(!witnessState.hasP9AuthenticatedActorWitness(replacement),
                                "same-UUID replacement must not match the witness");
                        check(!witnessState.hasP9AuthenticatedActorWitness(null),
                                "null candidate must not match the witness");
                        witnessState.clearP9AuthenticatedActorWitness();
                        check(!witnessState.hasP9AuthenticatedActorWitness(actor),
                                "clear must remove the exact witness");
                        witnessState.clearP9AuthenticatedActorWitness();
                        check(!witnessState.hasP9AuthenticatedActorWitness(actor),
                                "clear must remain idempotent");
                        check(spec.skill() == reference, "exact reference identity");
                        check(spec.nodeIndex() == 0, "node index");
                        check(spec.schedule().delayTicks() == 0L,
                                "delay coordinate");
                        check(spec.schedule().deadlineHorizonTicks() == 100L,
                                "deadline coordinate");
                        check(spec.schedule().persistence()
                                        == RuntimeSchedulePersistence.MEMORY_ONLY,
                                "memory-only retention");
                        check(spec.budgetAttribution().serverToken() == token,
                                "internal attribution token");
                        check(spec.origin().serverToken() == token,
                                "internal origin token");
                        check(spec.budgetAttribution().playerId().value().equals(playerId),
                                "attribution player identity");
                        check(spec.origin().playerId().value().equals(playerId),
                                "origin player identity");
                        check(spec.origin().dimension().location() == dimension,
                                "authoritative dimension identity");
                        check(spec.target().isEmpty(), "NO_TARGET");
                        check(spec.cause().kind().id().getNamespace().equals("gramarye"),
                                "cause namespace");
                        check(spec.cause().kind().id().getPath().equals("active_cast"),
                                "cause path");
                        check(spec.executionData() == geometry,
                                "frozen geometry identity");

                        var replacementServer = new MinecraftServer(true);
                        var replacementLevel = new ServerLevel(replacementServer, dimension);
                        var originalActor = new ServerPlayer(playerId, replacementLevel);
                        var replacementActor = new ServerPlayer(playerId, replacementLevel);
                        replacementServer.getPlayerList().register(playerId, replacementActor);
                        var replacementService = new SkillRuntimeService();
                        replacementService.install(
                                replacementServer, new RuntimeServerToken(44L));
                        var replacementRejected = replacementService
                                .admitAuthenticatedPlayerCast(
                                        replacementServer,
                                        originalActor,
                                        reference,
                                        geometry);
                        check(replacementRejected
                                        instanceof RuntimeAdmissionResult.InvalidRuntimeReference
                                                invalid
                                && invalid.reason() == RuntimeReferenceFailureReason.MISSING,
                                "same-UUID current-player replacement rejection");
                        check(replacementService.captured() == null,
                                "replacement must not reach actor-aware root admission");

                        var disconnectedServer = new MinecraftServer(true);
                        var disconnectedLevel = new ServerLevel(disconnectedServer, dimension);
                        var disconnectedActor = new ServerPlayer(playerId, disconnectedLevel);
                        disconnectedActor.connection.setAcceptingMessages(false);
                        disconnectedServer.getPlayerList().register(playerId, disconnectedActor);
                        var disconnectedService = new SkillRuntimeService();
                        disconnectedService.install(
                                disconnectedServer, new RuntimeServerToken(45L));
                        var disconnected = disconnectedService.admitAuthenticatedPlayerCast(
                                disconnectedServer,
                                disconnectedActor,
                                reference,
                                geometry);
                        check(disconnected
                                        instanceof RuntimeAdmissionResult.InvalidRuntimeReference
                                                invalid
                                && invalid.reason() == RuntimeReferenceFailureReason.MISSING,
                                "non-accepting connection rejection");
                        check(disconnectedService.captured() == null,
                                "non-accepting actor must not reach actor-aware root admission");

                        var mismatchedService = new SkillRuntimeService();
                        mismatchedService.install(runningServer, new RuntimeServerToken(42L));
                        var mismatchedGeometry = new CastGeometryExecutionDataV0(
                                new Object(), 1.25, 64.5, -2.75, 32767, 0, 0, 0);
                        var mismatched = mismatchedService.admitAuthenticatedPlayerCast(
                                runningServer, actor, reference, mismatchedGeometry);
                        check(mismatched instanceof RuntimeAdmissionResult.InvalidRuntimeReference
                                        invalid
                                && invalid.reason()
                                        == RuntimeReferenceFailureReason.WRONG_DIMENSION,
                                "P5 same-dimension rejection");
                        check(mismatchedService.captured() == null,
                                "P5 must reject mismatched geometry before root admission");

                        var foreignServer = new MinecraftServer(true);
                        var foreignService = new SkillRuntimeService();
                        foreignService.install(foreignServer, new RuntimeServerToken(43L));
                        var foreign = foreignService.admitAuthenticatedPlayerCast(
                                foreignServer, actor, reference, geometry);
                        check(foreign instanceof RuntimeAdmissionResult.InvalidRuntimeReference
                                        invalid
                                && invalid.reason() == RuntimeReferenceFailureReason.WRONG_SERVER,
                                "P5 wrong-server actor rejection");
                        check(foreignService.captured() == null,
                                "wrong-server actor must not reach root admission");
                        return "PASS";
                    }

                    private static void expectNullPointer(Runnable action, String message) {
                        try {
                            action.run();
                        } catch (NullPointerException expected) {
                            return;
                        }
                        throw new AssertionError(message);
                    }

                    private static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }
                }
                """);

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(
                    resourceLocation,
                    server,
                    serverLevel,
                    serverPlayer,
                    skillInstanceId,
                    skillReference,
                    triggerEventKind,
                    serviceShell,
                    harness));
            var options = List.of(
                    "--release", "21",
                    "-proc:none",
                    "-Xlint:all,-auxiliaryclass",
                    "-Werror",
                    "-classpath", outputRoot.toString(),
                    "-d", outputRoot.toString());
            success = Boolean.TRUE.equals(compiler.getTask(
                    null, files, diagnostics, options, null, units).call());
        }
        assertTrue(success, () -> diagnostics.getDiagnostics().toString());

        try (var loader = new URLClassLoader(
                new java.net.URL[] {outputRoot.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            var behavioralHarness = Class.forName(
                    "com.yo1no.gramarye.P7AuthenticatedIngressMethodHarness",
                    true,
                    loader);
            assertEquals("PASS", behavioralHarness.getMethod("run").invoke(null));
        }
    }

    private static String section(String source, String start, String end) {
        var first = source.indexOf(start);
        var last = source.indexOf(end, first + start.length());
        assertTrue(first >= 0 && last > first,
                () -> "source section unavailable: " + start + " -> " + end);
        return source.substring(first, last);
    }

    private static int occurrences(String source, String fragment) {
        var count = 0;
        var from = 0;
        while (true) {
            var found = source.indexOf(fragment, from);
            if (found < 0) {
                return count;
            }
            count++;
            from = found + fragment.length();
        }
    }

    private static Path write(Path root, String relativePath, String content)
            throws IOException {
        var target = root.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
        return target;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("project root unavailable");
    }
}
