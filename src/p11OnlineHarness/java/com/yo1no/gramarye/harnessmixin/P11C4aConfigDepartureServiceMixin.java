package com.yo1no.gramarye.harnessmixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe.Mode;
import com.yo1no.gramarye.P11C4aConfigDepartureProbe.Snapshot;
import java.util.Map;
import java.util.Optional;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(targets = "com.yo1no.gramarye.P11LiveTransitionService", remap = false)
abstract class P11C4aConfigDepartureServiceMixin implements P11C4aConfigDepartureProbe.ServiceView {
    @Shadow @Final private Map<Connection, ?> entries;
    @Shadow @Final private Map<Connection, Boolean> starting;
    @Shadow private int waiting;
    @Shadow private volatile boolean stopping;
    @Override public boolean c4a$departureEntriesHeld() { return Thread.holdsLock(entries); }
    @Override public Snapshot c4a$departureSnapshot(Connection connection) {
        int waits, size; boolean present, queued, initial, stopped;
        synchronized (entries) {
            waits = waiting; size = entries.size(); present = entries.containsKey(connection);
            queued = starting.containsKey(connection); initial = Boolean.TRUE.equals(starting.get(connection)); stopped = stopping;
        }
        return new Snapshot(waits, size, present, queued, initial, stopped);
    }
    @WrapOperation(method = "initializeConfiguration(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11IdentityOwner;bindAuthenticatedActorless(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)Ljava/util/Optional;"),
            require = 1, expect = 1, allow = 1)
    private Optional<?> departure$initial(@Coerce Object owner, ServerConfigurationPacketListenerImpl listener, Operation<Optional<?>> original) {
        return P11C4aConfigDepartureProbe.bind(this, owner, listener, Mode.INITIAL_JOIN, original);
    }
    @WrapOperation(method = "completeConfigurationAcknowledgement(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11IdentityOwner;bindAuthenticatedActorless(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)Ljava/util/Optional;"),
            require = 1, expect = 1, allow = 1)
    private Optional<?> departure$ack(@Coerce Object owner, ServerConfigurationPacketListenerImpl listener, Operation<Optional<?>> original) {
        return P11C4aConfigDepartureProbe.bind(this, owner, listener, Mode.ENTER_CONFIG_ACK, original);
    }
    @WrapMethod(method = "initializeConfiguration(Lnet/minecraft/server/network/ServerConfigurationPacketListenerImpl;)V", require = 1, expect = 1, allow = 1)
    private void departure$initialReturned(ServerConfigurationPacketListenerImpl listener, Operation<Void> original) {
        original.call(listener);
        P11C4aConfigDepartureProbe.returned(this, listener.getConnection(), Mode.INITIAL_JOIN);
    }
    @WrapMethod(method = "completeConfigurationAcknowledgement(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", require = 1, expect = 1, allow = 1)
    private void departure$ackReturned(@Coerce Object entry, Operation<Void> original) {
        original.call(entry);
        P11C4aConfigDepartureProbe.returned(this, ((P11C4aConfigDepartureProbe.EntryView) entry).c4a$departureConnection(), Mode.ENTER_CONFIG_ACK);
    }
    @WrapMethod(method = "retire(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V", require = 1, expect = 1, allow = 1)
    private void departure$actualRetire(@Coerce Object entry, Operation<Void> original) {
        boolean selected = P11C4aConfigDepartureProbe.beginRetire(entry);
        boolean normal = false;
        try { original.call(entry); normal = true; }
        finally { P11C4aConfigDepartureProbe.endRetire(selected, normal); }
    }
    @WrapOperation(method = "retire(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11IdentityOwner;retireConnection(Lcom/yo1no/gramarye/P11IdentityOwner$CapturedIdentity;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean departure$identityRetired(@Coerce Object owner, @Coerce Object capture, Operation<Boolean> original) {
        boolean result = original.call(owner, capture);
        P11C4aConfigDepartureProbe.retired(true, capture, result); return result;
    }
    @WrapOperation(method = "retire(Lcom/yo1no/gramarye/P11LiveTransitionService$Entry;)V",
            at = @At(value = "INVOKE", target = "Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher;retire(Lcom/yo1no/gramarye/P11ControlBudgets$FairDispatcher$Member;)Z"),
            require = 1, expect = 1, allow = 1)
    private boolean departure$memberRetired(@Coerce Object owner, @Coerce Object member, Operation<Boolean> original) {
        boolean result = original.call(owner, member);
        P11C4aConfigDepartureProbe.retired(false, member, result); return result;
    }
}
