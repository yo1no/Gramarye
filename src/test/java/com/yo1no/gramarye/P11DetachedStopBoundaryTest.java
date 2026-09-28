package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.junit.jupiter.api.Test;

/** Direct wiring/authority checks; real retained JSON physical writers are native cohort evidence. */
final class P11DetachedStopBoundaryTest {
    @Test void noStopCallerCannotUseBridgeAsAnIndependentSaveGrant() {
        long before = P11NativeStorageBoundary.observerFailureCount();
        assertDoesNotThrow(() -> P11NativeStorageBoundary.flushDetachedIndependentAtStop(null, null));
        assertEquals(before, P11NativeStorageBoundary.observerFailureCount());
    }

    @Test void exactUniqueAnchorPrecedesOriginalWorldUnlock() throws Exception {
        String mixin = read("mixin/P11MinecraftServerMixin.java");
        assertTrue(mixin.contains("target = \"Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;close()V\""));
        assertTrue(mixin.contains("require = 1, expect = 1, allow = 1"));
        assertFalse(mixin.contains("@At(\"RETURN\")"));
        String boundary = read("P11NativeStorageBoundary.java");
        String bridge = boundary.substring(boundary.indexOf("public static void flushDetachedIndependentAtStop"),
                boundary.indexOf("private enum ReadState"));
        assertStopAuthority(bridge);
        assertThrows(AssertionError.class, () -> assertStopAuthority(
                bridge.replace("STOP_SERVER.get() != server", "false")));
        assertThrows(AssertionError.class, () -> assertStopAuthority(
                bridge.replace("root.writerOwner(storage, null) == source", "true")));
    }

    @Test void onlyDetachedDirtyCanonicalOwnersUseOriginalSoleWritersIndependently() throws Exception {
        String source = read("P11QualifiedSourceOwner.java");
        String flush = source.substring(source.indexOf("void flushDetachedIndependentAtStop()"),
                source.indexOf("Summary retire(boolean"));
        assertTrue(flush.contains("if (detachedStopFlushed) { return; }"));
        assertTrue(flush.contains("server.getPlayerList().getPlayer(body.actor.getUUID()) != null"));
        assertTrue(flush.contains("flushDetachedIndependent(body, P11ReceiptLedger.WriterKind.STATISTICS)"));
        assertTrue(flush.contains("flushDetachedIndependent(body, P11ReceiptLedger.WriterKind.ADVANCEMENTS)"));
        assertTrue(flush.contains("independentEligible(body, kind)"));
        assertTrue(flush.contains("filter(P11ReceiptLedger.PhysicalFacts::dirty).isEmpty()"));
        assertTrue(flush.contains("body.stats.save()") && flush.contains("body.advancements.save()"));
        assertTrue(flush.contains("catch (RuntimeException | Error failure)"));
        assertFalse(flush.contains("saveWithoutId") || flush.contains("canSerialize") || flush.contains("load("));
        assertFalse(flush.contains("Files.write") || flush.contains("finishSave") || flush.contains("releaseDirty"));
    }

    @Test void stopBridgeRetainsOnlyTheExactNestedNativeCallScope() throws Exception {
        var bridge = P11NativeStorageBoundary.class.getDeclaredMethod("flushDetachedIndependentAtStop",
                MinecraftServer.class, LevelStorageSource.LevelStorageAccess.class);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC, bridge.getModifiers());
        assertEquals(void.class, bridge.getReturnType());
        var scope = P11NativeStorageBoundary.class.getDeclaredField("STOP_SERVER");
        assertEquals(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL, scope.getModifiers());
        assertEquals(ThreadLocal.class, scope.getType());
        assertArrayEquals(new java.lang.reflect.Type[] {MinecraftServer.class},
                ((ParameterizedType) scope.getGenericType()).getActualTypeArguments());
        var once = P11QualifiedSourceOwner.class.getDeclaredField("detachedStopFlushed");
        assertEquals(Modifier.PRIVATE, once.getModifiers());
        assertEquals(boolean.class, once.getType());
        var ownerEntry = P11QualifiedSourceOwner.class.getDeclaredMethod("flushDetachedIndependentAtStop");
        assertEquals(0, ownerEntry.getModifiers());
        assertEquals(void.class, ownerEntry.getReturnType());
        String boundary = read("P11NativeStorageBoundary.java");
        String stop = boundary.substring(boundary.indexOf("public static void stop("),
                boundary.indexOf("/** Exact original stop caller"));
        assertEquals("publicstaticvoidstop(MinecraftServerserver,Operation<Void>original){"
                + "varprevious=STOP_SERVER.get();STOP_SERVER.set(server);booleannormal=false;"
                + "try{original.call();normal=true;}finally{"
                + "try{if(root!=null){root.nativeStopTerminal(server,normal);}}"
                + "finally{if(previous==null){STOP_SERVER.remove();}else{STOP_SERVER.set(previous);}}}}",
                stop.replaceAll("\\s+", ""));
    }

    private static String read(String relative) throws Exception {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle"))) { current = current.getParent(); }
        if (current == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(current.resolve("src/main/java/com/yo1no/gramarye").resolve(relative));
    }

    private static void assertStopAuthority(String bridge) {
        assertTrue(bridge.contains("STOP_SERVER.get() != server"));
        assertTrue(bridge.contains("root.writerOwner(storage, null) == source"));
    }
}
