package com.yo1no.gramarye.magic.definition.document;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.yo1no.gramarye.magic.definition.tree.DynamicTreeBounds;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import net.minecraft.nbt.Tag;

final class DynamicTreeSupport {
    private DynamicTreeSupport() {
    }

    static BoundsResult checkBounds(Dynamic<?> dynamic, int maxDepth, long maxNodes) {
        return switch (DynamicTreeBounds.check(dynamic, maxDepth, maxNodes)) {
            case WITHIN_LIMITS -> BoundsResult.WITHIN_LIMITS;
            case DEPTH_EXCEEDED -> BoundsResult.DEPTH_EXCEEDED;
            case NODE_COUNT_EXCEEDED -> BoundsResult.NODE_COUNT_EXCEEDED;
            case KEY_LENGTH_EXCEEDED -> BoundsResult.KEY_LENGTH_EXCEEDED;
            case UNSUPPORTED -> BoundsResult.UNSUPPORTED;
        };
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

    enum BoundsResult {
        WITHIN_LIMITS,
        DEPTH_EXCEEDED,
        NODE_COUNT_EXCEEDED,
        KEY_LENGTH_EXCEEDED,
        UNSUPPORTED
    }
}
