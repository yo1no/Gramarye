package com.yo1no.gramarye.harnessmixin;

import com.yo1no.gramarye.P11C4aAckRejectionProbe;
import com.yo1no.gramarye.P11KeepAliveBoundary;
import java.util.concurrent.locks.ReentrantLock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = P11KeepAliveBoundary.Guard.class, remap = false)
abstract class P11C4aAckRejectionLockMixin implements P11C4aAckRejectionProbe.LockObservation {
    @Shadow @Final private ReentrantLock lock;
    @Override public boolean c4a$ackLockHeldByCurrentThread() { return lock.isHeldByCurrentThread(); }
}
