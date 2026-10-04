package com.yo1no.gramarye.harnessmixin;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
@Mixin(targets = "com.yo1no.gramarye.P11KeepAliveBoundary$Guard", remap = false)
abstract class P11C4aConfigDepartureLockMixin implements P11C4aConfigDepartureProbe.LockView {
    @Shadow @Final private ReentrantLock lock;
    @Override public boolean c4a$departureLockHeld() { return lock.isHeldByCurrentThread(); }
}
