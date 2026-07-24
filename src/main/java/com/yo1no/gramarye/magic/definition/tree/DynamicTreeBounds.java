package com.yo1no.gramarye.magic.definition.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Shared short-circuiting bounds traversal for supported serialized tree families. */
public final class DynamicTreeBounds {
    private DynamicTreeBounds() {
    }

    /**
     * Counts a root, every object/compound value and every array/list element as one node. Map
     * keys are not nodes, but are independently constrained by the technical string ceiling.
     */
    public static Result check(Dynamic<?> dynamic, int maxDepth, long maxNodes) {
        Objects.requireNonNull(dynamic, "dynamic");
        requirePositiveLimits(maxDepth, maxNodes);

        return check(dynamic, 1, maxDepth, new NodeBudget(maxNodes));
    }

    /**
     * Checks one logical outer tree and separately represented raw subtrees against one shared
     * node budget. Each raw-tree root is counted at the corresponding logical insertion depth.
     * The caller must omit those raw roots from {@code logicalOuterTree}; map keys are not nodes.
     */
    public static Result checkComposite(
            Dynamic<?> logicalOuterTree,
            List<? extends Dynamic<?>> rawTrees,
            List<Integer> rawRootDepths,
            int maxDepth,
            long maxNodes) {
        Objects.requireNonNull(logicalOuterTree, "logicalOuterTree");
        rawTrees = List.copyOf(Objects.requireNonNull(rawTrees, "rawTrees"));
        rawRootDepths = List.copyOf(Objects.requireNonNull(rawRootDepths, "rawRootDepths"));
        requirePositiveLimits(maxDepth, maxNodes);
        if (rawTrees.size() != rawRootDepths.size()) {
            throw new IllegalArgumentException("rawTrees and rawRootDepths must have equal size");
        }

        var budget = new NodeBudget(maxNodes);
        var result = check(logicalOuterTree, 1, maxDepth, budget);
        if (result != Result.WITHIN_LIMITS) {
            return result;
        }
        for (var index = 0; index < rawTrees.size(); index++) {
            var rawTree = Objects.requireNonNull(rawTrees.get(index), "rawTrees[" + index + "]");
            var rootDepth = Objects.requireNonNull(
                    rawRootDepths.get(index), "rawRootDepths[" + index + "]");
            if (rootDepth <= 0) {
                throw new IllegalArgumentException("raw root depth must be positive");
            }
            result = check(rawTree, rootDepth, maxDepth, budget);
            if (result != Result.WITHIN_LIMITS) {
                return result;
            }
        }
        return Result.WITHIN_LIMITS;
    }

    private static void requirePositiveLimits(int maxDepth, long maxNodes) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be positive");
        }
    }

    private static Result check(
            Dynamic<?> dynamic,
            int rootDepth,
            int maxDepth,
            NodeBudget budget) {
        var value = dynamic.getValue();
        if (value instanceof JsonElement json) {
            return checkJson(json, rootDepth, maxDepth, budget);
        }
        if (value instanceof Tag tag) {
            return checkNbt(tag, rootDepth, maxDepth, budget);
        }
        return Result.UNSUPPORTED;
    }

    private static Result checkJson(JsonElement value, int depth, int maxDepth, NodeBudget budget) {
        var status = budget.visit(depth, maxDepth);
        if (status != Result.WITHIN_LIMITS) {
            return status;
        }
        if (value instanceof JsonObject object) {
            for (var entry : object.entrySet()) {
                if (entry.getKey().length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                    return Result.KEY_LENGTH_EXCEEDED;
                }
                status = checkJson(entry.getValue(), depth + 1, maxDepth, budget);
                if (status != Result.WITHIN_LIMITS) {
                    return status;
                }
            }
        } else if (value instanceof JsonArray array) {
            for (var element : array) {
                status = checkJson(element, depth + 1, maxDepth, budget);
                if (status != Result.WITHIN_LIMITS) {
                    return status;
                }
            }
        }
        return Result.WITHIN_LIMITS;
    }

    private static Result checkNbt(Tag value, int depth, int maxDepth, NodeBudget budget) {
        var status = budget.visit(depth, maxDepth);
        if (status != Result.WITHIN_LIMITS) {
            return status;
        }
        if (value instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                if (key.length() > MagicSafetyCeilings.MAX_STRING_LENGTH) {
                    return Result.KEY_LENGTH_EXCEEDED;
                }
                var child = compound.get(key);
                if (child != null) {
                    status = checkNbt(child, depth + 1, maxDepth, budget);
                    if (status != Result.WITHIN_LIMITS) {
                        return status;
                    }
                }
            }
        } else if (value instanceof CollectionTag<?> collection) {
            for (var child : collection) {
                status = checkNbt(child, depth + 1, maxDepth, budget);
                if (status != Result.WITHIN_LIMITS) {
                    return status;
                }
            }
        }
        return Result.WITHIN_LIMITS;
    }

    public enum Result {
        WITHIN_LIMITS,
        DEPTH_EXCEEDED,
        NODE_COUNT_EXCEEDED,
        KEY_LENGTH_EXCEEDED,
        UNSUPPORTED
    }

    private static final class NodeBudget {
        private final long maximum;
        private long visited;

        private NodeBudget(long maximum) {
            this.maximum = maximum;
        }

        private Result visit(int depth, int maxDepth) {
            if (depth > maxDepth) {
                return Result.DEPTH_EXCEEDED;
            }
            visited++;
            return visited > maximum ? Result.NODE_COUNT_EXCEEDED : Result.WITHIN_LIMITS;
        }
    }
}
