package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1ServerHarness;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/** Observes the actual native receiver after product routing, not a console score surrogate. */
@Mixin(Player.class)
abstract class P11L1ScoreMixin {
    @WrapMethod(method = "increaseScore(I)V", require = 1, expect = 1, allow = 1)
    private void p11$l1NativeScore(int amount, Operation<Void> original) {
        var receiver = (Player) (Object) this;
        boolean selected = P11L1ServerHarness.scoreEntering(receiver);
        int before = selected ? receiver.getScore() : 0;
        original.call(amount);
        P11L1ServerHarness.scoreReturned(receiver, amount, before, selected);
    }
}
