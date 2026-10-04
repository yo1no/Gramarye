package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aClientInputProbe;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Excluded access to the two original GLFW mouse callback bodies. */
@Mixin(MouseHandler.class)
public interface P11C4aMouseInputMixin extends P11C4aClientInputProbe.MouseInput {
    @Override
    @Invoker("onMove")
    void p11$move(long window, double x, double y);

    @Override
    @Invoker("onPress")
    void p11$press(long window, int button, int action, int modifiers);
}
