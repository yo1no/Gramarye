package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService$Entry", remap = false)
abstract class P11C4aConfigDepartureEntryMixin implements P11C4aConfigDepartureProbe.EntryView {
    @Shadow @Final private Connection connection;
    @Override public Connection c4a$departureConnection() { return connection; }
}
