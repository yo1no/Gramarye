package com.yo1no.gramarye;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.UnconfiguredPipelineHandler;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.GameProtocols;
import net.minecraft.network.protocol.configuration.ClientConfigurationPacketListener;
import net.minecraft.network.protocol.configuration.ConfigurationProtocols;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Actual live clientbound transport fixture with an explicitly replaceable PLAY listener. */
final class P8ClientPlayConnection implements AutoCloseable {
    private final Connection connection = new Connection(PacketFlow.CLIENTBOUND);
    private final EmbeddedChannel channel = new EmbeddedChannel(
            new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel initializedChannel) {
                    Connection.configureInMemoryPipeline(
                            initializedChannel.pipeline(), PacketFlow.CLIENTBOUND);
                    connection.configurePacketHandler(initializedChannel.pipeline());
                }
            });
    private ICommonPacketListener listener;
    private boolean closed;

    P8ClientPlayConnection() {
        if (!connection.isConnected()) {
            throw new AssertionError("embedded clientbound transport did not open");
        }
        NetworkRegistry.configureMockConnection(connection);
        listener = replacePlayListener();
    }

    Connection connection() {
        return connection;
    }

    ICommonPacketListener listener() {
        return listener;
    }

    ICommonPacketListener replacePlayListener() {
        if (closed || !connection.isConnected()) {
            throw new IllegalStateException("test transport is closed");
        }
        if (connection.getPacketListener() != null) {
            transitionInboundToUnconfigured();
            var configurationListener = (ClientConfigurationPacketListener)
                    Proxy.newProxyInstance(
                            P8ClientPlayConnection.class.getClassLoader(),
                            new Class<?>[] {ClientConfigurationPacketListener.class},
                            (proxy, method, arguments) -> switch (method.getName()) {
                                case "getConnection" -> connection;
                                case "getConnectionType" -> ConnectionType.NEOFORGE;
                                case "flow" -> PacketFlow.CLIENTBOUND;
                                case "protocol" -> ConnectionProtocol.CONFIGURATION;
                                case "isAcceptingMessages" -> connection.isConnected();
                                case "onDisconnect" -> null;
                                case "toString" -> "P8 test CONFIGURATION listener";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == arguments[0];
                                default -> throw new AssertionError(
                                        "test CONFIGURATION listener call was not expected: "
                                                + method);
                            });
            connection.setupInboundProtocol(
                    ConfigurationProtocols.CLIENTBOUND, configurationListener);
            transitionInboundToUnconfigured();
        }
        listener = (ICommonPacketListener) Proxy.newProxyInstance(
                P8ClientPlayConnection.class.getClassLoader(),
                new Class<?>[] {ClientGamePacketListener.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getConnection" -> connection;
                    case "getConnectionType" -> ConnectionType.NEOFORGE;
                    case "flow" -> PacketFlow.CLIENTBOUND;
                    case "protocol" -> ConnectionProtocol.PLAY;
                    case "isAcceptingMessages" -> connection.isConnected();
                    case "onDisconnect" -> null;
                    case "toString" -> "P8 test PLAY listener";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new AssertionError(
                            "test PLAY listener call was not expected: " + method);
                });
        connection.setupInboundProtocol(
                GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                        RegistryFriendlyByteBuf.decorator(
                                RegistryAccess.EMPTY, ConnectionType.NEOFORGE)),
                (ClientGamePacketListener) listener);
        return listener;
    }

    private void transitionInboundToUnconfigured() {
        var pipeline = connection.channel().pipeline();
        if (pipeline.get("bundler") != null) {
            pipeline.remove("bundler");
        }
        if (pipeline.get("decoder") != null) {
            pipeline.addBefore(
                    "decoder",
                    "inbound_config",
                    new UnconfiguredPipelineHandler.Inbound());
            pipeline.remove("decoder");
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            channel.finishAndReleaseAll();
        }
    }
}

/** Compatibility harness for direct state tests that are not themselves about transport epochs. */
final class P8ClientTestEpochs {
    private static final Map<P8ClientPresentationState, Epoch> EPOCHS =
            new IdentityHashMap<>();

    private P8ClientTestEpochs() {
        throw new AssertionError("no instances");
    }

    static P8ClientConnectionOpenResult open(P8ClientPresentationState state) {
        Objects.requireNonNull(state, "state");
        var epoch = epochForOpen(state);
        var result = state.onConnectionOpened(
                epoch.transport().connection(), epoch.transport().listener());
        if (result.opened()) {
            epoch.markPublished();
        }
        result.catalogDrain().ifPresent(P8ClientDispatchTask::run);
        return result;
    }

    static P8ClientTransportMaintenanceResult logout(P8ClientPresentationState state) {
        Objects.requireNonNull(state, "state");
        final Epoch epoch;
        synchronized (EPOCHS) {
            epoch = EPOCHS.get(state);
        }
        if (epoch == null) {
            return state.onLoggedOut(null, null);
        }
        try {
            return state.onLoggedOut(
                    epoch.transport().connection(), epoch.transport().listener());
        } finally {
            synchronized (EPOCHS) {
                if (EPOCHS.get(state) == epoch) {
                    EPOCHS.remove(state);
                }
            }
            epoch.transport().close();
        }
    }

    static Optional<P8ClientDispatchTask> prepareProfileCatalog(
            P8ClientPresentationState state, ProfileCatalogPayload payload) {
        var transport = currentTransport(state);
        return state.prepareProfileCatalog(
                transport.connection(), transport.listener(), payload);
    }

    static Optional<P8ClientDispatchTask> preparePresentationEvent(
            P8ClientPresentationState state, PresentationEventPayload payload) {
        var transport = currentTransport(state);
        return state.preparePresentationEvent(
                transport.connection(), transport.listener(), payload);
    }

    static P8ClientPlayConnection currentTransport(P8ClientPresentationState state) {
        Objects.requireNonNull(state, "state");
        synchronized (EPOCHS) {
            return EPOCHS.computeIfAbsent(
                    state, ignored -> new Epoch(new P8ClientPlayConnection()))
                    .transport();
        }
    }

    static void closeAll() {
        final Epoch[] epochs;
        synchronized (EPOCHS) {
            epochs = EPOCHS.values().toArray(Epoch[]::new);
            EPOCHS.clear();
        }
        for (var epoch : epochs) {
            epoch.transport().close();
        }
    }

    private static Epoch epochForOpen(P8ClientPresentationState state) {
        synchronized (EPOCHS) {
            var epoch = EPOCHS.get(state);
            if (epoch == null) {
                epoch = new Epoch(new P8ClientPlayConnection());
                EPOCHS.put(state, epoch);
            } else if (epoch.published()) {
                epoch.transport().close();
                epoch = new Epoch(new P8ClientPlayConnection());
                EPOCHS.put(state, epoch);
            }
            return epoch;
        }
    }

    private static final class Epoch {
        private final P8ClientPlayConnection transport;
        private boolean published;

        private Epoch(P8ClientPlayConnection transport) {
            this.transport = transport;
        }

        private P8ClientPlayConnection transport() {
            return transport;
        }

        private boolean published() {
            return published;
        }

        private void markPublished() {
            published = true;
        }
    }
}
