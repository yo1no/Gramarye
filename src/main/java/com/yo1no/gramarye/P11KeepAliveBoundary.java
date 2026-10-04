package com.yo1no.gramarye;

import java.util.concurrent.locks.ReentrantLock;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/** Exact-connection native field linearization; no transport or caller runs under this lock. */
public final class P11KeepAliveBoundary {
    private static final ThreadLocal<Region> REGIONS = new ThreadLocal<>();

    private P11KeepAliveBoundary() { }

    /** One instance is held by the actual Connection mixin, never by a UUID registry. */
    public static final class Guard {
        private final ReentrantLock lock = new ReentrantLock();
        private long phase;
        public Guard() { }
    }

    public interface ConnectionAccess {
        Guard p11$keepAliveGuard();
        void p11$installParkedGame(Transfer transfer);
    }

    public interface CommonAccess {
        long p11$keepAlivePhase();
        void p11$beginKeepAlivePhase(ProtocolInstall install);
        boolean p11$ownsKeepAliveChallenge(long challenge);
        void p11$copyKeepAlive(Transfer transfer);
        void p11$receiveKeepAlive(Transfer transfer, long time, boolean pending, long challenge, int latency);
    }

    /** Used only around the one original native packetListener field write, not protocol IO. */
    public static final class ProtocolInstall {
        private final Guard guard;
        private final ServerCommonPacketListenerImpl listener;
        private final long phase;
        private boolean active = true;

        private ProtocolInstall(Guard guard, ServerCommonPacketListenerImpl listener, long phase) {
            this.guard = guard;
            this.listener = listener;
            this.phase = phase;
        }
    }

    /** Private construction confines the field/pointer write capability to this one native handoff. */
    public static final class Transfer {
        private final Connection connection;
        private final P11ParkingPacketListener previous;
        private final ServerGamePacketListenerImpl next;
        private final Guard guard;
        private boolean active = true;

        private Transfer(Connection connection, P11ParkingPacketListener previous,
                ServerGamePacketListenerImpl next, Guard guard) {
            this.connection = connection;
            this.previous = previous;
            this.next = next;
            this.guard = guard;
        }
    }

    /** A call-local native field region, not a retained task, player, or transition permit. */
    public static final class Region {
        private final ServerCommonPacketListenerImpl caller;
        private final Guard guard;
        private final Region previous;
        private final boolean acknowledgement;
        private final long packetId;
        private ServerCommonPacketListenerImpl receiver;
        private long now;
        private boolean locked;
        private boolean dropped;

        private Region(ServerCommonPacketListenerImpl caller, Guard guard,
                boolean acknowledgement, long packetId) {
            this.caller = caller;
            this.guard = guard;
            this.previous = REGIONS.get();
            this.acknowledgement = acknowledgement;
            this.packetId = packetId;
        }
    }

    public static Region beginAck(ServerCommonPacketListenerImpl listener, ServerboundKeepAlivePacket packet) {
        var region = begin(listener, true, packet.getId());
        if (region != null && region.guard != null) {
            try { region.now = net.minecraft.Util.getMillis(); }
            catch (RuntimeException | Error failure) { end(region); throw failure; }
        }
        return region;
    }

    public static Region beginTick(ServerCommonPacketListenerImpl listener) {
        return begin(listener, false, 0);
    }

    private static Region begin(ServerCommonPacketListenerImpl listener, boolean acknowledgement, long id) {
        var connection = listener.getConnection();
        if (!P11LiveTransitionBoundary.keepAliveManaged(connection)) {
            if (REGIONS.get() == null) { return null; }
            var disabled = new Region(listener, null, acknowledgement, id);
            REGIONS.set(disabled);
            return disabled;
        }
        return openRegion(listener, ((ConnectionAccess) connection).p11$keepAliveGuard(), acknowledgement, id);
    }

    static Region openRegion(ServerCommonPacketListenerImpl listener, Guard guard, boolean acknowledgement, long id) {
        if (((ConnectionAccess) listener.getConnection()).p11$keepAliveGuard() != guard) {
            throw new IllegalStateException("P11_KEEPALIVE_FOREIGN_GUARD");
        }
        var region = new Region(listener, guard, acknowledgement, id);
        REGIONS.set(region);
        return region;
    }

    public static Region region(ServerCommonPacketListenerImpl caller) {
        var region = REGIONS.get();
        return region != null && region.guard != null && region.caller == caller ? region : null;
    }

    public static void end(Region region) {
        if (region == null) { return; }
        release(region);
        if (region.previous == null) { REGIONS.remove(); }
        else { REGIONS.set(region.previous); }
    }

    /** Called only at the first native state-field read, after profiler/clock/host calls. */
    public static ServerCommonPacketListenerImpl fields(Region region) {
        if (region == null) { return null; }
        if (!region.locked) {
            region.guard.lock.lock();
            region.locked = true;
            PacketListener current = region.caller.getConnection().getPacketListener();
            // A terminal phase change retires its old ACK. Same-PLAY old ACKs instead
            // operate on the current common listener's native challenge and latency.
            if (!(current instanceof ServerCommonPacketListenerImpl common)
                    || ((CommonAccess) region.caller).p11$keepAlivePhase() != region.guard.phase
                    || ((CommonAccess) common).p11$keepAlivePhase() != region.guard.phase
                    || (!region.acknowledgement && current != region.caller)) {
                region.dropped = true;
                region.receiver = region.caller;
            } else {
                region.receiver = common;
            }
        }
        return region.receiver;
    }

    public static boolean dropped(Region region) { return region != null && region.dropped; }
    public static long packetId(Region region) { return region.packetId; }
    public static long now(Region region) { return region.now; }
    public static void observedTime(Region region, long now) { if (region != null) { region.now = now; } }

    public static void release(Region region) {
        if (region != null && region.locked) {
            region.locked = false;
            region.guard.lock.unlock();
        }
    }

    /** Re-enter after the native closed-listener check, which may disconnect and block. */
    public static boolean reacquireTick(Region region) {
        if (region == null) { return true; }
        fields(region);
        return !region.dropped;
    }

    public static ProtocolInstall beginProtocolInstall(Connection connection, PacketListener listener) {
        if (!(listener instanceof ServerCommonPacketListenerImpl common)) { return null; }
        if (common.getConnection() != connection) { throw new IllegalStateException("P11_KEEPALIVE_FOREIGN_LISTENER"); }
        var guard = ((ConnectionAccess) connection).p11$keepAliveGuard();
        guard.lock.lock();
        boolean normal = false;
        try {
            if (guard.phase == Long.MAX_VALUE) { throw new IllegalStateException("P11_KEEPALIVE_PHASE_EXHAUSTED"); }
            var install = new ProtocolInstall(guard, common, ++guard.phase);
            ((CommonAccess) common).p11$beginKeepAlivePhase(install);
            normal = true;
            return install;
        } finally {
            if (!normal) { guard.lock.unlock(); }
        }
    }

    public static long installedPhase(ProtocolInstall install, ServerCommonPacketListenerImpl listener) {
        if (install == null || !install.active || install.listener != listener
                || !install.guard.lock.isHeldByCurrentThread()) {
            throw new IllegalStateException("P11_KEEPALIVE_PHASE_WITHOUT_INSTALL");
        }
        return install.phase;
    }

    public static void endProtocolInstall(ProtocolInstall install) {
        if (install != null) {
            install.active = false;
            install.guard.lock.unlock();
        }
    }

    /** Revalidate the original Connection event-loop callback just before its real native write. */
    public static boolean allowScheduledKeepAlive(Connection connection, long challenge) {
        if (!P11LiveTransitionBoundary.keepAliveManaged(connection)) { return true; }
        var guard = ((ConnectionAccess) connection).p11$keepAliveGuard();
        guard.lock.lock();
        try {
            return connection.getPacketListener() instanceof ServerCommonPacketListenerImpl common
                    && ((CommonAccess) common).p11$keepAlivePhase() == guard.phase
                    && ((CommonAccess) common).p11$ownsKeepAliveChallenge(challenge);
        } finally { guard.lock.unlock(); }
    }

    static void installGame(Connection connection, P11ParkingPacketListener previous,
            ServerGamePacketListenerImpl next) {
        if (next.getConnection() != connection || previous.getConnection() != connection
                || connection.getInboundProtocol().id() != ConnectionProtocol.PLAY
                || !previous.getOwner().getId().equals(next.getOwner().getId())) {
            throw new IllegalStateException("P11_PARKING_HANDOFF_IDENTITY");
        }
        var guard = ((ConnectionAccess) connection).p11$keepAliveGuard();
        var transfer = new Transfer(connection, previous, next, guard);
        guard.lock.lock();
        try {
            if (connection.getPacketListener() != previous) {
                throw new IllegalStateException("P11_PARKING_HANDOFF_STALE");
            }
            ((CommonAccess) (ServerCommonPacketListenerImpl) previous).p11$copyKeepAlive(transfer);
            ((ConnectionAccess) connection).p11$installParkedGame(transfer);
        } finally {
            transfer.active = false;
            guard.lock.unlock();
        }
    }

    public static ServerGamePacketListenerImpl transferTarget(Transfer transfer, Connection connection) {
        if (!valid(transfer) || transfer.connection != connection
                || connection.getPacketListener() != transfer.previous) {
            throw new IllegalStateException("P11_PARKING_TRANSFER_NOT_CURRENT");
        }
        return transfer.next;
    }

    public static boolean transferFrom(Transfer transfer, ServerCommonPacketListenerImpl previous) {
        return valid(transfer) && transfer.previous == previous
                && ((CommonAccess) previous).p11$keepAlivePhase() == transfer.guard.phase
                && transfer.connection.getPacketListener() == previous;
    }

    public static boolean transferTo(Transfer transfer, ServerCommonPacketListenerImpl next) {
        return valid(transfer) && transfer.next == next
                && transfer.connection.getPacketListener() == transfer.previous;
    }

    public static long transferPhase(Transfer transfer, ServerCommonPacketListenerImpl next) {
        if (!transferTo(transfer, next)) { throw new IllegalStateException("P11_KEEPALIVE_TRANSFER_PHASE"); }
        return transfer.guard.phase;
    }

    private static boolean valid(Transfer transfer) {
        return transfer != null && transfer.active && transfer.guard.lock.isHeldByCurrentThread();
    }
}
