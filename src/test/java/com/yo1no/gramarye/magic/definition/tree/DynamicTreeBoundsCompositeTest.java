package com.yo1no.gramarye.magic.definition.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.List;
import org.junit.jupiter.api.Test;

class DynamicTreeBoundsCompositeTest {
    @Test
    void outerAndRawTreesShareOneNodeBudgetInInsertionOrder() {
        var outer = new JsonObject();
        outer.addProperty("fixed", true);
        var first = flatArray(3);
        var second = flatArray(3);

        assertEquals(
                DynamicTreeBounds.Result.WITHIN_LIMITS,
                check(outer, List.of(first, second), List.of(2, 4), 5, 10));
        assertEquals(
                DynamicTreeBounds.Result.NODE_COUNT_EXCEEDED,
                check(outer, List.of(first, second), List.of(2, 4), 5, 9));
    }

    @Test
    void rawRootUsesLogicalInsertionDepth() {
        var outer = new JsonObject();
        var relativeDepthThree = nestedJson(3);

        assertEquals(
                DynamicTreeBounds.Result.WITHIN_LIMITS,
                check(outer, List.of(relativeDepthThree), List.of(2), 4, 10));
        assertEquals(
                DynamicTreeBounds.Result.DEPTH_EXCEEDED,
                check(outer, List.of(relativeDepthThree), List.of(2), 3, 10));
    }

    @Test
    void compositeArgumentsAreProgrammingContracts() {
        var outer = new Dynamic<>(JsonOps.INSTANCE, new JsonObject());
        var raw = new Dynamic<>(JsonOps.INSTANCE, JsonNull.INSTANCE);

        assertThrows(IllegalArgumentException.class, () -> DynamicTreeBounds.checkComposite(
                outer, List.of(raw), List.of(), 4, 10));
        assertThrows(IllegalArgumentException.class, () -> DynamicTreeBounds.checkComposite(
                outer, List.of(raw), List.of(0), 4, 10));
    }

    private static DynamicTreeBounds.Result check(
            JsonObject outer,
            List<? extends com.google.gson.JsonElement> rawTrees,
            List<Integer> rawDepths,
            int maxDepth,
            long maxNodes) {
        return DynamicTreeBounds.checkComposite(
                new Dynamic<>(JsonOps.INSTANCE, outer),
                rawTrees.stream().map(value -> new Dynamic<>(JsonOps.INSTANCE, value)).toList(),
                rawDepths,
                maxDepth,
                maxNodes);
    }

    private static JsonArray flatArray(int elements) {
        var array = new JsonArray(elements);
        for (var index = 0; index < elements; index++) {
            array.add(index);
        }
        return array;
    }

    private static com.google.gson.JsonElement nestedJson(int depth) {
        com.google.gson.JsonElement value = JsonNull.INSTANCE;
        for (var index = 1; index < depth; index++) {
            var parent = new JsonObject();
            parent.add("next", value);
            value = parent;
        }
        return value;
    }
}
