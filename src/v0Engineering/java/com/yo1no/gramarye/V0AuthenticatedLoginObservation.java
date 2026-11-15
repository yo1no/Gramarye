package com.yo1no.gramarye;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

/** Engineering-only bridge for the sealed native authentication-success call. */
public final class V0AuthenticatedLoginObservation {
    private static final int AUTH_WINDOW_TICKS = 600;
    private static final Object GATE = new Object();
    private static final ThreadLocal<Call> EXACT_CALL = new ThreadLocal<>();
    private static Slot bound;

    private V0AuthenticatedLoginObservation() {}

    static boolean bind(MinecraftServer server, V0CatalogAuthority authority, UUID roleA, UUID roleB) {
        if (!owner(server, authority) || roleA == null || roleB == null || roleA.equals(roleB)) {
            return false;
        }
        synchronized (GATE) {
            if (bound != null) {
                return false;
            }
            bound = new Slot(server, authority, roleA, roleB);
            return true;
        }
    }

    static void advance(MinecraftServer server, V0CatalogAuthority authority) {
        if (!owner(server, authority)) {
            return;
        }
        synchronized (GATE) {
            if (matches(server, authority)) {
                bound.advance(server.getTickCount());
            }
        }
    }

    static void retire(MinecraftServer server, V0CatalogAuthority authority) {
        if (!owner(server, authority)) {
            return;
        }
        synchronized (GATE) {
            if (matches(server, authority)) {
                bound = null;
            }
        }
    }

    public static void enter(ServerLoginPacketListenerImpl login, GameProfile profile) {
        var previous = EXACT_CALL.get();
        if (previous != null) {
            previous.nested = true;
            return;
        }
        if (login == null || profile == null) {
            return;
        }
        synchronized (GATE) {
            var role = bound == null ? null : bound.find(profile.getId());
            if (role != null && role.inFlight == null) {
                var exact = new Call(login, profile, bound, role);
                EXACT_CALL.set(exact);
                role.inFlight = exact;
            }
        }
    }

    public static void leave(ServerLoginPacketListenerImpl login, GameProfile profile) {
        var exact = EXACT_CALL.get();
        if (exact != null && exact.login == login && exact.profile == profile) {
            EXACT_CALL.remove();
            synchronized (GATE) {
                if (exact.role.inFlight == exact) {
                    exact.role.inFlight = null;
                }
            }
        }
    }

    public static void observe(ServerLoginPacketListenerImpl login, GameProfile profile,
            MinecraftServer server, Connection connection) {
        var exact = EXACT_CALL.get();
        if (exact == null || exact.nested || exact.observed || exact.login != login || exact.profile != profile
                || profile == null || connection == null || connection.isMemoryConnection()) {
            return;
        }
        exact.observed = true;
        synchronized (GATE) {
            if (bound == exact.slot && bound.server == server && exact.role.inFlight == exact
                    && connection.isConnected() && exact.role.connection == null) {
                exact.role.connection = connection;
                exact.role.observedTick = bound.tick;
            }
        }
    }

    static boolean consume(MinecraftServer server, Connection connection, UUID authenticatedPlayerId,
            V0CatalogAuthority authority) {
        if (!owner(server, authority) || connection == null || connection.isMemoryConnection()) {
            return false;
        }
        synchronized (GATE) {
            if (!matches(server, authority)) {
                return false;
            }
            bound.advance(server.getTickCount());
            var role = bound.find(authenticatedPlayerId);
            if (role == null || role.connection != connection) {
                return false;
            }
            role.connection = null;
            return true;
        }
    }

    private static boolean owner(MinecraftServer server, V0CatalogAuthority authority) {
        return server != null && authority != null && server.isSameThread() && authority.isOwnerThread();
    }

    private static boolean matches(MinecraftServer server, V0CatalogAuthority authority) {
        return bound != null && bound.server == server && bound.authority == authority;
    }

    private static final class Call {
        private final ServerLoginPacketListenerImpl login;
        private final GameProfile profile;
        private final Slot slot;
        private final Role role;
        private boolean nested;
        private boolean observed;

        private Call(ServerLoginPacketListenerImpl login, GameProfile profile, Slot slot, Role role) {
            this.login = login;
            this.profile = profile;
            this.slot = slot;
            this.role = role;
        }
    }

    private static final class Slot {
        private final MinecraftServer server;
        private final V0CatalogAuthority authority;
        private final Role a;
        private final Role b;
        private int tick;

        private Slot(MinecraftServer server, V0CatalogAuthority authority, UUID a, UUID b) {
            this.server = server;
            this.authority = authority;
            this.a = new Role(a);
            this.b = new Role(b);
            this.tick = server.getTickCount();
        }

        private Role find(UUID id) {
            return a.id.equals(id) ? a : b.id.equals(id) ? b : null;
        }

        private void advance(int now) {
            tick = now;
            a.expire(now);
            b.expire(now);
        }
    }

    private static final class Role {
        private final UUID id;
        private Call inFlight;
        private Connection connection;
        private int observedTick;

        private Role(UUID id) {
            this.id = id;
        }

        private void expire(int now) {
            if (connection != null && (!connection.isConnected()
                    || Integer.toUnsignedLong(now - observedTick) >= AUTH_WINDOW_TICKS)) {
                connection = null;
            }
        }
    }
}
