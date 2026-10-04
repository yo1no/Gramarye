package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aConfigResetProbe;
import com.yo1no.gramarye.P11KeepAliveBoundary;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Read-only observation; never acquires/releases or exports the actual lock. */
@Mixin(value = P11KeepAliveBoundary.Guard.class, remap = false)
abstract class P11C4aConfigResetLockMixin implements P11C4aConfigResetProbe.LockObservation {
    @Shadow @Final private ReentrantLock lock;
    @Override public boolean c4a$resetLockHeldByCurrentThread() { return lock.isHeldByCurrentThread(); }
}
