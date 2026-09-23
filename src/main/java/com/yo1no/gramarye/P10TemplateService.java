package com.yo1no.gramarye;

import com.yo1no.gramarye.magic.validation.ValidationIssue;
import com.yo1no.gramarye.magic.validation.ValidationResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** One root-owned, identity-free template snapshot; never a player data owner. */
final class P10TemplateService {
    private static final AttemptStatus NO_ATTEMPT = new AttemptStatus(
            Classification.NONE, List.of(), 0, false);
    private static final AttemptStatus ACCEPTED_ATTEMPT = new AttemptStatus(
            Classification.ACCEPTED, List.of(), 0, false);
    private static final AttemptStatus INTERNAL_FAILURE_ATTEMPT = new AttemptStatus(
            Classification.INTERNAL_FAILURE, List.of(), 0, false);
    private static final Ticket DROPPED_TICKET = new Ticket();
    private final P10TemplateValidation validation;
    private Object epoch = new Object();
    private Object latestMarker;
    // Unlike publication authority this ownership is deliberately NOT reset at stop/restart.
    private Object readerOwner;
    private Staged staged;
    private P10TemplateBody active;
    private int activeRawBytes;
    private Object publicationToken;
    private Origin origin = Origin.UNAVAILABLE;
    private AttemptStatus lastAttempt = NO_ATTEMPT;
    private boolean contextCurrent;
    private MinecraftServer activeServer;
    private boolean registered;

    P10TemplateService(P10TemplateValidation validation) {
        this.validation = Objects.requireNonNull(validation, "validation");
    }

    void registerAfterP8(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        synchronized (this) {
            if (registered) {
                throw new IllegalStateException("P10 template service already registered");
            }
            registered = true;
        }
        bus.addListener(EventPriority.LOWEST, this::addReloadListener);
        bus.addListener(EventPriority.LOWEST, this::handleServerStopping);
        bus.addListener(EventPriority.LOWEST, this::handleServerStopped);
    }

    void addReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event");
        var cycle = beginCycle(Objects.requireNonNull(event.getServerResources(), "resources"));
        event.addListener(new TemplateReloadListener(this, cycle));
    }

    private synchronized Cycle beginCycle(Object resourcesIdentity) {
        if (epoch == null) {
            epoch = new Object();
        }
        latestMarker = new Object();
        return new Cycle(epoch, latestMarker, Objects.requireNonNull(resourcesIdentity));
    }

    private synchronized boolean current(Cycle cycle) {
        return epoch == cycle.epoch && latestMarker == cycle.marker;
    }

    private Ticket prepare(Cycle cycle, Function<Object, Prepared> reader) {
        final Object ownership;
        synchronized (this) {
            if (!current(cycle)) {
                return DROPPED_TICKET;
            }
            if (readerOwner != null) {
                return stage(cycle, Prepared.rejected(
                        AttemptStatus.control(Classification.OVERLAPPING_RELOAD)));
            }
            ownership = new Object();
            readerOwner = ownership;
        }
        try {
            // A stop/new registration after acquisition revokes opening as well as staging.
            synchronized (this) {
                if (!current(cycle)) {
                    return DROPPED_TICKET;
                }
            }
            // Transfer the only bounded body into the service before releasing this reader.
            // Platform futures/barriers carry only a lightweight ticket, never the body.
            return stage(cycle, Objects.requireNonNull(reader.apply(ownership), "prepared result"));
        } finally {
            synchronized (this) {
                if (readerOwner != ownership) {
                    throw new IllegalStateException("P10 reader ownership changed before terminal");
                }
                readerOwner = null;
            }
        }
    }

    private Prepared read(Cycle cycle, ResourceManager resources, Object ownership) {
        try {
            final java.io.InputStream stream;
            synchronized (this) {
                // Lookup/open linearize with marker replacement and stop. Read/parse do not
                // hold the lock, so lifecycle revocation can proceed while the body is live.
                if (!current(cycle) || readerOwner != ownership) {
                    return Prepared.droppedResult();
                }
                var resource = resources.getResource(P10TemplateCodec.RESOURCE_ID);
                if (resource.isEmpty()) {
                    return Prepared.rejected(AttemptStatus.control(Classification.MISSING_REQUIRED_TEMPLATE));
                }
                stream = resource.orElseThrow().open();
            }
            try (stream) {
                return switch (P10TemplateCodec.decode(stream)) {
                    case P10TemplateCodec.Decoded decoded ->
                            new Prepared(decoded.body(), decoded.rawBytes(), null, false);
                    case P10TemplateCodec.Rejected rejected -> Prepared.rejected(new AttemptStatus(
                            Classification.DECODE_FAILED, List.of(rejected.reason().name()), 1, false));
                };
            }
        } catch (IOException failure) {
            throw new UncheckedIOException("P10 resource read failed", failure);
        }
    }

    private synchronized Ticket stage(Cycle cycle, Prepared prepared) {
        if (!current(cycle) || prepared.dropped) {
            return DROPPED_TICKET;
        }
        var ticket = new Ticket();
        var replacement = new Staged(cycle, prepared, ticket);
        staged = replacement;
        return ticket;
    }

    private synchronized void applyTicket(Cycle cycle, Ticket ticket) {
        if (current(cycle) && staged != null
                && staged.cycle == cycle && staged.ticket == ticket) {
            staged.applied = true;
        }
    }

    void handleServerStarted(ServerStartedEvent event) {
        var server = Objects.requireNonNull(event, "event").getServer();
        requireLiveServerThread(server);
        synchronized (this) {
            if (activeServer != null) {
                throw new IllegalStateException("P10 template service already started");
            }
            activeServer = server;
            pessimisticPostInstall();
        }
        // The root has already required P8 initial activation to succeed.
        completeInstalled(server, P8ServerPresentationService.P8RootFullSyncOutcome.ACTIVATED_CURRENT);
    }

    void beginPostInstall(MinecraftServer server) {
        requireActiveServer(server);
        synchronized (this) {
            pessimisticPostInstall();
        }
    }

    private void pessimisticPostInstall() {
        origin = active == null ? Origin.UNAVAILABLE : Origin.LKG;
        contextCurrent = false;
        lastAttempt = INTERNAL_FAILURE_ATTEMPT;
    }

    void completeInstalled(
            MinecraftServer server,
            P8ServerPresentationService.P8RootFullSyncOutcome p8Outcome) {
        requireActiveServer(server);
        Objects.requireNonNull(p8Outcome, "p8Outcome");
        synchronized (this) {
            decideInstalled(server.getServerResources().managers(), p8Outcome,
                    body -> validation.validate(server, body));
        }
    }

    private void decideInstalled(Object resourcesIdentity,
            P8ServerPresentationService.P8RootFullSyncOutcome p8Outcome,
            Function<P10TemplateBody, P10TemplateValidation.Result> eligibility) {
        var pending = staged;
        boolean matching = pending != null
                && pending.applied
                && current(pending.cycle)
                && pending.cycle.resourcesIdentity == resourcesIdentity;
        // Consume/drop staged data on the installed decision, never retain rejected raw.
        staged = null;
        if (p8Outcome != P8ServerPresentationService.P8RootFullSyncOutcome.ACTIVATED_CURRENT) {
            contextCurrent = false;
            lastAttempt = AttemptStatus.control(Classification.CONTEXT_UNAVAILABLE);
            return;
        }
        contextCurrent = true;
        if (!matching) {
            lastAttempt = AttemptStatus.control(Classification.STALE_CANDIDATE);
            return;
        }
        var candidate = pending.prepared;
        if (candidate.rejection != null) {
            lastAttempt = candidate.rejection;
            return;
        }
        var result = eligibility.apply(candidate.body);
        if (result.ready()) {
            // All potentially throwing allocation precedes the atomic, non-throwing publish.
            var freshToken = new Object();
            var acceptedAttempt = ACCEPTED_ATTEMPT;
            active = candidate.body;
            activeRawBytes = candidate.rawBytes;
            publicationToken = freshToken;
            origin = Origin.CURRENT;
            lastAttempt = acceptedAttempt;
        } else {
            lastAttempt = AttemptStatus.fromValidation(result);
        }
    }

    synchronized Capture capture(MinecraftServer server) {
        requireActiveServer(server);
        return new Capture(Optional.ofNullable(active), publicationToken, origin, lastAttempt, contextCurrent);
    }

    synchronized boolean isCurrent(MinecraftServer server, Capture captured) {
        requireActiveServer(server);
        Objects.requireNonNull(captured, "captured");
        return captured.publicationToken != null && publicationToken == captured.publicationToken;
    }

    void handleServerStopping(ServerStoppingEvent event) {
        clear(Objects.requireNonNull(event, "event").getServer());
    }

    void handleServerStopped(ServerStoppedEvent event) {
        clear(Objects.requireNonNull(event, "event").getServer());
    }

    private synchronized void clear(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread()) {
            throw new IllegalStateException("P10 lifecycle requires the server thread");
        }
        if (activeServer != null && activeServer != server) {
            throw new IllegalStateException("P10 lifecycle belongs to another server");
        }
        clearState();
    }

    private void clearState() {
        epoch = null;
        latestMarker = null;
        staged = null;
        active = null;
        activeRawBytes = 0;
        publicationToken = null;
        origin = Origin.UNAVAILABLE;
        lastAttempt = NO_ATTEMPT;
        contextCurrent = false;
        activeServer = null;
        // Only the actual prepare body's finally may release readerOwner.
    }

    private void requireActiveServer(MinecraftServer server) {
        requireLiveServerThread(server);
        if (activeServer != server) {
            throw new IllegalStateException("P10 operation requires the active server");
        }
    }

    private static void requireLiveServerThread(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!server.isSameThread() || !server.isRunning() || server.isStopped()) {
            throw new IllegalStateException("P10 operation requires the running server thread");
        }
    }

    enum Origin { CURRENT, LKG, UNAVAILABLE }

    enum Classification {
        NONE, ACCEPTED, DECODE_FAILED, UNKNOWN_TYPE, MIGRATION_FAILED, FUTURE_SCHEMA,
        SEMANTIC_INVALID, CONTEXT_UNAVAILABLE, MISSING_REQUIRED_TEMPLATE,
        OVERLAPPING_RELOAD, STALE_CANDIDATE, INTERNAL_FAILURE
    }

    record Capture(Optional<P10TemplateBody> body, Object publicationToken, Origin origin,
                   AttemptStatus lastAttempt, boolean contextCurrent) {}

    record AttemptStatus(Classification classification, List<String> summaries,
                         int retainedCount, boolean upstreamTruncated) {
        AttemptStatus {
            Objects.requireNonNull(classification, "classification");
            summaries = List.copyOf(summaries);
            if (summaries.size() > 16 || retainedCount < summaries.size() || retainedCount > 1_024
                    || summaries.stream().anyMatch(value -> value.length() > 1_024)) {
                throw new IllegalArgumentException("P10 attempt diagnostics exceed bounds");
            }
        }

        int retainedButNotSummarized() {
            return retainedCount - summaries.size();
        }

        private static AttemptStatus control(Classification classification) {
            if (classification == Classification.NONE) return NO_ATTEMPT;
            if (classification == Classification.ACCEPTED) return ACCEPTED_ATTEMPT;
            if (classification == Classification.INTERNAL_FAILURE) return INTERNAL_FAILURE_ATTEMPT;
            return new AttemptStatus(classification, List.of(), 0, false);
        }

        private static AttemptStatus fromValidation(P10TemplateValidation.Result result) {
            ValidationResult report = result.report();
            return new AttemptStatus(Classification.valueOf(result.classification().name()),
                    report.issues().stream().limit(16).map(P10TemplateService::summary).toList(),
                    report.issues().size(), report.truncated());
        }
    }

    static String summary(ValidationIssue issue) {
        // Product code/path only: no raw payload, exception, metadata value, or host path.
        String safe = issue.code().toString() + " " + issue.path().render();
        safe = safe.replaceAll("[^A-Za-z0-9_ .\\[\\]-]", "?");
        return safe.length() <= 1_024 ? safe : safe.substring(0, 1_012) + " [truncated]";
    }

    private record Cycle(Object epoch, Object marker, Object resourcesIdentity) {}
    private static final class Ticket {}
    private static final class Staged {
        private final Cycle cycle;
        private final Prepared prepared;
        private final Ticket ticket;
        private boolean applied;

        private Staged(Cycle cycle, Prepared prepared, Ticket ticket) {
            this.cycle = cycle;
            this.prepared = prepared;
            this.ticket = ticket;
        }
    }
    private record Prepared(P10TemplateBody body, int rawBytes, AttemptStatus rejection, boolean dropped) {
        private static Prepared droppedResult() { return new Prepared(null, 0, null, true); }
        private static Prepared rejected(AttemptStatus rejection) {
            return new Prepared(null, 0, rejection, false);
        }
    }

    private static final class TemplateReloadListener extends SimplePreparableReloadListener<Ticket> {
        private final P10TemplateService owner;
        private final Cycle cycle;

        private TemplateReloadListener(P10TemplateService owner, Cycle cycle) {
            this.owner = owner;
            this.cycle = cycle;
        }

        @Override
        protected Ticket prepare(ResourceManager resources, ProfilerFiller profiler) {
            return owner.prepare(cycle, ownership -> owner.read(cycle, resources, ownership));
        }

        @Override
        protected void apply(Ticket ticket, ResourceManager resources, ProfilerFiller profiler) {
            owner.applyTicket(cycle, ticket);
        }
    }
}
