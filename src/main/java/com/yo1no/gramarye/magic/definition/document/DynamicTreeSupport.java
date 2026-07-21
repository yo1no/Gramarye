package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

final class DynamicTreeSupport {
    private DynamicTreeSupport() {
    }

    static DataResult<SerializedTreeFamily> family(Dynamic<?> dynamic) {
        if (dynamic.getValue() instanceof JsonElement) {
            return DataResult.success(SerializedTreeFamily.JSON);
        }
        if (dynamic.getValue() instanceof Tag) {
            return DataResult.success(SerializedTreeFamily.NBT);
        }
        return DataResult.error(() -> boundedDiagnostic("Unsupported Dynamic value family: "
                + dynamic.getValue().getClass().getName()));
    }

    static DataResult<SerializedTreeFamily> family(DynamicOps<?> ops) {
        var empty = ops.empty();
        if (empty instanceof JsonElement) {
            return DataResult.success(SerializedTreeFamily.JSON);
        }
        if (empty instanceof Tag) {
            return DataResult.success(SerializedTreeFamily.NBT);
        }
        return DataResult.error(() -> boundedDiagnostic(
                "Unsupported DynamicOps family: " + ops.getClass().getName()));
    }

    static BoundsResult checkBounds(Dynamic<?> dynamic, int maxDepth, long maxNodes) {
        var value = dynamic.getValue();
        if (value instanceof JsonElement json) {
            return checkJson(json, 1, maxDepth, new NodeBudget(maxNodes));
        }
        if (value instanceof Tag tag) {
            return checkNbt(tag, 1, maxDepth, new NodeBudget(maxNodes));
        }
        return BoundsResult.UNSUPPORTED;
    }

    static boolean isNull(Dynamic<?> dynamic) {
        var value = dynamic.getValue();
        if (value instanceof JsonElement json) {
            return json.isJsonNull();
        }
        return value instanceof Tag tag && tag.getId() == Tag.TAG_END;
    }

    static String boundedDiagnostic(String diagnostic) {
        return diagnostic.length() <= MagicSafetyCeilings.MAX_STRING_LENGTH
                ? diagnostic
                : diagnostic.substring(0, MagicSafetyCeilings.MAX_STRING_LENGTH);
    }

    private static BoundsResult checkJson(JsonElement value, int depth, int maxDepth, NodeBudget budget) {
        var status = budget.visit(depth, maxDepth);
        if (status != BoundsResult.WITHIN_LIMITS) {
            return status;
        }
        if (value instanceof JsonObject object) {
            for (var entry : object.entrySet()) {
                status = checkJson(entry.getValue(), depth + 1, maxDepth, budget);
                if (status != BoundsResult.WITHIN_LIMITS) {
                    return status;
                }
            }
        } else if (value instanceof JsonArray array) {
            for (var element : array) {
                status = checkJson(element, depth + 1, maxDepth, budget);
                if (status != BoundsResult.WITHIN_LIMITS) {
                    return status;
                }
            }
        }
        return BoundsResult.WITHIN_LIMITS;
    }

    private static BoundsResult checkNbt(Tag value, int depth, int maxDepth, NodeBudget budget) {
        var status = budget.visit(depth, maxDepth);
        if (status != BoundsResult.WITHIN_LIMITS) {
            return status;
        }
        if (value instanceof CompoundTag compound) {
            for (var key : compound.getAllKeys()) {
                var child = compound.get(key);
                if (child != null) {
                    status = checkNbt(child, depth + 1, maxDepth, budget);
                    if (status != BoundsResult.WITHIN_LIMITS) {
                        return status;
                    }
                }
            }
        } else if (value instanceof CollectionTag<?> collection) {
            for (var child : collection) {
                status = checkNbt(child, depth + 1, maxDepth, budget);
                if (status != BoundsResult.WITHIN_LIMITS) {
                    return status;
                }
            }
        }
        return BoundsResult.WITHIN_LIMITS;
    }

    enum BoundsResult {
        WITHIN_LIMITS,
        DEPTH_EXCEEDED,
        NODE_COUNT_EXCEEDED,
        UNSUPPORTED
    }

    private static final class NodeBudget {
        private final long maximum;
        private long visited;

        private NodeBudget(long maximum) {
            this.maximum = maximum;
        }

        private BoundsResult visit(int depth, int maxDepth) {
            if (depth > maxDepth) {
                return BoundsResult.DEPTH_EXCEEDED;
            }
            visited++;
            return visited > maximum ? BoundsResult.NODE_COUNT_EXCEEDED : BoundsResult.WITHIN_LIMITS;
        }
    }
}
