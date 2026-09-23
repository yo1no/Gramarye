package com.yo1no.gramarye;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Process-local negative control for one actual P8-S2 global reload. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.DEDICATED_SERVER)
final class P8S2ReloadFailureHarness {
    static final String TEST_ID = "gramarye_p8_s2_reload:"
            + "p8s2reloadgametests."
            + "globalfailureafterp8stagingpreservesactivepublication";

    private static final String MARKER_PREFIX = "GRAMARYE_P8_S2_RELOAD";
    private static final String FAILURE_MESSAGE =
            "P8-S2 test-owned failure after production reload listeners applied";
    private static final int LOCKED_NEOFORGE_RELOAD_LISTENERS = 3;
    private static final int EXPECTED_PRECEDING_RELOAD_LISTENERS =
            LOCKED_NEOFORGE_RELOAD_LISTENERS + 2;
    private static final String LOCKED_LISTENER_WRAPPER =
            "net.neoforged.neoforge.event.AddReloadListenerEvent$WrappedStateAwareListener";
    private static final Set<String> EXPECTED_PLATFORM_LISTENER_TYPES = Set.of(
            "net.neoforged.neoforge.common.loot.LootModifierManager",
            "net.neoforged.neoforge.registries.DataMapLoader",
            "net.neoforged.neoforge.common.CreativeModeTabRegistry$1");
    private static final List<String> EXPECTED_PRODUCT_LISTENER_TYPES = List.of(
            "com.yo1no.gramarye.P8ServerPresentationService$ProfileCatalogReloadListener",
            "com.yo1no.gramarye.P10TemplateService$TemplateReloadListener");
    private static final ResourceLocation PROFILE_FIXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "gramarye_p8_s2_reload",
                    "gramarye/presentation_profiles/qualification_sound.json");

    private static Phase phase = Phase.COLD;
    private static MinecraftServer activeServer;
    private static ReloadableServerResources startupResources;
    private static ReloadableServerResources failedCandidateResources;
    private static ReloadableServerResources recoveryCandidateResources;
    private static int globalSyncCount;
    private static int baselineGlobalSyncCount;
    private static int failureApplyCount;
    private static int recoveryApplyCount;
    private static P10TemplateService templateOwner;
    private static P10TemplateService.Capture startupTemplate;

    private P8S2ReloadFailureHarness() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static synchronized void onServerAboutToStart(ServerAboutToStartEvent event) {
        Objects.requireNonNull(event, "event");
        verifyPredecessorTypeControls();
        if (activeServer != null && activeServer != event.getServer()) {
            throw new IllegalStateException("P8-S2 reload harness server lifetime overlapped");
        }
        reset();
        activeServer = Objects.requireNonNull(event.getServer(), "server");
        requireIsolatedWorld(activeServer);
        phase = Phase.STARTING;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static synchronized void onServerStarted(ServerStartedEvent event) {
        requireExactServer(event.getServer());
        requirePhase(Phase.STARTING);
        requireServerThread(activeServer);
        startupResources = Objects.requireNonNull(
                activeServer.getServerResources().managers(), "startup resources");
        startupTemplate = Objects.requireNonNull(templateOwner, "observed production P10 owner")
                .capture(activeServer);
        requireCurrentTemplate(startupTemplate);
        phase = Phase.READY;
        marker("STARTUP_READY");
        marker("TEST_ID=" + TEST_ID);
    }

    /**
     * Production installs its LOWEST listener during the mod constructor; FML injects this
     * automatic subscriber after construction. SimpleReloadInstance then preserves that exact
     * same-priority registration order for apply.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static synchronized void onAddReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event");
        var resources = Objects.requireNonNull(
                event.getServerResources(), "candidate server resources");
        observeExactPredecessors(event);
        var kind = switch (phase) {
            case FAILURE_ARMED -> CycleKind.FAILURE;
            case RECOVERY_ARMED -> CycleKind.RECOVERY;
            default -> CycleKind.PASSIVE;
        };
        if (kind == CycleKind.FAILURE) {
            if (failedCandidateResources != null || resources == startupResources) {
                throw new IllegalStateException("P8-S2 failure resource identity was reused");
            }
            failedCandidateResources = resources;
        } else if (kind == CycleKind.RECOVERY) {
            if (recoveryCandidateResources != null
                    || resources == startupResources
                    || resources == failedCandidateResources) {
                throw new IllegalStateException("P8-S2 recovery resource identity was reused");
            }
            recoveryCandidateResources = resources;
        }
        event.addListener(new OrderedFailureListener(kind, resources));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static synchronized void onDatapackSync(OnDatapackSyncEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getPlayer() != null) {
            return;
        }
        var server = Objects.requireNonNull(
                event.getPlayerList().getServer(), "datapack-sync server");
        requireExactServer(server);
        requireServerThread(server);
        globalSyncCount++;
        if (phase == Phase.FAILURE_ARMED || phase == Phase.FAILURE_APPLIED) {
            throw new AssertionError(
                    "failed P8-S2 reload must not publish global datapack sync");
        }
        if (phase == Phase.RECOVERY_APPLIED) {
            if (server.getServerResources().managers() != recoveryCandidateResources) {
                throw new AssertionError(
                        "recovery sync did not publish its exact server resources");
            }
            phase = Phase.RECOVERY_SYNCHRONIZED;
            marker("RECOVERY_GLOBAL_SYNC");
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static synchronized void onServerStopping(ServerStoppingEvent event) {
        resetIfExact(event.getServer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static synchronized void onServerStopped(ServerStoppedEvent event) {
        resetIfExact(event.getServer());
    }

    static void exerciseActualReload(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        requireServerThread(server);
        armFailure(server);
        marker("NEGATIVE_BEGIN");

        var selectedPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        Throwable failure = null;
        try {
            server.reloadResources(selectedPacks).join();
        } catch (RuntimeException expected) {
            failure = unwrap(expected);
        }
        if (!(failure instanceof InjectedGlobalReloadFailure)
                || !FAILURE_MESSAGE.equals(failure.getMessage())) {
            throw new AssertionError("P8-S2 reload did not expose the exact injected failure", failure);
        }
        observeFailedReload(server);
        marker("NEGATIVE_COMPLETED_EXCEPTIONALLY");

        armRecovery(server);
        marker("RECOVERY_BEGIN");
        server.reloadResources(selectedPacks).join();
        observeSuccessfulRecovery(server);
        marker("RECOVERY_COMPLETED_SUCCESSFULLY");
        marker("ASSERTIONS_COMPLETE");
    }

    private static synchronized void armFailure(MinecraftServer server) {
        requireExactServer(server);
        requirePhase(Phase.READY);
        if (startupResources == null
                || server.getServerResources().managers() != startupResources) {
            throw new AssertionError("P8-S2 startup resource identity is not active");
        }
        baselineGlobalSyncCount = globalSyncCount;
        phase = Phase.FAILURE_ARMED;
    }

    private static synchronized void observeFailedReload(MinecraftServer server) {
        requireExactServer(server);
        requirePhase(Phase.FAILURE_APPLIED);
        if (failureApplyCount != 1
                || recoveryApplyCount != 0
                || globalSyncCount != baselineGlobalSyncCount
                || failedCandidateResources == null
                || server.getServerResources().managers() != startupResources) {
            throw new AssertionError(
                    "failed global reload changed publication or lost exact apply evidence");
        }
        requireUnchangedTemplate(server);
        phase = Phase.FAILURE_OBSERVED;
    }

    private static synchronized void armRecovery(MinecraftServer server) {
        requireExactServer(server);
        requirePhase(Phase.FAILURE_OBSERVED);
        phase = Phase.RECOVERY_ARMED;
    }

    private static synchronized void observeSuccessfulRecovery(MinecraftServer server) {
        requireExactServer(server);
        requirePhase(Phase.RECOVERY_SYNCHRONIZED);
        if (failureApplyCount != 1
                || recoveryApplyCount != 1
                || globalSyncCount != baselineGlobalSyncCount + 1
                || recoveryCandidateResources == null
                || server.getServerResources().managers() != recoveryCandidateResources) {
            throw new AssertionError(
                    "successful recovery did not publish one fresh exact resource identity");
        }
        var recovered = templateOwner.capture(server);
        requireCurrentTemplate(recovered);
        if (recovered.publicationToken() == startupTemplate.publicationToken()
                || recovered.body().orElseThrow() == startupTemplate.body().orElseThrow()) {
            throw new AssertionError("P10 recovery did not publish its freshly prepared body/token");
        }
        startupTemplate = null;
        phase = Phase.COMPLETE;
    }

    private static synchronized void predecessorsApplied(
            CycleKind kind,
            ReloadableServerResources exactResources,
            boolean fixtureVisible) {
        if (!fixtureVisible) {
            throw new IllegalStateException("P8-S2 isolated Profile fixture is unavailable");
        }
        if (kind == CycleKind.PASSIVE) {
            return;
        }
        requireServerThread(activeServer);
        requireUnchangedTemplate(activeServer);
        if (kind == CycleKind.FAILURE) {
            requirePhase(Phase.FAILURE_ARMED);
            if (exactResources != failedCandidateResources || failureApplyCount != 0) {
                throw new AssertionError("P8-S2 failure listener identity/count changed");
            }
            failureApplyCount++;
            phase = Phase.FAILURE_APPLIED;
            marker("NEGATIVE_PREDECESSORS_APPLIED");
            marker("GLOBAL_FAILURE_INJECTED");
            throw new InjectedGlobalReloadFailure();
        }
        requirePhase(Phase.RECOVERY_ARMED);
        if (exactResources != recoveryCandidateResources || recoveryApplyCount != 0) {
            throw new AssertionError("P8-S2 recovery listener identity/count changed");
        }
        recoveryApplyCount++;
        phase = Phase.RECOVERY_APPLIED;
        marker("RECOVERY_PREDECESSORS_APPLIED");
    }

    /** Locked wrapper/owner fields are observed only; no platform or product identity is set. */
    private static void observeExactPredecessors(AddReloadListenerEvent event) {
        var listeners = event.getListeners();
        if (listeners.size() != EXPECTED_PRECEDING_RELOAD_LISTENERS) {
            throw new IllegalStateException("P8-S2 requires exact locked platform -> P8 -> P10 listeners");
        }
        try {
            var listenerTypes = new ArrayList<String>(EXPECTED_PRECEDING_RELOAD_LISTENERS);
            Object templateListener = null;
            for (int index = 0; index < listeners.size(); index++) {
                var wrapper = listeners.get(index);
                if (!wrapper.getClass().getName().equals(LOCKED_LISTENER_WRAPPER)) {
                    throw new AssertionError("P8-S2 locked reload wrapper changed at " + index);
                }
                var wrapped = wrapper.getClass().getDeclaredField("wrapped");
                wrapped.setAccessible(true);
                var listener = Objects.requireNonNull(wrapped.get(wrapper), "wrapped reload listener");
                listenerTypes.add(listener.getClass().getName());
                if (index == LOCKED_NEOFORGE_RELOAD_LISTENERS + 1) {
                    templateListener = listener;
                }
            }
            requireExactPredecessorTypes(listenerTypes);
            var exactTemplateListener = Objects.requireNonNull(templateListener, "P10 listener");
            var ownerField = exactTemplateListener.getClass().getDeclaredField("owner");
            ownerField.setAccessible(true);
            var observed = P10TemplateService.class.cast(ownerField.get(exactTemplateListener));
            if (templateOwner != null && templateOwner != observed) {
                throw new AssertionError("P10 root owner identity changed across actual reloads");
            }
            templateOwner = Objects.requireNonNull(observed, "production P10 owner");
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("P8-S2 locked read-only listener observation failed", failure);
        }
    }

    private static void requireExactPredecessorTypes(List<String> listenerTypes) {
        if (listenerTypes.size() != EXPECTED_PRECEDING_RELOAD_LISTENERS) {
            throw new AssertionError("P8-S2 exact predecessor count changed");
        }
        // Locked NeoForge registers its NORMAL-priority handlers by reflection, whose method
        // order is unspecified. Their exact set precedes the explicitly ordered P8/P10 pair.
        var remainingPlatformTypes = new HashSet<>(EXPECTED_PLATFORM_LISTENER_TYPES);
        for (int index = 0; index < listenerTypes.size(); index++) {
            var listenerType = listenerTypes.get(index);
            if (index < LOCKED_NEOFORGE_RELOAD_LISTENERS) {
                if (!remainingPlatformTypes.remove(listenerType)) {
                    throw new AssertionError("P8-S2 exact platform predecessor set changed at " + index
                            + ": " + listenerType);
                }
            } else if (!EXPECTED_PRODUCT_LISTENER_TYPES.get(
                    index - LOCKED_NEOFORGE_RELOAD_LISTENERS).equals(listenerType)) {
                throw new AssertionError("P8-S2 exact predecessor order changed at " + index
                        + ": " + listenerType);
            }
        }
    }

    private static void verifyPredecessorTypeControls() {
        var localOrder = List.of(
                "net.neoforged.neoforge.common.loot.LootModifierManager",
                "net.neoforged.neoforge.registries.DataMapLoader",
                "net.neoforged.neoforge.common.CreativeModeTabRegistry$1",
                EXPECTED_PRODUCT_LISTENER_TYPES.get(0),
                EXPECTED_PRODUCT_LISTENER_TYPES.get(1));
        requireExactPredecessorTypes(localOrder);
        requireExactPredecessorTypes(List.of(
                localOrder.get(2), localOrder.get(0), localOrder.get(1),
                localOrder.get(3), localOrder.get(4)));

        var duplicate = new ArrayList<>(localOrder);
        duplicate.set(1, duplicate.get(0));
        requireRejectedPredecessorTypes(duplicate);
        requireRejectedPredecessorTypes(localOrder.subList(0, localOrder.size() - 1));
        var extra = new ArrayList<>(localOrder);
        extra.add(localOrder.get(0));
        requireRejectedPredecessorTypes(extra);
        requireRejectedPredecessorTypes(List.of(
                localOrder.get(0), localOrder.get(1), localOrder.get(2),
                localOrder.get(4), localOrder.get(3)));
    }

    private static void requireRejectedPredecessorTypes(List<String> listenerTypes) {
        try {
            requireExactPredecessorTypes(listenerTypes);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError("P8-S2 predecessor negative control was accepted");
    }

    private static void requireCurrentTemplate(P10TemplateService.Capture captured) {
        if (captured.body().isEmpty() || captured.publicationToken() == null
                || captured.origin() != P10TemplateService.Origin.CURRENT
                || !captured.contextCurrent()
                || captured.lastAttempt().classification() != P10TemplateService.Classification.ACCEPTED) {
            throw new AssertionError("P10 expected a current accepted active template");
        }
    }

    private static void requireUnchangedTemplate(MinecraftServer server) {
        var captured = templateOwner.capture(server);
        requireCurrentTemplate(captured);
        if (startupTemplate == null
                || captured.body().orElseThrow() != startupTemplate.body().orElseThrow()
                || captured.publicationToken() != startupTemplate.publicationToken()
                || captured.origin() != startupTemplate.origin()
                || captured.lastAttempt() != startupTemplate.lastAttempt()
                || captured.contextCurrent() != startupTemplate.contextCurrent()) {
            throw new AssertionError("P10 preinstall failure changed its exact active publication tuple");
        }
    }

    private static void requireIsolatedWorld(MinecraftServer server) {
        var configuredText = System.getProperty("gramarye.p8s2.gameDirectory");
        if (configuredText == null || configuredText.isBlank()) {
            throw new IllegalStateException("P8-S2 isolated game directory is not configured");
        }
        var configured = Path.of(configuredText).toAbsolutePath().normalize();
        var world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!world.startsWith(configured)
                || configured.getFileName() == null
                || !"reload-negative-world".equals(configured.getFileName().toString())
                || (Files.exists(configured, LinkOption.NOFOLLOW_LINKS)
                        && Files.isSymbolicLink(configured))) {
            throw new IllegalStateException("P8-S2 GameTest escaped its isolated world root");
        }
    }

    private static synchronized void requireExactServer(MinecraftServer server) {
        if (activeServer != Objects.requireNonNull(server, "server")) {
            throw new IllegalStateException("P8-S2 reload harness server identity changed");
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (server == null || !server.isSameThread()) {
            throw new IllegalStateException("P8-S2 reload harness requires the server thread");
        }
    }

    private static void requirePhase(Phase expected) {
        if (phase != expected) {
            throw new IllegalStateException(
                    "P8-S2 reload phase expected " + expected + " but was " + phase);
        }
    }

    private static Throwable unwrap(Throwable failure) {
        var current = Objects.requireNonNull(failure, "failure");
        while ((current instanceof CompletionException
                        || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void marker(String value) {
        Gramarye.LOGGER.info("{} {}", MARKER_PREFIX, value);
    }

    private static synchronized void resetIfExact(MinecraftServer server) {
        if (activeServer == server) {
            reset();
            templateOwner = null;
        }
    }

    private static void reset() {
        phase = Phase.COLD;
        activeServer = null;
        startupResources = null;
        failedCandidateResources = null;
        recoveryCandidateResources = null;
        globalSyncCount = 0;
        baselineGlobalSyncCount = 0;
        failureApplyCount = 0;
        recoveryApplyCount = 0;
        startupTemplate = null;
    }

    private enum Phase {
        COLD,
        STARTING,
        READY,
        FAILURE_ARMED,
        FAILURE_APPLIED,
        FAILURE_OBSERVED,
        RECOVERY_ARMED,
        RECOVERY_APPLIED,
        RECOVERY_SYNCHRONIZED,
        COMPLETE
    }

    private enum CycleKind {
        PASSIVE,
        FAILURE,
        RECOVERY
    }

    private record PreparedProbe(boolean fixtureVisible) {}

    private static final class OrderedFailureListener
            extends SimplePreparableReloadListener<PreparedProbe> {
        private final CycleKind kind;
        private final ReloadableServerResources exactResources;

        private OrderedFailureListener(
                CycleKind kind, ReloadableServerResources exactResources) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.exactResources = Objects.requireNonNull(exactResources, "exact resources");
        }

        @Override
        protected PreparedProbe prepare(
                ResourceManager resources, ProfilerFiller profiler) {
            Objects.requireNonNull(resources, "resources");
            Objects.requireNonNull(profiler, "profiler");
            return new PreparedProbe(resources.getResource(PROFILE_FIXTURE).isPresent());
        }

        @Override
        protected void apply(
                PreparedProbe prepared,
                ResourceManager resources,
                ProfilerFiller profiler) {
            Objects.requireNonNull(resources, "resources");
            Objects.requireNonNull(profiler, "profiler");
            predecessorsApplied(kind, exactResources,
                    Objects.requireNonNull(prepared, "prepared").fixtureVisible());
        }
    }

    private static final class InjectedGlobalReloadFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private InjectedGlobalReloadFailure() {
            super(FAILURE_MESSAGE);
        }
    }
}
