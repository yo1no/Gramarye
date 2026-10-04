package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aParkingProbe;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService$Entry", remap = false)
abstract class P11C4aParkingEntryMixin implements P11C4aParkingProbe.EntryAccess {
    @Shadow @Final private Connection connection;
    @Shadow private boolean waiting;
    @Override public Connection c4a$parkingConnection() { return connection; }
    @Override public boolean c4a$parkingWaiting() { return waiting; }
}
