package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Pure identity algorithm support; these equal-id values are not native lifecycle evidence. */
final class P11ExactCleanupTest {
    @Test void equalIdentifierCannotRemoveSuccessor() {
        var old = new EqualId(17);
        var successor = new EqualId(17);
        var roster = new ArrayList<>(List.of(successor));
        assertEquals(old, successor);
        assertFalse(P11NativeCleanup.removeExact(roster, old));
        assertSame(successor, roster.getFirst());
        assertTrue(P11NativeCleanup.removeExact(roster, successor));
        assertTrue(roster.isEmpty());
    }

    @Test void exactRemovalPreservesEqualAndUnrelatedValuesAndOrder() {
        var old = new EqualId(17);
        var successor = new EqualId(17);
        var other = new EqualId(23);
        var roster = new ArrayList<>(List.of(successor, old, other));
        assertTrue(P11NativeCleanup.removeExact(roster, old));
        assertEquals(2, roster.size());
        assertSame(successor, roster.get(0));
        assertSame(other, roster.get(1));
        assertFalse(P11NativeCleanup.removeExact(roster, old));
    }

    @Test void changedSectionCallbackKeyOrLevelCannotAuthorizeTheCapturedTail() {
        var callback = new Object();
        var section = new EqualId(7);
        var level = new Object();
        assertTrue(P11NativeCleanup.sameRemovalFrame(callback, callback, section, section, 12, 12, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, callback, section, new EqualId(7), 12, 12, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, new Object(), section, section, 12, 12, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, callback, section, section, 12, 13, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, callback, section, section, 12, 12, level, new Object()));
        assertFalse(P11NativeCleanup.sameRemovalFrame(null, null, section, section, 12, 12, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, callback, null, null, 12, 12, level, level));
        assertFalse(P11NativeCleanup.sameRemovalFrame(callback, callback, section, section, 12, 12, null, null));
    }

    @Test void removalFrameRetainsOnlyItsOpaqueCallLocalWitnesses() throws Exception {
        var scope = P11NativeCleanup.Scope.class;
        assertEquals(Set.of("actor", "owner", "body", "source", "manager", "callback", "level",
                        "section", "sectionKey", "reason", "previous", "leaveEntered", "leaveThrew",
                        "repairAttempted", "closed"),
                Arrays.stream(scope.getDeclaredFields()).map(java.lang.reflect.Field::getName)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(Arrays.stream(scope.getDeclaredFields()).allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        assertTrue(Arrays.stream(scope.getDeclaredConstructors()).allMatch(constructor -> Modifier.isPrivate(constructor.getModifiers())));
        assertEquals(0, scope.getDeclaredMethods().length);
        assertEquals(P11NativeCleanup.CallbackState.class, scope.getDeclaredField("callback").getType());
        assertEquals(net.minecraft.world.level.Level.class, scope.getDeclaredField("level").getType());
        var current = P11NativeCleanup.class.getDeclaredField("CURRENT");
        assertEquals(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL, current.getModifiers());
        assertEquals(ThreadLocal.class, current.getType());
        assertEquals(scope, ((java.lang.reflect.ParameterizedType) current.getGenericType()).getActualTypeArguments()[0]);
        var frame = P11NativeCleanup.class.getDeclaredMethod("unchangedRemovalFrame", scope,
                P11NativeCleanup.CallbackState.class, net.minecraft.world.level.entity.EntitySection.class, long.class);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC, frame.getModifiers());
        assertEquals(boolean.class, frame.getReturnType());
        var remove = P11NativeCleanup.class.getDeclaredMethod("remove",
                net.minecraft.world.level.entity.EntityAccess.class,
                net.minecraft.world.entity.Entity.RemovalReason.class, P11NativeCleanup.Manager.class,
                P11NativeCleanup.CallbackState.class, net.minecraft.world.level.entity.EntitySection.class,
                long.class, com.llamalad7.mixinextras.injector.wrapoperation.Operation.class);
        assertEquals(Modifier.PUBLIC | Modifier.STATIC, remove.getModifiers());
        assertEquals(void.class, remove.getReturnType());
        assertEquals(1, P11NativeCleanup.CallbackState.class.getDeclaredMethods().length);
        assertEquals(boolean.class, P11NativeCleanup.CallbackState.class
                .getDeclaredMethod("p11$unchangedRemovalFrame", scope).getReturnType());
        assertEquals(1, P11NativeCleanup.EntityCallbackState.class.getDeclaredMethods().length);
        assertEquals(boolean.class, P11NativeCleanup.EntityCallbackState.class
                .getDeclaredMethod("p11$hasRemovalCallback", net.minecraft.world.level.entity.EntityInLevelCallback.class)
                .getReturnType());
        assertFalse(P11NativeCleanup.unchangedRemovalFrame(null, null, null, 0));
    }

    @Test void exactLiveFrameMustBeCheckedBeforeAnyStructuralRepair() throws Exception {
        String cleanup = read("P11NativeCleanup.java").replaceAll("\\s+", "");
        String finish = cleanup.substring(cleanup.indexOf("publicstatic<TextendsEntityAccess>booleanfinishLeaveTail("),
                cleanup.indexOf("/**TheoriginalCallback"));
        assertFrameGuard(finish);
        assertThrows(AssertionError.class, () -> assertFrameGuard(
                finish.replace("||!scope.callback.p11$unchangedRemovalFrame(scope)", "")));
        String current = cleanup.substring(cleanup.indexOf("publicstaticbooleanunchangedRemovalFrame("),
                cleanup.indexOf("staticbooleansameRemovalFrame("));
        assertEquals("publicstaticbooleanunchangedRemovalFrame(Scopescope,CallbackStatecallback,"
                + "EntitySection<?>section,longsectionKey){returnscope!=null&&CURRENT.get()==scope&&!scope.closed"
                + "&&sameRemovalFrame(scope.callback,callback,scope.section,section,scope.sectionKey,sectionKey,"
                + "scope.level,scope.actor.level())"
                + "&&((EntityCallbackState)scope.actor).p11$hasRemovalCallback((EntityInLevelCallback)callback);}", current);
        String callback = read("mixin/P11EntityRemovalMixin.java").replaceAll("\\s+", "");
        assertTrue(callback.contains("implementsP11NativeCleanup.CallbackState"));
        assertTrue(callback.contains("returnP11NativeCleanup.unchangedRemovalFrame(scope,this,currentSection,currentSectionKey);"));
        assertTrue(callback.contains("P11NativeCleanup.remove(entity,reason,(P11NativeCleanup.Manager)this$0,this,currentSection,currentSectionKey,original);"));
        String entity = read("mixin/P11EntityPresenceMixin.java").replaceAll("\\s+", "");
        assertTrue(entity.contains("implementsP11NativeCleanup.EntityCallbackState"));
        assertTrue(entity.contains("@ShadowprivateEntityInLevelCallbacklevelCallback;"));
        assertTrue(entity.contains("publicbooleanp11$hasRemovalCallback(EntityInLevelCallbackexpected){returnlevelCallback==expected;}"));
    }

    private static void assertFrameGuard(String finish) {
        String guard = "||!scope.callback.p11$unchangedRemovalFrame(scope)){returnfalse;}scope.repairAttempted=true;";
        assertTrue(finish.contains(guard));
        assertTrue(finish.indexOf(guard) < finish.indexOf("lookup.remove(exact);"));
    }

    private static String read(String relative) throws Exception {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("build.gradle"))) { current = current.getParent(); }
        if (current == null) { throw new IllegalStateException("project root not found"); }
        return Files.readString(current.resolve("src/main/java/com/yo1no/gramarye").resolve(relative));
    }

    private record EqualId(int id) {}
}
