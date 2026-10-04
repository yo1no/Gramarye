package com.yo1no.gramarye;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.electronwill.nightconfig.core.CommentedConfig;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.neoforged.bus.api.BusBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforgespi.language.IConfigurable;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.language.IModLanguageLoader;
import net.neoforged.neoforgespi.locating.ForgeFeature;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.Test;

/** Platform config handoff plus isolated real foundation owners, never a fake MinecraftServer. */
final class P11FoundationBoundaryTest {
    private static final Path ROOT = projectRoot();
    private static final Path JAVA_ROOT = ROOT.resolve("src/main/java/com/yo1no/gramarye");

    @Test
    void platformLoadedConfigToSnapshotToSlotKeepsFrozenLimitsThroughReloadAndUnload() {
        var container = new ConfigContainer();
        var config = new P5ServerRuntimeConfig(container.getEventBus(), container);
        var raw = P11StartupConfigurationTest.diagnosticRawProfile();
        // Uses the permitted platform LoadedConfig producer and actual ModConfig.setConfig;
        // this is config transport plumbing, not a native server-start qualification claim.
        ConfigTracker.acceptSyncedConfig(container.registered, toml(raw));
        assertNotNull(container.registered.getLoadedConfig());
        assertEquals(1, container.registrations);
        assertSame(ModConfig.Type.SERVER, container.registered.getType());
        assertEquals(P5ServerRuntimeConfig.CONFIG_FILE_NAME, container.registered.getFileName());
        var snapshot = config.snapshotAllForStarted();
        var limits = assertInstanceOf(P11StartupLoadState.Ready.class, snapshot.p11State()).limits();
        var identities = P11IdentityOwner.isolatedModel(limits.maxUuids());
        var slot = new P11FoundationSlot(limits, identities);
        var actor = identities.modelActor(new UUID(1L, 2L), 7);
        var connection = identities.modelConnection();
        var captured = identities.bindModel(actor, connection).orElseThrow();

        assertSame(limits, slot.limits());
        assertFalse(slot.retired());
        assertTrue(identities.current(captured));
        assertThrows(IllegalStateException.class, () -> new P11ReceiptLedger(identities),
                "the slot has already installed the sole receipt owner");

        raw.set("p11.retention.maxUuids", 5);
        ConfigTracker.acceptSyncedConfig(container.registered, toml(raw));
        container.getEventBus().post(new ModConfigEvent.Reloading(container.registered));
        assertEquals(5, assertInstanceOf(P11StartupLoadState.Ready.class,
                config.snapshotAllForStarted().p11State()).limits().maxUuids());
        assertEquals(4, slot.limits().maxUuids());
        assertTrue(identities.current(captured));

        raw.set("p11.control.tryBurst", 0);
        ConfigTracker.acceptSyncedConfig(container.registered, toml(raw));
        var invalid = config.snapshotAllForStarted();
        assertInstanceOf(P11StartupLoadState.Invalid.class, invalid.p11State());
        assertEquals(4_096, invalid.p5Limits().pendingEventsPerServer());
        assertEquals(4, slot.limits().maxUuids());
        assertFalse(slot.retired());
        assertTrue(identities.current(captured));

        container.tracker.unloadConfigs(ModConfig.Type.SERVER);
        assertThrows(P5RuntimeConfigurationException.class, config::snapshotAllForStarted);
        assertFalse(slot.retired(), "config unload must not retire the running slot");
        assertTrue(identities.current(captured));
        slot.retire();
        slot.retire();
        assertTrue(slot.retired());
        assertFalse(identities.current(captured));
        assertFalse(captured.currentBinding());
        assertEquals(0, identities.retainedBindings());
        assertEquals(0, identities.retainedAccounts());
    }

    @Test
    void actualPlatformP5OnlyConfigRemainsPlayableWhileP11IsExplicitlyInvalid() {
        var container = new ConfigContainer();
        var config = new P5ServerRuntimeConfig(container.getEventBus(), container);
        var raw = CommentedConfig.inMemory();
        container.registered.getSpec().correct(raw);
        ConfigTracker.acceptSyncedConfig(container.registered, toml(raw));
        var snapshot = config.snapshotAllForStarted();
        assertEquals(4_096, snapshot.p5Limits().pendingEventsPerServer());
        var invalid = assertInstanceOf(P11StartupLoadState.Invalid.class, snapshot.p11State());
        assertEquals(P11ConfigurationFailureReason.MISSING_REQUIRED_VALUE, invalid.failure().reason());
        assertEquals(P11ConfigurationKey.MAX_UUIDS, invalid.failure().key());
        assertFalse(container.registered.getLoadedConfig().config().contains("p11"));
        container.tracker.unloadConfigs(ModConfig.Type.SERVER);
    }

    @Test
    void rootSamplesOneCombinedCandidateAndRegistersOnlyFoundationStartStop() throws Exception {
        var root = Files.readString(JAVA_ROOT.resolve("Gramarye.java"));
        assertEquals(List.of(
                "private final P11FoundationService p11FoundationService;",
                "p11FoundationService = new P11FoundationService(p11SourceProvenance, playerSkillAttachmentService);",
                "NeoForge.EVENT_BUS.addListener(p11FoundationService::tick);",
                "p11FoundationService);",
                "p11FoundationService.bindRuntime(skillRuntimeService);",
                "NeoForge.EVENT_BUS.addListener(p11FoundationService::stopping);",
                "NeoForge.EVENT_BUS.addListener(p11FoundationService::stopped);",
                "p11FoundationService.started(event, snapshot.p11State());"),
                root.lines().map(String::strip)
                        .filter(line -> line.contains("p11FoundationService")).toList());
        var start = root.substring(root.indexOf("private void handleP5RuntimeStarted("),
                root.indexOf("/** Returns the controlled server skill subsystem port"));
        assertOrdered(start,
                "var snapshot = p5ServerRuntimeConfig.snapshotAllForStarted();",
                "var limits = snapshot.p5Limits();",
                "p11FoundationService.started(event, snapshot.p11State());",
                "skillRuntimeService.handleRuntimeStarted(event, limits);");
        assertEquals(1, start.split("snapshotAllForStarted\\(", -1).length - 1);
        assertFalse(start.contains("catch ("));
        assertTrue(root.contains("modBus.addListener(P11TransitionPayloadRegistrar::register);"));
        assertTrue(root.contains("modBus.addListener(P11ConfigurationBoundary::registerTasks);"));

        var service = Files.readString(JAVA_ROOT.resolve("P11FoundationService.java"));
        assertOrdered(service, "startupState = snapshot;",
                "if (snapshot instanceof P11StartupLoadState.Ready ready)",
                "slot = new P11FoundationSlot(ready.limits(),",
                "new P11IdentityOwner(exact, ready.limits().maxUuids(),",
                "Math.addExact((long) exact.getMaxPlayers(), ready.limits().maxWaitingConnections())");
        assertOrdered(service, "slot.sources = new P11QualifiedSourceOwner(",
                "transitions = new P11LiveTransitionService(exact, ready.limits(), slot.identities, slot.sources);");
        assertTrue(service.contains("server == exact"));
        assertTrue(service.contains("if (server != exact) { return; }"));
        assertOrdered(service, "terminalSummary = slot.sources.retire(nativeStopNormal);",
                "provenance.stopped(exact);", "slot.retire();", "slot = null;", "server = null;");
        var stopping = service.substring(service.indexOf("void stopping("), service.indexOf("void stopped("));
        assertTrue(stopping.contains("source.stopping()"));
        assertOrdered(stopping, "control.stop()", "source.stopping()");
        assertFalse(stopping.contains("retire("));
        assertTrue(service.contains("P11_START_WRONG_THREAD"));
        assertTrue(service.contains("P11_STOP_WRONG_THREAD"));
        var rawOwner = Files.readString(JAVA_ROOT.resolve("P5ServerRuntimeConfig.java"));
        assertFalse(rawOwner.contains("P11FoundationService"));
        assertFalse(rawOwner.contains("P11FoundationSlot"));
        assertFalse(rawOwner.contains(".retire()"));
    }

    @Test
    void newCommonFoundationHasNoTransportWriterClientOrGameplayRegistration() throws Exception {
        var clientOnly = Set.of("P11ClientTransitions.java", "P11ClientTransitionScreen.java",
                "P11ClientLeaveScreen.java");
        var observedClient = new java.util.HashSet<String>();
        try (var sources = Files.list(JAVA_ROOT)) {
            for (var path : sources.filter(path -> path.getFileName().toString().startsWith("P11"))
                    .filter(path -> path.toString().endsWith(".java")).toList()) {
                var source = Files.readString(path);
                var name = path.getFileName().toString();
                if (source.contains("import net.minecraft.client.")) { observedClient.add(name); }
                assertAll(path.getFileName().toString(),
                        () -> assertEquals(clientOnly.contains(name), source.contains("import net.minecraft.client.")),
                        () -> assertEquals(name.equals("P11TransitionPayloadRegistrar.java"),
                                source.contains("RegisterPayloadHandlersEvent")),
                        () -> assertEquals(name.equals("P11ConfigurationBoundary.java"),
                                source.contains("RegisterConfigurationTasksEvent")),
                        () -> assertEquals(name.equals("P11ClientTransitions.java"), source.contains(".addListener(")),
                        () -> assertFalse(source.contains("@Mixin")),
                        () -> assertFalse(source.contains("@Inject")),
                        () -> assertFalse(source.contains("admitAuthenticatedPlayerCast(")),
                        () -> assertFalse(source.contains("P7ServerAuthorizationBoundary.install(")),
                        () -> assertFalse(source.contains("java.lang.reflect")),
                        () -> assertFalse(source.contains("sun.misc.Unsafe")));
            }
        }
        assertEquals(clientOnly, observedClient);
        var registrar = Files.readString(JAVA_ROOT.resolve("P11TransitionPayloadRegistrar.java"));
        assertTrue(registrar.contains("HandlerThread.NETWORK).commonToServer("));
        assertTrue(registrar.contains("HandlerThread.MAIN).commonToClient("));
        assertFalse(registrar.contains(".optional("));
        assertFalse(registrar.contains("context.player("));
        var dispatch = Files.readString(JAVA_ROOT.resolve("P11ClientTransitionDispatch.java"));
        for (var client : clientOnly) {
            assertFalse(dispatch.contains(client.replace(".java", "")));
        }
    }

    private static byte[] toml(CommentedConfig raw) {
        var text = new StringBuilder();
        for (var key : P5RuntimeLimitKey.values()) {
            var path = P5RawServerConfigSpec.rawPath(key);
            text.append(path).append(" = ").append(raw.getRaw(path).toString()).append('\n');
        }
        for (var key : P11ConfigurationKey.values()) {
            if (raw.contains(key.rawPath())) {
                text.append(key.rawPath()).append(" = ")
                        .append(raw.getRaw(key.rawPath()).toString()).append('\n');
            }
        }
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void assertOrdered(String source, String... fragments) {
        int previous = -1;
        for (var fragment : fragments) {
            int position = source.indexOf(fragment, previous + 1);
            assertTrue(position > previous, fragment);
            previous = position;
        }
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) { throw new IllegalStateException("project root not found"); }
        return current;
    }

    private static final class ConfigContainer extends ModContainer {
        private final IEventBus bus = BusBuilder.builder().build();
        private final ConfigTracker tracker = new ConfigTracker();
        private ModConfig registered;
        private int registrations;

        ConfigContainer() { super(new StubInfo()); }
        @Override public IEventBus getEventBus() { return bus; }
        @Override public void registerConfig(ModConfig.Type type, IConfigSpec spec, String fileName) {
            registrations++;
            registered = tracker.registerConfig(type, spec, this, fileName);
        }
    }

    private record StubInfo() implements IModInfo {
        @Override public IModFileInfo getOwningFile() { return null; }
        @Override public IModLanguageLoader getLoader() { return null; }
        @Override public String getModId() { return Gramarye.MOD_ID; }
        @Override public String getDisplayName() { return Gramarye.MOD_ID; }
        @Override public String getDescription() { return "P11 platform configuration boundary"; }
        @Override public ArtifactVersion getVersion() { return new DefaultArtifactVersion("1"); }
        @Override public List<? extends ModVersion> getDependencies() { return List.of(); }
        @Override public List<? extends ForgeFeature.Bound> getForgeFeatures() { return List.of(); }
        @Override public String getNamespace() { return Gramarye.DATA_NAMESPACE; }
        @Override public Map<String, Object> getModProperties() { return Map.of(); }
        @Override public Optional<URL> getUpdateURL() { return Optional.empty(); }
        @Override public Optional<URL> getModURL() { return Optional.empty(); }
        @Override public Optional<String> getLogoFile() { return Optional.empty(); }
        @Override public boolean getLogoBlur() { return false; }
        @Override public IConfigurable getConfig() { return EmptyConfigurable.INSTANCE; }
    }

    private enum EmptyConfigurable implements IConfigurable {
        INSTANCE;
        @Override public <T> Optional<T> getConfigElement(String... key) { return Optional.empty(); }
        @Override public List<? extends IConfigurable> getConfigList(String... key) { return List.of(); }
    }
}
