package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

/** Real native codecs/requirements plus delivery facts; not a claim of native hook or client IO coverage. */
final class P11CanonicalAdvancementsTest {
    @Test
    void nativeDataCopyDoesNotAliasProgressOrCriterionAndSurvivesLaterMutation() {
        var id = ResourceLocation.fromNamespaceAndPath("gramarye", "copy");
        var original = progress("first", "second");
        assertTrue(original.grantProgress("first"));
        var map = new LinkedHashMap<ResourceLocation, AdvancementProgress>();
        map.put(id, original);
        // This is the native Data.CODEC's unbounded map/AdvancementProgress codec pair.
        var copied = P11CanonicalAdvancements.copy(
                Codec.unboundedMap(ResourceLocation.CODEC, AdvancementProgress.CODEC), map);
        var frozen = copied.get(id);
        assertNotSame(map, copied);
        assertNotSame(original, frozen);
        assertNotSame(original.getCriterion("first"), frozen.getCriterion("first"));

        assertTrue(original.revokeProgress("first"));
        assertTrue(original.grantProgress("second"));
        map.clear();
        frozen.update(AdvancementRequirements.allOf(List.of("first", "second")));
        assertTrue(frozen.getCriterion("first").isDone());
        assertFalse(frozen.getCriterion("second").isDone());
        assertFalse(frozen.isDone());
    }

    @Test
    void nativeDoneIsRecomputedUsingNewRequirementsRatherThanCopiedAsAuthority() {
        var original = progress("first");
        original.grantProgress("first");
        assertTrue(original.isDone());
        var copied = P11CanonicalAdvancements.copy(AdvancementProgress.CODEC, original);
        // Native decoder deliberately has no requirements until startProgress/update.
        assertFalse(copied.isDone());
        copied.update(AdvancementRequirements.allOf(List.of("first", "new")));
        assertTrue(copied.getCriterion("first").isDone());
        assertFalse(copied.getCriterion("new").isDone());
        assertFalse(copied.isDone());
        copied.update(AdvancementRequirements.allOf(List.of("first")));
        assertNull(copied.getCriterion("new"));
        assertTrue(copied.isDone());
    }

    @Test
    void nativeCriterionRemovalDoesNotPreserveDeletedCompletion() {
        var original = progress("removed", "kept");
        original.grantProgress("removed");
        var copied = P11CanonicalAdvancements.copy(AdvancementProgress.CODEC, original);
        copied.update(AdvancementRequirements.allOf(List.of("kept")));
        assertNull(copied.getCriterion("removed"));
        assertFalse(copied.hasProgress());
        assertFalse(copied.isDone());
    }

    @Test
    void copyFailurePropagatesWithoutReplacingInput() {
        var input = progress("kept");
        input.grantProgress("kept");
        Codec<AdvancementProgress> failure = AdvancementProgress.CODEC.flatXmap(
                value -> DataResult.error(() -> "injected decode failure"), DataResult::success);
        assertThrows(IllegalStateException.class, () -> P11CanonicalAdvancements.copy(failure, input));
        assertTrue(input.getCriterion("kept").isDone());
        assertTrue(input.isDone());
    }

    @Test
    void oldRecipientCompletionCannotConsumeNewInitialSync() {
        var state = new P11CanonicalAdvancements.Delivery(true);
        long old = state.generation();
        state.requireInitial();
        assertFalse(state.submitted(old));
        assertTrue(state.pending());
        assertTrue(state.submitted(state.generation()));
        assertFalse(state.pending());
    }

    @Test
    void failedDeliveryAndNewMutationKeepOneCurrentObligation() {
        var state = new P11CanonicalAdvancements.Delivery(false);
        state.requireInitial();
        long first = state.generation();
        state.requireInitial();
        assertEquals(first + 1, state.generation());
        assertFalse(state.submitted(first));
        assertTrue(state.pending());
        assertTrue(state.current(state.generation()));
    }

    @Test
    void oldHolderWithSameIdCannotPublishIntoTheNewNativeTree() {
        var id = ResourceLocation.fromNamespaceAndPath("gramarye", "generation");
        var oldHolder = Advancement.Builder.advancement().build(id);
        var currentHolder = Advancement.Builder.advancement().build(id);
        var tree = new AdvancementTree();
        tree.addAll(List.of(currentHolder));
        assertFalse(P11CanonicalAdvancements.currentHolder(tree, oldHolder, 2, 2));
        assertTrue(P11CanonicalAdvancements.currentHolder(tree, currentHolder, 2, 2));
        // Reusing the same manager/holder object in a nested reload still invalidates
        // the outer callback generation, even when identity happens to match.
        assertFalse(P11CanonicalAdvancements.currentHolder(tree, currentHolder, 1, 2));
        tree.clear();
        assertFalse(P11CanonicalAdvancements.currentHolder(tree, currentHolder, 2, 2));
    }

    @Test
    void obsoleteSameIdReadKeepsNativeValuesButCannotMutateCurrentProgress() {
        var current = progress("first", "second");
        current.grantProgress("first");
        var detached = P11CanonicalAdvancements.detachedProgress(current,
                AdvancementRequirements.allOf(List.of("first", "second")));
        assertNotSame(current, detached);
        assertTrue(detached.getCriterion("first").isDone());
        assertFalse(detached.isDone());
        assertTrue(detached.grantProgress("second"));
        assertTrue(detached.isDone(), "captured callback retains its native completion/reward decision");
        assertFalse(current.getCriterion("second").isDone());
        assertFalse(current.isDone(), "old callback must not publish into the new same-ID object");
        detached.revokeProgress("first");
        assertTrue(current.getCriterion("first").isDone());
    }

    @Test
    void capturedBatchGenerationOutlivesOneCallbackAndNestedScopesRestoreExactOwner() {
        var first = new Object();
        var other = new Object();
        var outer = P11CanonicalAdvancements.beginCallbacks(first, 7);
        try {
            assertEquals(7, P11CanonicalAdvancements.callbackGeneration(first, 8));
            var nestedOther = P11CanonicalAdvancements.beginCallbacks(other, 11);
            try {
                assertEquals(7, P11CanonicalAdvancements.callbackGeneration(first, 8));
                assertEquals(11, P11CanonicalAdvancements.callbackGeneration(other, 12));
                var nestedReload = P11CanonicalAdvancements.beginCallbacks(first, 8);
                try { assertEquals(8, P11CanonicalAdvancements.callbackGeneration(first, 9)); }
                finally { P11CanonicalAdvancements.endCallbacks(nestedReload); }
                assertEquals(7, P11CanonicalAdvancements.callbackGeneration(first, 8));
            } finally { P11CanonicalAdvancements.endCallbacks(nestedOther); }
            assertEquals(7, P11CanonicalAdvancements.callbackGeneration(first, 8));
            assertEquals(12, P11CanonicalAdvancements.callbackGeneration(other, 12));
        } finally { P11CanonicalAdvancements.endCallbacks(outer); }
        assertEquals(8, P11CanonicalAdvancements.callbackGeneration(first, 8));
    }

    @Test
    void onlySelectedListenerFirstAwardUsesCapturedGenerationAndNestedCurrentAwardsStayCurrent() {
        var canonical = new Object();
        var batch = P11CanonicalAdvancements.beginCallbacks(canonical, 7);
        try {
            var first = P11CanonicalAdvancements.beginListener(canonical, 8);
            try {
                assertEquals(7, P11CanonicalAdvancements.listenerGeneration(canonical, 8));
                assertEquals(8, P11CanonicalAdvancements.listenerGeneration(canonical, 8),
                        "legitimate new-tree callback mutation must not inherit an old batch");
                var nestedBatch = P11CanonicalAdvancements.beginCallbacks(canonical, 8);
                try {
                    var nestedListener = P11CanonicalAdvancements.beginListener(canonical, 9);
                    try { assertEquals(8, P11CanonicalAdvancements.listenerGeneration(canonical, 9)); }
                    finally { P11CanonicalAdvancements.endListener(nestedListener); }
                } finally { P11CanonicalAdvancements.endCallbacks(nestedBatch); }
                assertEquals(9, P11CanonicalAdvancements.listenerGeneration(canonical, 9));
            } finally { P11CanonicalAdvancements.endListener(first); }
            var second = P11CanonicalAdvancements.beginListener(canonical, 9);
            try { assertEquals(7, P11CanonicalAdvancements.listenerGeneration(canonical, 9)); }
            finally { P11CanonicalAdvancements.endListener(second); }
        } finally { P11CanonicalAdvancements.endCallbacks(batch); }
        assertEquals(9, P11CanonicalAdvancements.listenerGeneration(canonical, 9));
    }

    @Test
    void constructorNullAdvancementsIsUnchangedAndObserverMismatchCannotMaskPrimary() {
        long failures = P11CanonicalAdvancements.scopeFailures();
        assertNull(P11CanonicalAdvancements.beginCallbacks(null));
        assertNull(P11CanonicalAdvancements.beginListener(null));
        P11CanonicalAdvancements.endCallbacks(null);
        P11CanonicalAdvancements.endListener(null);
        assertEquals(failures, P11CanonicalAdvancements.scopeFailures());

        var canonical = new Object();
        var primary = new AssertionError("native callback");
        var outer = P11CanonicalAdvancements.beginCallbacks(canonical, 1);
        var inner = P11CanonicalAdvancements.beginCallbacks(canonical, 2);
        try {
            var observed = assertThrows(AssertionError.class, () -> {
                try { throw primary; }
                finally { P11CanonicalAdvancements.endCallbacks(outer); }
            });
            assertTrue(observed == primary);
            assertEquals(2, P11CanonicalAdvancements.callbackGeneration(canonical, 3));
            assertEquals(failures + 1, P11CanonicalAdvancements.scopeFailures());
        } finally {
            P11CanonicalAdvancements.endCallbacks(inner);
            P11CanonicalAdvancements.endCallbacks(outer);
        }
        assertEquals(3, P11CanonicalAdvancements.callbackGeneration(canonical, 3));
    }

    @Test
    void callbackScopeApiRetainsOnlyExactCallStackScalarsAndExposesNoMutableAuthority() throws ReflectiveOperationException {
        var callbacks = P11CanonicalAdvancements.CapturedCallbacks.class;
        var listener = P11CanonicalAdvancements.CapturedListener.class;
        assertScopeFields(callbacks, Map.of("canonical", Object.class, "generation", long.class, "previous", callbacks));
        assertScopeFields(listener, Map.of("canonical", Object.class, "generation", long.class,
                "previous", listener, "claimed", boolean.class));
        for (var entry : Map.of("CALLBACKS", callbacks, "LISTENERS", listener).entrySet()) {
            var field = P11CanonicalAdvancements.class.getDeclaredField(entry.getKey());
            assertEquals(Modifier.PRIVATE | Modifier.STATIC | Modifier.FINAL, field.getModifiers());
            assertEquals(ThreadLocal.class, field.getType());
            assertEquals(entry.getValue(), ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0]);
        }
        var expected = Set.of("beginCallbacks", "endCallbacks", "beginListener", "endListener", "listenerGeneration");
        var publicScopes = Arrays.stream(P11CanonicalAdvancements.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && expected.contains(method.getName()))
                .toList();
        assertEquals(5, publicScopes.size());
        for (var method : publicScopes) { assertTrue(Modifier.isStatic(method.getModifiers())); }
        assertEquals(callbacks, P11CanonicalAdvancements.class.getMethod("beginCallbacks", PlayerAdvancements.class).getReturnType());
        assertEquals(void.class, P11CanonicalAdvancements.class.getMethod("endCallbacks", callbacks).getReturnType());
        assertEquals(listener, P11CanonicalAdvancements.class.getMethod("beginListener", PlayerAdvancements.class).getReturnType());
        assertEquals(void.class, P11CanonicalAdvancements.class.getMethod("endListener", listener).getReturnType());
        assertEquals(long.class, P11CanonicalAdvancements.class
                .getMethod("listenerGeneration", PlayerAdvancements.class, long.class).getReturnType());
        assertEquals(Set.of("p11$associatedPlayer", "p11$treeGeneration"), Arrays.stream(
                P11CanonicalAdvancements.Access.class.getDeclaredMethods()).map(method -> method.getName()).collect(Collectors.toSet()));
        assertEquals(2, P11CanonicalAdvancements.Access.class.getDeclaredMethods().length);
        assertEquals(ServerPlayer.class, P11CanonicalAdvancements.Access.class.getMethod("p11$associatedPlayer").getReturnType());
        assertEquals(long.class, P11CanonicalAdvancements.Access.class.getMethod("p11$treeGeneration").getReturnType());
    }

    private static void assertScopeFields(Class<?> scope, Map<String, Class<?>> expected) {
        assertTrue(Modifier.isFinal(scope.getModifiers()));
        assertEquals(1, scope.getDeclaredConstructors().length);
        assertTrue(Modifier.isPrivate(scope.getDeclaredConstructors()[0].getModifiers()));
        assertEquals(0, scope.getDeclaredMethods().length);
        assertEquals(expected, Arrays.stream(scope.getDeclaredFields())
                .collect(Collectors.toMap(field -> field.getName(), field -> field.getType())));
        for (var field : scope.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers()));
            assertFalse(Modifier.isStatic(field.getModifiers()));
            assertEquals(!field.getName().equals("claimed"), Modifier.isFinal(field.getModifiers()));
        }
    }

    private static AdvancementProgress progress(String... criteria) {
        var progress = new AdvancementProgress();
        progress.update(AdvancementRequirements.allOf(List.of(criteria)));
        return progress;
    }
}
