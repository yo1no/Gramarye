package com.yo1no.gramarye.magic.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

final class P7ServerPlatformBoundaryTest {
    private static final Path NETWORK_SOURCE = projectRoot().resolve(
            "src/main/java/com/yo1no/gramarye/magic/network");

    @Test
    void serverAccessHasTheExactPackageLocalStatelessPlatformSurface()
            throws Exception {
        var type = P7ServerAccess.class;

        assertTrue(Modifier.isFinal(type.getModifiers()));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertFalse(Modifier.isProtected(type.getModifiers()));
        assertEquals(0, type.getDeclaredFields().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertTrue(Arrays.stream(type.getDeclaredConstructors()).allMatch(constructor ->
                !Modifier.isPublic(constructor.getModifiers())
                        && !Modifier.isProtected(constructor.getModifiers())));

        assertSame(MinecraftServer.class,
                type.getDeclaredMethod("currentServer").getReturnType());
        assertSame(boolean.class,
                type.getDeclaredMethod("sameThread", MinecraftServer.class).getReturnType());
        assertSame(boolean.class,
                type.getDeclaredMethod("running", MinecraftServer.class).getReturnType());
        assertSame(ServerPlayer.class,
                type.getDeclaredMethod(
                                "currentPlayer", MinecraftServer.class, UUID.class)
                        .getReturnType());
        assertSame(boolean.class,
                type.getDeclaredMethod(
                                "currentConnectedPlayer",
                                MinecraftServer.class,
                                ServerPlayer.class,
                                UUID.class)
                        .getReturnType());
        assertSame(long.class,
                type.getDeclaredMethod(
                                "authoritativeTick", MinecraftServer.class)
                        .getReturnType());
        assertSame(void.class,
                type.getDeclaredMethod(
                                "disconnectCurrent",
                                MinecraftServer.class,
                                ServerPlayer.class)
                        .getReturnType());
        assertTrue(Arrays.stream(type.getDeclaredMethods()).allMatch(method ->
                !Modifier.isPublic(method.getModifiers())
                        && !Modifier.isProtected(method.getModifiers())
                        && !Modifier.isStatic(method.getModifiers())));
    }

    @Test
    void serverAccessUsesOnlyTheLockedServerPlayerAndLongTickApis() {
        var source = read(NETWORK_SOURCE.resolve("P7ServerAccess.java"));

        assertTrue(source.contains("ServerLifecycleHooks.getCurrentServer()"));
        assertTrue(source.contains(".isSameThread()"));
        assertTrue(source.contains("exactServer.isRunning() && !exactServer.isStopped()"));
        assertTrue(source.contains(".getPlayerList()"));
        assertTrue(source.contains(".getPlayer(authenticatedPlayerId)"));
        assertTrue(source.contains("authenticatedPlayerId.equals(actor.getUUID())"));
        assertTrue(source.contains("connection.isAcceptingMessages()"));
        assertTrue(source.contains("!actor.hasDisconnected()"));
        assertTrue(source.contains(".overworld().getGameTime()"));

        assertFalse(source.contains("System.currentTimeMillis"));
        assertFalse(source.contains("System.nanoTime"));
        assertFalse(source.contains("getTickCount"));
        assertFalse(source.contains("new Thread"));
        assertFalse(source.contains("Executor"));
        assertFalse(source.contains("Random"));
    }

    @Test
    void disconnectPortIsOnePackagePrivateTypedOperationWithoutStaticApi()
            throws Exception {
        var type = P7ServerDisconnectPort.class;
        var method = type.getDeclaredMethod(
                "disconnect", MinecraftServer.class, ServerPlayer.class);

        assertTrue(type.isInterface());
        assertTrue(type.isAnnotationPresent(FunctionalInterface.class));
        assertFalse(Modifier.isPublic(type.getModifiers()));
        assertFalse(Modifier.isProtected(type.getModifiers()));
        assertEquals(0, type.getDeclaredFields().length);
        assertEquals(0, type.getDeclaredClasses().length);
        assertEquals(1, type.getDeclaredMethods().length);
        assertTrue(Modifier.isPublic(method.getModifiers()));
        assertTrue(Modifier.isAbstract(method.getModifiers()));
        assertFalse(Modifier.isStatic(method.getModifiers()));
        assertSame(void.class, method.getReturnType());
    }

    @Test
    void productionDisconnectRevalidatesThreadAndCurrentActorThenUsesBoundedReason() {
        var source = read(NETWORK_SOURCE.resolve("P7ServerAccess.java"));
        var reason = "Rate limit exceeded";

        assertTrue(reason.length() <= P7NetworkBounds.MAX_WIRE_STRING_OR_RESOURCE_BYTES);
        assertTrue(source.contains("if (!server.isSameThread())"));
        assertTrue(source.contains(
                "currentConnectedPlayer(server, actor, actor.getUUID())"));
        assertTrue(source.contains(
                "actor.connection.disconnect(Component.literal(\"" + reason + "\"))"));
        assertFalse(source.contains("getMessage()"));
        assertFalse(source.contains("getStackTrace"));
        assertFalse(source.contains("Throwable"));
        assertFalse(source.contains("String.format"));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception failure) {
            throw new AssertionError("cannot read " + path, failure);
        }
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
