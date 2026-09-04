package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P7AdvisoryTargetValidatorTest {
    private static final Path SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/network/"
                    + "P7AdvisoryTargetValidator.java");

    @TempDir
    Path temporary;

    @Test
    void noHintAndAimOnlyTakeTheDeterministicLocalValidPath() {
        var validator = new P7AdvisoryTargetValidator();

        assertSame(
                P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                validator.validate(null, null, null, null));
        assertSame(
                P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                validator.validate(null, null, new AimHint(32767, -32767, 1), null));
    }

    @Test
    void validatorIsPackagePrivateFinalStatelessAndRetainsNoPlatformObject()
            throws Exception {
        var type = P7AdvisoryTargetValidator.class;
        var method = type.getDeclaredMethod(
                "validate",
                MinecraftServer.class,
                ServerPlayer.class,
                AimHint.class,
                EntityHint.class);

        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertFalse(Modifier.isProtected(type.getModifiers()));
        assertEquals(0, type.getDeclaredFields().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertFalse(Modifier.isPublic(method.getModifiers()));
        assertFalse(Modifier.isProtected(method.getModifiers()));
        assertFalse(Modifier.isPrivate(method.getModifiers()));
        assertSame(P7ServerAuthorizationBoundary.TargetDisposition.class,
                method.getReturnType());
        assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                !Modifier.isPublic(constructor.getModifiers())
                        && !Modifier.isProtected(constructor.getModifiers())));
    }

    @Test
    void entityHintUsesOnlyActorLevelLoadedNetworkIdLookupAndClosedValidation()
            throws Exception {
        var source = read(SOURCE);

        assertTrue(source.contains("var actorLevel = actor.serverLevel();"));
        assertTrue(source.contains(
                "actorLevel.getEntity(entityHint.networkId())"));
        assertTrue(source.contains("target.getId() != entityHint.networkId()"));
        assertTrue(source.contains("target.level() != actorLevel"));
        assertTrue(source.contains("target.getServer() != server"));
        assertTrue(source.contains("!target.isAddedToLevel()"));
        assertTrue(source.contains("target.isRemoved()"));
        assertTrue(source.contains("!target.isAlive()"));
        assertTrue(source.contains("TargetDisposition.TARGET_UNAVAILABLE"));
        assertTrue(source.contains("TargetDisposition.INVALID_TARGET"));

        assertFalse(source.contains("getAllLevels"));
        assertFalse(source.contains("getChunk"));
        assertFalse(source.contains("setChunkForced"));
        assertFalse(source.contains("forceLoad"));
        assertFalse(source.contains("java.util.UUID"));
        assertFalse(source.contains("getEntityOrPart"));
        assertFalse(source.contains("getEntity(entityHint.networkId(),"));
        assertProductionValidatorBehavior();
    }

    @Test
    void aimNormalizationIsLocalDeterministicAndCannotProduceGameplayAuthority() {
        var source = read(SOURCE);

        assertTrue(source.contains("AimHint.componentsValid("));
        assertTrue(source.contains("StrictMath.sqrt(lengthSquared)"));
        assertTrue(source.contains("Double.isFinite("));
        assertFalse(source.contains("magnitude"));
        assertFalse(source.contains("mana"));
        assertFalse(source.contains("SkillReference"));
        assertFalse(source.contains("RuntimeEvent"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception failure) {
            throw new AssertionError("cannot read " + path, failure);
        }
    }

    private void assertProductionValidatorBehavior() throws Exception {
        var sourceRoot = Files.createDirectories(temporary.resolve("validator-source"));
        var outputRoot = Files.createDirectories(temporary.resolve("validator-classes"));
        var serverStub = write(sourceRoot,
                "net/minecraft/server/MinecraftServer.java", """
                package net.minecraft.server;

                import java.util.List;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.world.entity.Entity;

                public final class MinecraftServer {
                    private int globalLookupCalls;

                    public Entity getEntity(int ignored) {
                        globalLookupCalls++;
                        return null;
                    }

                    public Iterable<ServerLevel> getAllLevels() {
                        globalLookupCalls++;
                        return List.of();
                    }

                    public int globalLookupCalls() {
                        return globalLookupCalls;
                    }
                }
                """);
        var levelStub = write(sourceRoot,
                "net/minecraft/server/level/ServerLevel.java", """
                package net.minecraft.server.level;

                import java.util.function.Consumer;
                import net.minecraft.world.entity.Entity;

                public final class ServerLevel {
                    private final Entity entity;
                    private final Consumer<String> recorder;
                    private int loadedLookupCalls;
                    private int forbiddenLookupCalls;

                    public ServerLevel(Entity entity, Consumer<String> recorder) {
                        this.entity = entity;
                        this.recorder = recorder;
                    }

                    public Entity getEntity(int networkId) {
                        loadedLookupCalls++;
                        recorder.accept("level.getEntity:" + networkId);
                        return entity;
                    }

                    public Entity getEntityOrPart(int ignored) {
                        forbiddenLookupCalls++;
                        return null;
                    }

                    public Object getChunk(int x, int z) {
                        forbiddenLookupCalls++;
                        return null;
                    }

                    public void setChunkForced(int x, int z, boolean forced) {
                        forbiddenLookupCalls++;
                    }

                    public void forceLoad(int x, int z) {
                        forbiddenLookupCalls++;
                    }

                    public int loadedLookupCalls() {
                        return loadedLookupCalls;
                    }

                    public int forbiddenLookupCalls() {
                        return forbiddenLookupCalls;
                    }
                }
                """);
        var playerStub = write(sourceRoot,
                "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;

                import java.util.function.Consumer;

                public final class ServerPlayer {
                    private final ServerLevel level;
                    private final Consumer<String> recorder;
                    private int levelCalls;

                    public ServerPlayer(ServerLevel level, Consumer<String> recorder) {
                        this.level = level;
                        this.recorder = recorder;
                    }

                    public ServerLevel serverLevel() {
                        levelCalls++;
                        recorder.accept("actor.level");
                        return level;
                    }

                    public int levelCalls() {
                        return levelCalls;
                    }
                }
                """);
        var entityStub = write(sourceRoot,
                "net/minecraft/world/entity/Entity.java", """
                package net.minecraft.world.entity;

                import java.util.function.Consumer;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerLevel;

                public final class Entity {
                    private final int id;
                    private ServerLevel level;
                    private final MinecraftServer server;
                    private final boolean added;
                    private final boolean removed;
                    private final boolean alive;
                    private final Consumer<String> recorder;

                    public Entity(
                            int id,
                            ServerLevel level,
                            MinecraftServer server,
                            boolean added,
                            boolean removed,
                            boolean alive,
                            Consumer<String> recorder) {
                        this.id = id;
                        this.level = level;
                        this.server = server;
                        this.added = added;
                        this.removed = removed;
                        this.alive = alive;
                        this.recorder = recorder;
                    }

                    public void installLevel(ServerLevel installedLevel) {
                        level = installedLevel;
                    }

                    public int getId() {
                        recorder.accept("entity.id");
                        return id;
                    }

                    public ServerLevel level() {
                        recorder.accept("entity.level");
                        return level;
                    }

                    public MinecraftServer getServer() {
                        recorder.accept("entity.server");
                        return server;
                    }

                    public boolean isAddedToLevel() {
                        recorder.accept("entity.added");
                        return added;
                    }

                    public boolean isRemoved() {
                        recorder.accept("entity.removed");
                        return removed;
                    }

                    public boolean isAlive() {
                        recorder.accept("entity.alive");
                        return alive;
                    }
                }
                """);
        var harness = write(sourceRoot,
                "com/yo1no/gramarye/magic/network/P7TargetValidatorExecutionHarness.java",
                """
                package com.yo1no.gramarye.magic.network;

                import java.util.ArrayList;
                import java.util.List;
                import net.minecraft.server.MinecraftServer;
                import net.minecraft.server.level.ServerLevel;
                import net.minecraft.server.level.ServerPlayer;
                import net.minecraft.world.entity.Entity;

                public final class P7TargetValidatorExecutionHarness {
                    private P7TargetValidatorExecutionHarness() {
                    }

                    public static String run() {
                        noHintDoesNotTouchPlatform();
                        aimOnlyDoesNotTouchPlatform();
                        validLoadedIdUsesOneLocalLookup();
                        missingLoadedIdIsUnavailable();
                        removedTargetIsInvalid();
                        wrongDimensionAndServerAreInvalid();
                        check(P7AdvisoryTargetValidator.class.getDeclaredFields().length == 0,
                                "validator retained state");
                        return "PASS";
                    }

                    private static void noHintDoesNotTouchPlatform() {
                        AimHint.reset();
                        var validator = new P7AdvisoryTargetValidator();
                        var disposition = validator.validate(null, null, null, null);
                        check(disposition ==
                                P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                                "no-hint disposition");
                        check(AimHint.componentChecks() == 0, "no-hint aim checks");
                    }

                    private static void aimOnlyDoesNotTouchPlatform() {
                        AimHint.reset();
                        var validator = new P7AdvisoryTargetValidator();
                        var disposition = validator.validate(
                                null, null, new AimHint(3, 4, 12), null);
                        check(disposition ==
                                P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                                "aim-only disposition");
                        check(AimHint.componentChecks() == 1, "aim check count");
                    }

                    private static void validLoadedIdUsesOneLocalLookup() {
                        var trace = new Trace();
                        var server = new MinecraftServer();
                        var target = new Entity(
                                91, null, server, true, false, true, trace::add);
                        var level = new ServerLevel(target, trace::add);
                        target.installLevel(level);
                        var actor = new ServerPlayer(level, trace::add);

                        var disposition = new P7AdvisoryTargetValidator().validate(
                                server, actor, null, new EntityHint(91));

                        check(disposition ==
                                P7ServerAuthorizationBoundary.TargetDisposition.VALID,
                                "valid target disposition");
                        trace.expect(
                                "actor.level", "level.getEntity:91", "entity.id",
                                "entity.level", "entity.server", "entity.added",
                                "entity.removed", "entity.alive");
                        check(actor.levelCalls() == 1, "actor-level lookup count");
                        check(level.loadedLookupCalls() == 1, "loaded-ID lookup count");
                        assertNoForbiddenLookup(server, level);
                    }

                    private static void missingLoadedIdIsUnavailable() {
                        var trace = new Trace();
                        var server = new MinecraftServer();
                        var level = new ServerLevel(null, trace::add);
                        var actor = new ServerPlayer(level, trace::add);

                        var disposition = new P7AdvisoryTargetValidator().validate(
                                server, actor, null, new EntityHint(92));

                        check(disposition == P7ServerAuthorizationBoundary
                                .TargetDisposition.TARGET_UNAVAILABLE,
                                "missing target disposition");
                        trace.expect("actor.level", "level.getEntity:92");
                        check(level.loadedLookupCalls() == 1, "missing local lookup count");
                        assertNoForbiddenLookup(server, level);
                    }

                    private static void removedTargetIsInvalid() {
                        var trace = new Trace();
                        var server = new MinecraftServer();
                        var target = new Entity(
                                93, null, server, true, true, true, trace::add);
                        var level = new ServerLevel(target, trace::add);
                        target.installLevel(level);
                        var actor = new ServerPlayer(level, trace::add);

                        var disposition = new P7AdvisoryTargetValidator().validate(
                                server, actor, null, new EntityHint(93));

                        check(disposition == P7ServerAuthorizationBoundary
                                .TargetDisposition.INVALID_TARGET,
                                "removed target disposition");
                        trace.expect(
                                "actor.level", "level.getEntity:93", "entity.id",
                                "entity.level", "entity.server", "entity.added",
                                "entity.removed");
                        assertNoForbiddenLookup(server, level);
                    }

                    private static void wrongDimensionAndServerAreInvalid() {
                        var dimensionTrace = new Trace();
                        var server = new MinecraftServer();
                        var otherLevel = new ServerLevel(null, ignored -> { });
                        var wrongDimension = new Entity(
                                94, otherLevel, server, true, false, true,
                                dimensionTrace::add);
                        var actorLevel = new ServerLevel(
                                wrongDimension, dimensionTrace::add);
                        var actor = new ServerPlayer(actorLevel, dimensionTrace::add);
                        var dimensionDisposition = new P7AdvisoryTargetValidator().validate(
                                server, actor, null, new EntityHint(94));
                        check(dimensionDisposition == P7ServerAuthorizationBoundary
                                .TargetDisposition.INVALID_TARGET,
                                "wrong-dimension disposition");
                        dimensionTrace.expect(
                                "actor.level", "level.getEntity:94", "entity.id",
                                "entity.level");
                        assertNoForbiddenLookup(server, actorLevel);

                        var serverTrace = new Trace();
                        var authoritativeServer = new MinecraftServer();
                        var otherServer = new MinecraftServer();
                        var wrongServer = new Entity(
                                95, null, otherServer, true, false, true,
                                serverTrace::add);
                        var serverLevel = new ServerLevel(wrongServer, serverTrace::add);
                        wrongServer.installLevel(serverLevel);
                        var serverActor = new ServerPlayer(serverLevel, serverTrace::add);
                        var serverDisposition = new P7AdvisoryTargetValidator().validate(
                                authoritativeServer,
                                serverActor,
                                null,
                                new EntityHint(95));
                        check(serverDisposition == P7ServerAuthorizationBoundary
                                .TargetDisposition.INVALID_TARGET,
                                "wrong-server disposition");
                        serverTrace.expect(
                                "actor.level", "level.getEntity:95", "entity.id",
                                "entity.level", "entity.server");
                        assertNoForbiddenLookup(authoritativeServer, serverLevel);
                        check(otherServer.globalLookupCalls() == 0,
                                "wrong server global lookup");
                    }

                    private static void assertNoForbiddenLookup(
                            MinecraftServer server, ServerLevel level) {
                        check(server.globalLookupCalls() == 0, "global lookup count");
                        check(level.forbiddenLookupCalls() == 0,
                                "chunk/force/part lookup count");
                    }

                    static void check(boolean condition, String message) {
                        if (!condition) {
                            throw new AssertionError(message);
                        }
                    }
                }

                final class Trace {
                    private final List<String> events = new ArrayList<>();

                    void add(String event) {
                        events.add(event);
                    }

                    void expect(String... expected) {
                        P7TargetValidatorExecutionHarness.check(
                                events.equals(List.of(expected)),
                                "events expected=" + List.of(expected) + " actual=" + events);
                    }
                }

                record EntityHint(int networkId) {
                }

                final class AimHint {
                    private static int checks;
                    private final int x;
                    private final int y;
                    private final int z;

                    AimHint(int x, int y, int z) {
                        this.x = x;
                        this.y = y;
                        this.z = z;
                    }

                    static boolean componentsValid(int x, int y, int z) {
                        checks++;
                        return x != 0 || y != 0 || z != 0;
                    }

                    static void reset() {
                        checks = 0;
                    }

                    static int componentChecks() {
                        return checks;
                    }

                    int x() {
                        return x;
                    }

                    int y() {
                        return y;
                    }

                    int z() {
                        return z;
                    }
                }

                final class P7ServerAuthorizationBoundary {
                    enum TargetDisposition {
                        VALID,
                        TARGET_UNAVAILABLE,
                        INVALID_TARGET
                    }

                    private P7ServerAuthorizationBoundary() {
                    }
                }

                final class P7SemanticInvariantException extends RuntimeException {
                    private static final long serialVersionUID = 1L;

                    P7SemanticInvariantException(String message) {
                        super(message);
                    }
                }
                """);

        var compiler = ToolProvider.getSystemJavaCompiler();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        boolean success;
        try (var files = compiler.getStandardFileManager(
                diagnostics, java.util.Locale.ROOT, StandardCharsets.UTF_8)) {
            var units = files.getJavaFileObjectsFromPaths(List.of(
                    SOURCE, serverStub, levelStub, playerStub, entityStub, harness));
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
                    "com.yo1no.gramarye.magic.network.P7TargetValidatorExecutionHarness",
                    true,
                    loader);
            assertEquals("PASS", behavioralHarness.getMethod("run").invoke(null));
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
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new AssertionError("project root is unavailable");
        }
        return current;
    }
}
