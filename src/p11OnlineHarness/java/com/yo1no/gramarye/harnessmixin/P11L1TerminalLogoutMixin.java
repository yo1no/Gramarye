package com.yo1no.gramarye.harnessmixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.yo1no.gramarye.P11L1TerminalBoundaryProbe;
import com.yo1no.gramarye.P11L1ImpactCustodyProbe;
import com.yo1no.gramarye.P11NativeStorageBoundary;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(P11NativeStorageBoundary.class)
abstract class P11L1TerminalLogoutMixin {
    @WrapMethod(method = "normalLogout(Lnet/minecraft/server/network/ServerGamePacketListenerImpl;Lnet/minecraft/network/DisconnectionDetails;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V",
            require = 1, expect = 1, allow = 1)
    private static void l1$wholeNativeLogout(ServerGamePacketListenerImpl listener, DisconnectionDetails details,
            Operation<Void> nativeOriginal, Operation<Void> original) {
        boolean diagnostic = P11L1TerminalBoundaryProbe.closeDiagnosticLogoutEntering(listener);
        boolean selected = P11L1TerminalBoundaryProbe.logoutEntering(listener); Throwable primary = null;
        boolean impact = P11L1ImpactCustodyProbe.logoutEntering(listener);
        try { original.call(listener, details, nativeOriginal); }
        catch (RuntimeException | Error failure) { primary = failure; throw failure; }
        finally {
            P11L1TerminalBoundaryProbe.closeDiagnosticLogoutFinished(diagnostic, primary == null);
            P11L1TerminalBoundaryProbe.logoutFinished(selected, primary);
            P11L1ImpactCustodyProbe.logoutFinished(impact, primary);
        }
    }
}
