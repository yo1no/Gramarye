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
                SkillReference.class);

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
                                SkillReference.class),
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
                "RuntimeAdmissionResult admitRoot(");
        var compact = ingress.replaceAll("\\s+", "");
        var wrongThread = ingress.indexOf(
                "return new RuntimeAdmissionResult.WrongThread();");
        var slotRead = ingress.indexOf("var slot = slots.get(server);");

        assertAll(
                () -> assertTrue(wrongThread >= 0),
                () -> assertTrue(slotRead > wrongThread),
                () -> assertFalse(ingress.substring(0, wrongThread).contains("slots.")),
                () -> assertEquals(1, occurrences(ingress, "return admitRoot(")),
                () -> assertEquals(1, occurrences(ingress, "new RuntimeRootEventSpec(")),
                () -> assertEquals(0, occurrences(ingress, "latestReference(")),
                () -> assertEquals(0, occurrences(ingress, "new EventId(")),
                () -> assertEquals(0, occurrences(ingress, "RuntimeEventToken")),
                () -> assertEquals(0, occurrences(ingress, "RuntimeCancellationToken")),
                () -> assertEquals(0, occurrences(ingress, "executionPort")),
                () -> assertTrue(compact.contains(
                        "newRuntimeRootEventSpec(exactReference,0,")),
                () -> assertTrue(compact.contains(
                        "newRuntimeScheduleSpec(0,0,RuntimeSchedulePersistence.MEMORY_ONLY)")),
                () -> assertTrue(compact.contains(
                        "newPlayerRuntimeBudgetAttribution(slot.token,playerId)")),
                () -> assertTrue(compact.contains(
                        "newPlayerOrigin(slot.token,actor.serverLevel().dimension(),playerId)")),
                () -> assertEquals(1, occurrences(ingress, "Optional.empty()")),
                () -> assertTrue(compact.contains(
                        "ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID,"
                                + "\"active_cast\")")),
                () -> assertTrue(compact.contains("NoRuntimeExecutionData.INSTANCE")));
    }

    @Test
    void exactProductionIngressMethodExecutesTokenSafeCoordinatesAndEarlyFailures()
            throws Exception {
        var productionSource = Files.readString(SERVICE_SOURCE);
        var exactMethod = section(
                productionSource,
                "    RuntimeAdmissionResult admitAuthenticatedPlayerCast(",
                "    RuntimeAdmissionResult admitRoot(");
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

                public final class MinecraftServer {
                    private final boolean sameThread;
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
                }
                """);
        var serverLevel = write(sourceRoot,
                "net/minecraft/server/level/ServerLevel.java", """
                package net.minecraft.server.level;

                public final class ServerLevel {
                    private final Object dimension;
                    private int dimensionCalls;

                    public ServerLevel(Object dimension) {
                        this.dimension = dimension;
                    }

                    public Object dimension() {
                        dimensionCalls++;
                        return dimension;
                    }

                    public int dimensionCalls() {
                        return dimensionCalls;
                    }
                }
                """);
        var serverPlayer = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                import java.util.UUID;

                public final class ServerPlayer {
                    private final UUID playerId;
                    private final ServerLevel level;
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

                    public int uuidCalls() {
                        return uuidCalls;
                    }

                    public int levelCalls() {
                        return levelCalls;
                    }
                }
                """);
        var skillReference = write(sourceRoot,
                "com/yo1no/gramarye/magic/definition/document/SkillReference.java", """
                package com.yo1no.gramarye.magic.definition.document;

                public final class SkillReference {
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

                import com.yo1no.gramarye.magic.capability.TriggerEventKind;
                import com.yo1no.gramarye.magic.definition.document.SkillReference;
                import java.util.IdentityHashMap;
                import java.util.Objects;
                import java.util.Optional;
                import java.util.UUID;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerPlayer;

                final class SkillRuntimeService {
                    private final IdentityHashMap<MinecraftServer, ServerSlot> slots =
                            new IdentityHashMap<>();
                    private RuntimeRootEventSpec captured;
                    private int admitRootCalls;
                """ + exactMethod + """
                    RuntimeAdmissionResult admitRoot(
                            MinecraftServer server, RuntimeRootEventSpec spec) {
                        admitRootCalls++;
                        captured = spec;
                        return RuntimeAdmissionResult.Accepted.INSTANCE;
                    }

                    void install(MinecraftServer server, RuntimeServerToken token) {
                        slots.put(server, new ServerSlot(token));
                    }

                    RuntimeRootEventSpec captured() {
                        return captured;
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
                        RuntimeServerToken serverToken, RuntimePlayerId playerId) {
                }

                record PlayerOrigin(
                        RuntimeServerToken serverToken, Object dimension, RuntimePlayerId playerId) {
                }

                record RootTriggerCause(TriggerEventKind kind) {
                }

                enum NoRuntimeExecutionData {
                    INSTANCE
                }

                record RuntimeRootEventSpec(
                        SkillReference skill,
                        int nodeIndex,
                        RuntimeScheduleSpec schedule,
                        PlayerRuntimeBudgetAttribution budgetAttribution,
                        PlayerOrigin origin,
                        Optional<Object> target,
                        RootTriggerCause cause,
                        NoRuntimeExecutionData executionData) {
                }

                interface RuntimeAdmissionResult {
                    record WrongThread() implements RuntimeAdmissionResult {
                    }

                    record ServerNotRunning() implements RuntimeAdmissionResult {
                    }

                    enum Accepted implements RuntimeAdmissionResult {
                        INSTANCE
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/P7AuthenticatedIngressMethodHarness.java", """
                package com.yo1no.gramarye;

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
                        var level = new ServerLevel(dimension);
                        var actor = new ServerPlayer(playerId, level);
                        var reference = new SkillReference();

                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                null, actor, reference), "null server");
                        var validationServer = new MinecraftServer(true);
                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                validationServer, null, reference), "null actor");
                        check(validationServer.sameThreadCalls() == 0,
                                "actor validation must precede thread access");
                        expectNullPointer(() -> service.admitAuthenticatedPlayerCast(
                                validationServer, actor, null), "null reference");
                        check(validationServer.sameThreadCalls() == 0,
                                "reference validation must precede thread access");

                        var wrongThreadServer = new MinecraftServer(false);
                        var wrongThread = service.admitAuthenticatedPlayerCast(
                                wrongThreadServer, actor, reference);
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
                                missingSlotServer, actor, reference);
                        check(unavailable instanceof RuntimeAdmissionResult.ServerNotRunning,
                                "missing-slot result");
                        check(actor.uuidCalls() == 0 && actor.levelCalls() == 0,
                                "missing-slot actor access");
                        check(service.admitRootCalls() == 0 && service.captured() == null,
                                "missing-slot root admission");

                        var runningServer = new MinecraftServer(true);
                        var token = new RuntimeServerToken(41L);
                        service.install(runningServer, token);
                        var accepted = service.admitAuthenticatedPlayerCast(
                                runningServer, actor, reference);
                        check(accepted == RuntimeAdmissionResult.Accepted.INSTANCE,
                                "tail result identity");
                        check(service.admitRootCalls() == 1, "admitRoot exact once");
                        check(actor.uuidCalls() == 1 && actor.levelCalls() == 1,
                                "actor scalar reads exact once");
                        check(level.dimensionCalls() == 1, "dimension read exact once");

                        var spec = service.captured();
                        check(spec.skill() == reference, "exact reference identity");
                        check(spec.nodeIndex() == 0, "node index");
                        check(spec.schedule().delayTicks() == 0L,
                                "delay coordinate");
                        check(spec.schedule().deadlineHorizonTicks() == 0L,
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
                        check(spec.origin().dimension() == dimension,
                                "authoritative dimension identity");
                        check(spec.target().isEmpty(), "NO_TARGET");
                        check(spec.cause().kind().id().getNamespace().equals("gramarye"),
                                "cause namespace");
                        check(spec.cause().kind().id().getPath().equals("active_cast"),
                                "cause path");
                        check(spec.executionData() == NoRuntimeExecutionData.INSTANCE,
                                "no execution data");
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
