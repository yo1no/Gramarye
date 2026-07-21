package com.yo1no.gramarye.magic.definition.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
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
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("maxNodes must be positive");
        }

        var value = dynamic.getValue();
        if (value instanceof JsonElement json) {
            return checkJson(json, 1, maxDepth, new NodeBudget(maxNodes));
        }
        if (value instanceof Tag tag) {
            return checkNbt(tag, 1, maxDepth, new NodeBudget(maxNodes));
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
