package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class P11NativeCreditLifetimeTest {
    @Test void normalRemovalWithoutNativeConsumersReleasesImmediately() {
        assertTrue(new P11NativeCreditLifetime().removed());
    }

    @Test void nativeRemovalDoesNotEraseProofBeforeLaterSculkConsumer() {
        var facts = new P11NativeCreditLifetime();
        facts.enter();
        assertFalse(facts.removed());
        // Original dragon tickDeath still has a gameEvent after remove returns.
        facts.enter();
        assertFalse(facts.leave());
        assertTrue(facts.leave());
    }

    @Test void reentrantRemovalStillHasOneLastNativeConsumer() {
        var facts = new P11NativeCreditLifetime();
        facts.enter(); facts.enter();
        assertFalse(facts.removed());
        assertFalse(facts.removed());
        assertFalse(facts.leave());
        assertTrue(facts.leave());
    }

    @Test void exceptionFinallyAfterCompletedRemovalReleasesWithoutReplacingPrimary() {
        var facts = new P11NativeCreditLifetime();
        var primary = new IllegalStateException("native consumer tail");
        RuntimeException observed = assertThrows(RuntimeException.class, () -> {
            facts.enter();
            try { assertFalse(facts.removed()); throw primary; }
            finally { assertTrue(facts.leave()); }
        });
        assertSame(primary, observed);
    }

    @Test void throwingTeardownDoesNotProduceNormalRemovalFact() {
        var facts = new P11NativeCreditLifetime();
        facts.enter();
        // No removed() observation: the native teardown threw before its normal terminal.
        assertFalse(facts.leave());
    }

    @Test void noRemovalAndNoConsumerAreNotInventedTerminals() {
        var facts = new P11NativeCreditLifetime();
        facts.enter();
        assertFalse(facts.leave());
        assertThrows(IllegalStateException.class, facts::leave);
    }

    @Test void nativeSameBodyReviveRetainsExistingFieldsAfterDimensionFinally() {
        var facts = new P11NativeCreditLifetime();
        facts.enter();
        assertFalse(facts.removed());
        facts.revived();
        assertFalse(facts.leave());
        // A later real terminal, such as showEndCredits, still releases immediately.
        assertTrue(facts.removed());
    }

    @Test void dimensionFailureBeforeNativeReviveReleasesInFinallyAndPreservesPrimary() {
        var facts = new P11NativeCreditLifetime();
        var primary = new IllegalStateException("native pre-revive failure");
        var observed = assertThrows(RuntimeException.class, () -> {
            facts.enter();
            try { assertFalse(facts.removed()); throw primary; }
            finally { assertTrue(facts.leave()); }
        });
        assertSame(primary, observed);
    }

    @Test void dimensionFailureAfterNativeReviveDoesNotInventAnotherRemoval() {
        var facts = new P11NativeCreditLifetime();
        var primary = new IllegalStateException("native post-revive failure");
        var observed = assertThrows(RuntimeException.class, () -> {
            facts.enter();
            try { assertFalse(facts.removed()); facts.revived(); throw primary; }
            finally { assertFalse(facts.leave()); }
        });
        assertSame(primary, observed);
        assertTrue(facts.removed());
    }

    @Test void reentrantSameBodyTransitionsPreserveOnlyTheLatestNormalRemoval() {
        var facts = new P11NativeCreditLifetime();
        facts.enter();
        assertFalse(facts.removed());
        facts.revived();
        facts.enter();
        assertFalse(facts.removed());
        facts.revived();
        assertFalse(facts.leave());
        assertFalse(facts.removed());
        assertTrue(facts.leave());
    }

    @Test void nativeReviveInsideNestedConsumerDoesNotReleaseOuterFieldCustody() {
        var facts = new P11NativeCreditLifetime();
        facts.enter(); facts.enter();
        assertFalse(facts.removed());
        facts.revived();
        assertFalse(facts.leave());
        assertFalse(facts.leave());
    }

    @Test void arbitraryReviveCannotRecreateAnAlreadyReleasedOrUnobservedCredit() {
        var facts = new P11NativeCreditLifetime();
        assertThrows(IllegalStateException.class, facts::revived);
        assertTrue(facts.removed());
        assertThrows(IllegalStateException.class, facts::revived);
        facts.enter();
        assertThrows(IllegalStateException.class, facts::revived);
        assertFalse(facts.leave());
    }

    @Test void onlyTheExactNativeSameBodyReviveNotifiesTheUnexpandedLifetime() throws Exception {
        var notification = P11NativeOperationBoundary.CreditAccess.class.getDeclaredMethod("p11$nativeCreditRevived");
        assertEquals(void.class, notification.getReturnType());
        assertTrue(Modifier.isPublic(notification.getModifiers()));
        var bridge = P11NativeOperationBoundary.class.getDeclaredMethod("creditRevived",
                P11NativeOperationBoundary.CreditLifetime.class);
        assertEquals(void.class, bridge.getReturnType());
        assertTrue(Modifier.isPublic(bridge.getModifiers()) && Modifier.isStatic(bridge.getModifiers()));
        var fields = P11NativeCreditLifetime.class.getDeclaredFields();
        assertEquals(2, fields.length);
        assertEquals(int.class, P11NativeCreditLifetime.class.getDeclaredField("consumers").getType());
        assertEquals(boolean.class, P11NativeCreditLifetime.class.getDeclaredField("removalPending").getType());
        assertTrue(Arrays.stream(fields).allMatch(field -> Modifier.isPrivate(field.getModifiers())));
        var wrapper = P11NativeOperationBoundary.CreditLifetime.class.getDeclaredFields();
        assertEquals(1, wrapper.length);
        assertEquals(P11NativeCreditLifetime.class, wrapper[0].getType());
        assertTrue(Modifier.isPrivate(wrapper[0].getModifiers()) && Modifier.isFinal(wrapper[0].getModifiers()));

        var source = source("src/main/java/com/yo1no/gramarye/mixin/P11ServerPlayerScoreMixin.java");
        assertTrue(exactSameBodyRevive(source));
        assertFalse(exactSameBodyRevive(source.replace("ServerPlayer;revive()V", "ServerPlayer;unsetRemoved()V")));
        assertFalse(exactSameBodyRevive(source.replace("original.call(actor);",
                "((P11NativeOperationBoundary.CreditAccess) actor).p11$nativeCreditRevived(); original.call(actor);")));
    }

    private static boolean exactSameBodyRevive(String source) {
        var compact = source.replaceAll("\\s+", "");
        String descriptor = "changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;";
        String anchor = "Lnet/minecraft/server/level/ServerPlayer;revive()V";
        if (compact.split(java.util.regex.Pattern.quote(descriptor), -1).length != 3
                || compact.split(java.util.regex.Pattern.quote(anchor), -1).length != 2
                || !compact.contains("holder.p11$beginNativeConsumers();try{returnoriginal.call(transition);}finally{holder.p11$endNativeConsumers();}")) {
            return false;
        }
        int start = compact.indexOf("privatevoidp11$actualSameBodyRevive(");
        if (start < 0) { return false; }
        var body = compact.substring(start, compact.indexOf('}', start));
        int original = body.indexOf("original.call(actor);");
        int notification = body.indexOf("((P11NativeOperationBoundary.CreditAccess)actor).p11$nativeCreditRevived();");
        return original >= 0 && notification > original;
    }

    private static String source(String relative) throws java.io.IOException {
        for (var root = Path.of("").toAbsolutePath(); root != null; root = root.getParent()) {
            var candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) { return Files.readString(candidate); }
        }
        throw new IllegalStateException("repository source was not found: " + relative);
    }
}
