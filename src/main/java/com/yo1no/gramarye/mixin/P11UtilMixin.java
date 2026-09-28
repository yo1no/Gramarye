package com.yo1no.gramarye.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import java.nio.file.Path;
import net.minecraft.Util;
import org.spongepowered.asm.mixin.Mixin;

/** Consumes the real replace boolean, including calls whose outer safeReplaceFile returns void. */
@Mixin(Util.class)
abstract class P11UtilMixin {
    @WrapMethod(
            method = "safeReplaceOrMoveFile(Ljava/nio/file/Path;Ljava/nio/file/Path;Ljava/nio/file/Path;Z)Z",
            require = 1, expect = 1, allow = 1)
    private static boolean p11$replace(Path current, Path incoming, Path backup,
            boolean noRestore, Operation<Boolean> original) {
        // Check before backup/deletion too. Returning false from only the later replacement
        // sub-step would otherwise trigger the original restore side effect.
        if (!P11NativeStorageBoundary.beforeReplace(current, incoming, backup, noRestore)) {
            // The helper was not executed: this is a retained refusal, not an observed
            // native false result. beforeReplace records the refusal without a fake receipt.
            return false;
        }
        // Exceptional exit has no boolean result. Leave it unknown rather than invent false;
        // the original exception and the enclosing native writer's catch/finally remain intact.
        boolean replaced = original.call(current, incoming, backup, noRestore);
        P11NativeStorageBoundary.replaceResult(replaced);
        return replaced;
    }
}
