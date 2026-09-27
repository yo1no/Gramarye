package com.yo1no.gramarye;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

/**
 * A slot-local, bounded identity custodian, not an actor locator or execution grant.
 * Only this owner retains native references. Captures retained by control work are actor-free.
 * No native lifecycle producer is attached in the foundation slice.
 */
final class P11IdentityOwner {
    private final SlotKey slot;
    private MinecraftServer server;
    private final int maxUuids;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private long lastConnectionEpoch;
    private boolean stopped;
    private ReceiptDomain receiptDomain;

    P11IdentityOwner(MinecraftServer server, int maxUuids) {
        this(Objects.requireNonNull(server, "server"), maxUuids, false);
    }

    private P11IdentityOwner(MinecraftServer server, int maxUuids, boolean isolated) {
        if (maxUuids < 1 || (server == null) != isolated) {
            throw new IllegalArgumentException("invalid identity domain");
        }
        this.server = server;
        this.maxUuids = maxUuids;
        this.slot = new SlotKey(isolated);
    }

    /** Pure fixtures use a separate domain which can never pass liveCurrent. */
    static P11IdentityOwner isolatedModel(int maxUuids) {
        return new P11IdentityOwner(null, maxUuids, true);
    }

    SlotKey slot() {
        return slot;
    }

    synchronized Optional<CapturedIdentity> bindAuthenticated(
            ServerPlayer actor, Connection connection) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(connection, "connection");
        if (server == null || stopped || !server.isSameThread()
                || actor.getServer() != server
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor
                || actor.connection == null || actor.connection.player != actor
                || actor.connection.getConnection() != connection
                || connection.getPacketListener() != actor.connection || !connection.isConnected()) {
            return Optional.empty();
        }
        return bind(actor.getUUID(), new NativeReferences(actor, connection));
    }

    synchronized Optional<CapturedIdentity> bindAuthenticatedActorless(
            ServerConfigurationPacketListenerImpl listener) {
        Objects.requireNonNull(listener, "listener");
        var connection = listener.getConnection();
        if (server == null || stopped || !server.isSameThread()
                || listener.getMainThreadEventLoop() != server
                || connection.getPacketListener() != listener || !connection.isConnected()
                || listener.getOwner().getId() == null) {
            return Optional.empty();
        }
        return bind(listener.getOwner().getId(), new NativeActorlessReferences(listener, connection));
    }

    /**
     * B is only an expected identity here. Listener/roster membership and readiness remain
     * separate facts; retaining the connection does not certify native transition success.
     */
    synchronized Optional<CapturedIdentity> bindExpectedActor(
            CapturedIdentity current, ServerPlayer expectedActor) {
        Objects.requireNonNull(expectedActor, "expectedActor");
        if (server == null || !server.isSameThread() || !current(current)
                || expectedActor.getServer() != server
                || !expectedActor.getUUID().equals(current.uuid())) {
            return Optional.empty();
        }
        var account = accounts.get(current.uuid());
        var connection = nativeConnection(account.binding.references);
        if (connection == null
                || expectedActor.connection == null
                || expectedActor.connection.getConnection() != connection
                || !connection.isConnected()) {
            return Optional.empty();
        }
        return bind(current.uuid(), new NativeReferences(expectedActor, connection));
    }

    synchronized ModelActor modelActor(UUID uuid, int entityId) {
        requireModel();
        return new ModelActor(slot, Objects.requireNonNull(uuid, "uuid"), entityId);
    }

    synchronized ModelConnection modelConnection() {
        requireModel();
        return new ModelConnection(slot);
    }

    synchronized Optional<CapturedIdentity> bindModel(
            ModelActor actor, ModelConnection connection) {
        requireModel();
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(connection, "connection");
        if (stopped || actor.slot != slot || connection.slot != slot || connection.retired) {
            return Optional.empty();
        }
        return bind(actor.uuid, new ModelReferences(actor, connection));
    }

    synchronized Optional<CapturedIdentity> modelActorless(UUID uuid, ModelConnection connection) {
        requireModel();
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(connection, "connection");
        if (stopped || connection.slot != slot || connection.retired) {
            return Optional.empty();
        }
        return bind(uuid, new ModelActorlessReferences(connection));
    }

    private Optional<CapturedIdentity> bind(UUID uuid, References references) {
        for (var existing : accounts.values()) {
            if (!existing.key.uuid.equals(uuid) && existing.binding != null
                    && existing.binding.references.sameConnection(references)) {
                return Optional.empty();
            }
        }
        var account = accounts.get(uuid);
        if (account == null && accounts.size() >= maxUuids) {
            return Optional.empty();
        }
        var prior = account == null ? null : account.binding;
        if (prior != null && prior.references.sameActor(references)
                && prior.references.sameConnection(references)) {
            return Optional.of(prior.capture);
        }
        boolean sameConnection = prior != null && prior.references.sameConnection(references);
        if ((!sameConnection && lastConnectionEpoch == Long.MAX_VALUE)
                || (sameConnection && !references.actorless()
                        && prior.lastActorGeneration == Long.MAX_VALUE)) {
            return Optional.empty();
        }
        if (account == null) {
            account = new Account(new AccountKey(slot, uuid));
            accounts.put(uuid, account);
        }
        var connection = sameConnection
                ? prior.capture.connection
                : new ConnectionKey(slot, uuid, ++lastConnectionEpoch);
        long lastActor = sameConnection ? prior.lastActorGeneration : 0;
        long generation = references.actorless() ? 0 : lastActor + 1;
        var capture = new CapturedIdentity(account.key, connection,
                generation == 0 ? null : new ActorKey(slot, uuid, connection.epoch, generation));
        // An unused-account discharge is not authority to release a later binding lifecycle.
        account.discharge = null;
        if (prior != null) {
            prior.capture.currentBinding = false;
            if (!sameConnection) {
                prior.capture.connection.currentConnection = false;
                var oldModelConnection = modelConnection(prior.references);
                if (oldModelConnection != null) {
                    oldModelConnection.retired = true;
                }
            }
        }
        // Replacing a binding releases the old native graph; stale captures contain only keys.
        account.binding = new Binding(capture, references, Math.max(lastActor, generation));
        return Optional.of(capture);
    }

    synchronized boolean current(CapturedIdentity capture) {
        if (stopped || capture == null || capture.slot() != slot) {
            return false;
        }
        var account = accounts.get(capture.uuid());
        return account != null && account.binding != null
                && account.binding.capture == capture;
    }

    synchronized boolean liveCurrent(CapturedIdentity capture) {
        if (server == null || !server.isSameThread() || !current(capture)) {
            return false;
        }
        var bound = accounts.get(capture.uuid()).binding.references;
        if (bound instanceof NativeActorlessReferences actorless) {
            return actorless.listener.getMainThreadEventLoop() == server
                    && actorless.connection.getPacketListener() == actorless.listener
                    && actorless.connection.isConnected()
                    && capture.uuid().equals(actorless.listener.getOwner().getId());
        }
        var references = (NativeReferences) bound;
        var actor = references.actor;
        return actor.getServer() == server
                && server.getPlayerList().getPlayer(capture.uuid()) == actor
                && actor.connection != null && actor.connection.player == actor
                && actor.connection.getConnection() == references.connection
                && references.connection.getPacketListener() == actor.connection
                && references.connection.isConnected();
    }

    synchronized boolean owns(AccountKey account) {
        return !stopped && account != null && account.slot == slot
                && accounts.containsKey(account.uuid)
                && accounts.get(account.uuid).key == account;
    }

    synchronized boolean retireConnection(CapturedIdentity capture) {
        if (!current(capture)) {
            return false;
        }
        var account = accounts.get(capture.uuid());
        var nativeConnection = nativeConnection(account.binding.references);
        if (nativeConnection != null && nativeConnection.isConnected()) {
            return false;
        }
        var modelConnection = modelConnection(account.binding.references);
        if (modelConnection != null) {
            modelConnection.retired = true;
        }
        account.binding.capture.currentBinding = false;
        account.binding.capture.connection.currentConnection = false;
        // Retirement is final for this connection; it is not a phase-handoff shortcut.
        // Control retirement drops actor/connection roots, not account/data responsibility.
        account.binding = null;
        return true;
    }

    synchronized int retainedAccounts() {
        return accounts.size();
    }

    synchronized ReceiptDomain claimReceiptDomain() {
        if (stopped || receiptDomain != null) {
            throw new IllegalStateException("one receipt ledger per identity owner");
        }
        receiptDomain = new ReceiptDomain();
        return receiptDomain;
    }

    synchronized OptionalLong nextSourceEpoch(ReceiptDomain domain, CapturedIdentity current) {
        if (domain == null || domain != receiptDomain || !current(current)) {
            return OptionalLong.empty();
        }
        var account = accounts.get(current.uuid());
        if (account.lastSourceEpoch == Long.MAX_VALUE) {
            return OptionalLong.empty();
        }
        // Allocation starts a new source responsibility. Revoke in this same boundary so
        // retirement cannot use an old discharge before the ledger installs that source.
        account.discharge = null;
        return OptionalLong.of(++account.lastSourceEpoch);
    }

    synchronized boolean releaseUnboundAccount(
            P11ReceiptLedger.Discharge discharge) {
        if (discharge == null || discharge.domain() != receiptDomain
                || !owns(discharge.account())) {
            return false;
        }
        var account = accounts.get(discharge.account().uuid);
        if (account.binding != null || account.discharge != discharge) {
            return false;
        }
        accounts.remove(discharge.account().uuid);
        return true;
    }

    synchronized boolean installDischarge(P11ReceiptLedger.Discharge discharge) {
        if (discharge == null || discharge.domain() != receiptDomain || !owns(discharge.account())) {
            return false;
        }
        accounts.get(discharge.account().uuid).discharge = discharge;
        return true;
    }

    synchronized void revokeDischarge(ReceiptDomain domain, AccountKey account) {
        if (domain == receiptDomain && owns(account)) {
            accounts.get(account.uuid).discharge = null;
        }
    }

    synchronized int retainedBindings() {
        int count = 0;
        for (var account : accounts.values()) {
            if (account.binding != null) {
                count++;
            }
        }
        return count;
    }

    synchronized void stop() {
        stopped = true;
        for (var account : accounts.values()) {
            if (account.binding != null) {
                account.binding.capture.currentBinding = false;
                account.binding.capture.connection.currentConnection = false;
            }
        }
        accounts.clear();
        server = null;
    }

    private void requireModel() {
        if (!slot.isolatedModel) {
            throw new IllegalStateException("model identities are not live identities");
        }
    }

    static final class SlotKey {
        private final boolean isolatedModel;

        private SlotKey(boolean isolatedModel) { this.isolatedModel = isolatedModel; }
    }

    static final class ReceiptDomain {
        private ReceiptDomain() {}
    }

    static final class AccountKey {
        private final SlotKey slot;
        private final UUID uuid;

        private AccountKey(SlotKey slot, UUID uuid) {
            this.slot = slot;
            this.uuid = uuid;
        }

        SlotKey slot() { return slot; }
        UUID uuid() { return uuid; }
    }

    static final class ConnectionKey {
        private final SlotKey slot;
        private final UUID uuid;
        private final long epoch;
        private volatile boolean currentConnection = true;
        private boolean controlClaimed;

        private ConnectionKey(SlotKey slot, UUID uuid, long epoch) {
            this.slot = slot;
            this.uuid = uuid;
            this.epoch = epoch;
        }

        SlotKey slot() { return slot; }
        UUID uuid() { return uuid; }
        long epoch() { return epoch; }
        boolean currentConnection() { return currentConnection; }

        synchronized boolean claimControl() {
            if (!currentConnection || controlClaimed) {
                return false;
            }
            controlClaimed = true;
            return true;
        }
    }

    static final class ActorKey {
        private final SlotKey slot;
        private final UUID uuid;
        private final long connectionEpoch;
        private final long generation;

        private ActorKey(SlotKey slot, UUID uuid, long connectionEpoch, long generation) {
            this.slot = slot;
            this.uuid = uuid;
            this.connectionEpoch = connectionEpoch;
            this.generation = generation;
        }

        SlotKey slot() { return slot; }
        UUID uuid() { return uuid; }
        long connectionEpoch() { return connectionEpoch; }
        long generation() { return generation; }
    }

    static final class CapturedIdentity {
        private final AccountKey account;
        private final ConnectionKey connection;
        private final ActorKey actor;
        private volatile boolean currentBinding = true;

        private CapturedIdentity(AccountKey account, ConnectionKey connection, ActorKey actor) {
            this.account = account;
            this.connection = connection;
            this.actor = actor;
        }

        SlotKey slot() { return account.slot; }
        AccountKey account() { return account; }
        UUID uuid() { return account.uuid; }
        ConnectionKey connection() { return connection; }
        ActorKey actor() { return actor; }
        long connectionEpoch() { return connection.epoch; }
        long actorGeneration() { return actor == null ? 0 : actor.generation; }
        boolean isolatedModel() { return slot().isolatedModel; }
        boolean currentBinding() { return currentBinding && connection.currentConnection; }
    }

    /** Deliberately equals-by-id fixture: the production binding kernel must still use ==. */
    static final class ModelActor {
        private final SlotKey slot;
        private final UUID uuid;
        private final int entityId;

        private ModelActor(SlotKey slot, UUID uuid, int entityId) {
            this.slot = slot;
            this.uuid = uuid;
            this.entityId = entityId;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ModelActor actor && entityId == actor.entityId;
        }

        @Override
        public int hashCode() {
            return entityId;
        }
    }

    static final class ModelConnection {
        private final SlotKey slot;
        private boolean retired;

        private ModelConnection(SlotKey slot) {
            this.slot = slot;
        }
    }

    private static final class Account {
        private final AccountKey key;
        private Binding binding;
        private P11ReceiptLedger.Discharge discharge;
        private long lastSourceEpoch;

        private Account(AccountKey key) {
            this.key = key;
        }
    }

    private record Binding(CapturedIdentity capture, References references, long lastActorGeneration) {}

    private sealed interface References permits NativeReferences, ModelReferences,
            NativeActorlessReferences, ModelActorlessReferences {
        boolean sameActor(References other);
        boolean sameConnection(References other);
        default boolean actorless() { return false; }
    }

    private static Connection nativeConnection(References references) {
        if (references instanceof NativeReferences nativeReferences) {
            return nativeReferences.connection;
        }
        return references instanceof NativeActorlessReferences actorless ? actorless.connection : null;
    }

    private static ModelConnection modelConnection(References references) {
        if (references instanceof ModelReferences modelReferences) {
            return modelReferences.connection;
        }
        return references instanceof ModelActorlessReferences actorless ? actorless.connection : null;
    }

    private record NativeReferences(ServerPlayer actor, Connection connection) implements References {
        @Override
        public boolean sameActor(References other) {
            return other instanceof NativeReferences nativeOther && actor == nativeOther.actor;
        }

        @Override
        public boolean sameConnection(References other) {
            return connection == nativeConnection(other);
        }
    }

    private record ModelReferences(ModelActor actor, ModelConnection connection) implements References {
        @Override
        public boolean sameActor(References other) {
            return other instanceof ModelReferences modelOther && actor == modelOther.actor;
        }

        @Override
        public boolean sameConnection(References other) {
            return connection == modelConnection(other);
        }
    }

    private record NativeActorlessReferences(
            ServerConfigurationPacketListenerImpl listener, Connection connection) implements References {
        @Override
        public boolean sameActor(References other) {
            return other instanceof NativeActorlessReferences actorless && listener == actorless.listener;
        }

        @Override
        public boolean sameConnection(References other) {
            return connection == nativeConnection(other);
        }

        @Override
        public boolean actorless() { return true; }
    }

    private record ModelActorlessReferences(ModelConnection connection) implements References {
        @Override
        public boolean sameActor(References other) {
            return other instanceof ModelActorlessReferences actorless && connection == actorless.connection;
        }

        @Override
        public boolean sameConnection(References other) {
            return connection == modelConnection(other);
        }

        @Override
        public boolean actorless() { return true; }
    }
}
