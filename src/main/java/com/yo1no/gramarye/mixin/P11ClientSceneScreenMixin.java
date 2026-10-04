package com.yo1no.gramarye.mixin;

import com.yo1no.gramarye.P11ClientTransitions;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.WinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Exact original native presentation carries scalars; no Screen is kept by the controller. */
@Mixin({DeathScreen.class, WinScreen.class})
abstract class P11ClientSceneScreenMixin implements P11ClientTransitions.SceneScreenAccess {
    @Unique private long p11$connectionEpoch;
    @Unique private long p11$sceneSerial;

    @Override
    public void p11$bindNativeScene(long connectionEpoch, long sceneSerial) {
        if (p11$connectionEpoch == 0 && p11$sceneSerial == 0
                && connectionEpoch > 0 && sceneSerial > 0) {
            p11$connectionEpoch = connectionEpoch;
            p11$sceneSerial = sceneSerial;
        }
    }

    @Override
    public boolean p11$matchesNativeScene(long connectionEpoch, long sceneSerial) {
        return connectionEpoch > 0 && sceneSerial > 0
                && p11$connectionEpoch == connectionEpoch && p11$sceneSerial == sceneSerial;
    }
}
