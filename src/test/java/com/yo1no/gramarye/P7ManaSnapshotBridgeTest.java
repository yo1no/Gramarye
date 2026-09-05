package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.network.P7ServerAuthorizationBoundary;
import com.yo1no.gramarye.magic.runtime.mana.P7ManaSnapshotBridge;
import java.lang.reflect.Modifier;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.tools.ToolProvider;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P7ManaSnapshotBridgeTest {
    @Test
    void observationCapabilityFailsBeforeActorValidation() {
        var failure = assertThrows(NullPointerException.class,
                () -> P7ManaSnapshotBridge.observeBalance(null, null));
        assertEquals("capability", failure.getMessage());
    }

    @Test
    void observationValidCapabilityRejectsNullActor() {
        var failure = assertThrows(NullPointerException.class,
                () -> P7ManaSnapshotBridge.observeBalance(
                        P6RuntimeExecutionCapability.forRuntimeAdapter(), null));
        assertEquals("actor", failure.getMessage());
    }

    @Test
    void observationHasExactlyOneScalarPublicOperationAndNoRetainedState()
            throws Exception {
        var type = P7ManaSnapshotBridge.class;
        var observe = type.getDeclaredMethod(
                "observeBalance", P6RuntimeExecutionCapability.class, ServerPlayer.class);
        assertTrue(Modifier.isPublic(type.getModifiers()));
        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertEquals(1, type.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(type.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(0, type.getDeclaredFields().length);
        assertEquals(List.of(observe), Arrays.asList(type.getDeclaredMethods()));
        assertTrue(Modifier.isPublic(observe.getModifiers()));
        assertTrue(Modifier.isStatic(observe.getModifiers()));
        assertSame(long.class, observe.getReturnType());
    }

    @Test
    void loginPortCapabilityIsFirstAndValidAcquisitionsShareOneNoncapturingIdentity() {
        var failure = assertThrows(NullPointerException.class,
                () -> P7ServerAuthorizationBoundary.loginReadyPort(null));
        assertEquals("capability", failure.getMessage());
        var capability = P6RuntimeExecutionCapability.forRuntimeAdapter();
        var port = P7ServerAuthorizationBoundary.loginReadyPort(capability);
        assertSame(port, P7ServerAuthorizationBoundary.loginReadyPort(capability));
        assertTrue(port.getClass().isSynthetic());
        assertEquals(0, port.getClass().getDeclaredFields().length);
    }

    @Test
    void actualBridgeSourceRejectsWrongThreadBeforeAnyAttachmentRead(
            @TempDir Path temporary) throws Exception {
        var production = projectRoot().resolve("src/main/java/com/yo1no/gramarye/magic/runtime/mana/"
                + "P7ManaSnapshotBridge.java");
        var capability = writeSource(temporary,
                "com/yo1no/gramarye/P6RuntimeExecutionCapability.java", """
                package com.yo1no.gramarye;
                public final class P6RuntimeExecutionCapability {}
                """);
        var player = writeSource(temporary, "net/minecraft/server/level/ServerPlayer.java", """
                package net.minecraft.server.level;
                public final class ServerPlayer {
                    public boolean logicThread = true;
                    public boolean available = true;
                    public long balance;
                    public int guardCalls;
                    public int stateReads;
                    public int balanceReads;
                    public RuntimeException failure;
                }
                """);
        var account = writeSource(temporary,
                "com/yo1no/gramarye/magic/runtime/mana/PlayerManaAccountAccess.java", """
                package com.yo1no.gramarye.magic.runtime.mana;
                import net.minecraft.server.level.ServerPlayer;
                final class PlayerManaAccountAccess {
                    private final ServerPlayer actor;
                    PlayerManaAccountAccess(ServerPlayer actor) { this.actor = actor; }
                    boolean isLogicThread() {
                        actor.guardCalls++;
                        return actor.logicThread;
                    }
                }
                """);
        var attachments = writeSource(temporary,
                "com/yo1no/gramarye/magic/runtime/mana/ManaAttachments.java", """
                package com.yo1no.gramarye.magic.runtime.mana;
                import net.minecraft.server.level.ServerPlayer;
                final class ManaAttachments {
                    static ManaState state(ServerPlayer actor) {
                        actor.stateReads++;
                        if (actor.failure != null) { throw actor.failure; }
                        return new ManaState(actor);
                    }
                }
                """);
        var state = writeSource(temporary,
                "com/yo1no/gramarye/magic/runtime/mana/ManaState.java", """
                package com.yo1no.gramarye.magic.runtime.mana;
                import net.minecraft.server.level.ServerPlayer;
                final class ManaState {
                    private final ServerPlayer actor;
                    ManaState(ServerPlayer actor) { this.actor = actor; }
                    ManaAvailability availability() {
                        return actor.available ? ManaAvailability.AVAILABLE
                                : ManaAvailability.UNAVAILABLE;
                    }
                    long balance() {
                        actor.balanceReads++;
                        return actor.balance;
                    }
                }
                """);
        var availability = writeSource(temporary,
                "com/yo1no/gramarye/magic/runtime/mana/ManaAvailability.java", """
                package com.yo1no.gramarye.magic.runtime.mana;
                enum ManaAvailability { AVAILABLE, UNAVAILABLE }
                """);
        var harness = writeSource(temporary,
                "com/yo1no/gramarye/magic/runtime/mana/ObservationHarness.java", """
                package com.yo1no.gramarye.magic.runtime.mana;
                import com.yo1no.gramarye.P6RuntimeExecutionCapability;
                import net.minecraft.server.level.ServerPlayer;
                public final class ObservationHarness {
                    private ObservationHarness() {}
                    public static String run() {
                        var capability = new P6RuntimeExecutionCapability();
                        var actor = new ServerPlayer();
                        actor.logicThread = false;
                        try {
                            P7ManaSnapshotBridge.observeBalance(capability, actor);
                            throw new AssertionError("wrong-thread observation accepted");
                        } catch (IllegalStateException expected) {
                            if (actor.guardCalls != 1 || actor.stateReads != 0) {
                                throw new AssertionError("wrong-thread truth read");
                            }
                        }
                        actor.logicThread = true;
                        actor.available = false;
                        if (P7ManaSnapshotBridge.observeBalance(capability, actor) != -1
                                || actor.stateReads != 1 || actor.balanceReads != 0) {
                            throw new AssertionError("unavailable truth repaired or read");
                        }
                        actor.available = true;
                        actor.balance = 1000000000L;
                        if (P7ManaSnapshotBridge.observeBalance(capability, actor) != actor.balance
                                || actor.stateReads != 2 || actor.balanceReads != 1) {
                            throw new AssertionError("available scalar observation mismatch");
                        }
                        var failure = new IllegalStateException("expected-observation-fault");
                        actor.failure = failure;
                        try {
                            P7ManaSnapshotBridge.observeBalance(capability, actor);
                            throw new AssertionError("truth fault disappeared");
                        } catch (RuntimeException observed) {
                            if (observed != failure) {
                                throw new AssertionError("observation replaced throwable");
                            }
                        }
                        return "PASS";
                    }
                }
                """);
        var output = Files.createDirectory(temporary.resolve("classes"));
        var compiler = ToolProvider.getSystemJavaCompiler();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            var files = manager.getJavaFileObjectsFromPaths(List.of(production, capability,
                    player, account, attachments, state, availability, harness));
            assertTrue(compiler.getTask(null, manager, null,
                    List.of("--release", "21", "-proc:none", "-Xlint:all", "-Werror",
                            "-classpath", output.toString(), "-d", output.toString()),
                    null, files).call());
        }
        try (var loader = new URLClassLoader(new java.net.URL[] {output.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            assertEquals("PASS", loader.loadClass(
                            "com.yo1no/gramarye/magic/runtime/mana/ObservationHarness"
                                    .replace('/', '.'))
                    .getMethod("run").invoke(null));
        }
    }

    private static Path writeSource(Path root, String relative, String source) throws Exception {
        var path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        return Files.writeString(path, source);
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath(); candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root unavailable");
    }
}
