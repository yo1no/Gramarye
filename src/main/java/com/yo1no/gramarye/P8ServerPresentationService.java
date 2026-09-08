package com.yo1no.gramarye;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.LazilyParsedNumber;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.mojang.serialization.JsonOps;
import com.yo1no.gramarye.magic.api.registry.MagicRegistries;
import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailability;
import com.yo1no.gramarye.magic.definition.validation.ProfileAvailabilityView;
import com.yo1no.gramarye.magic.limits.MagicPolicyLimits;
import com.yo1no.gramarye.magic.presentation.api.ProfileChannel;
import com.yo1no.gramarye.magic.presentation.api.ProfileConfiguration;
import com.yo1no.gramarye.magic.presentation.api.ProfileCost;
import com.yo1no.gramarye.magic.presentation.api.ProfileType;
import com.yo1no.gramarye.magic.validation.ValidationContext;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Root-owned P8 server catalog, reload, generation, and availability authority. */
final class P8ServerPresentationService {
    private static final String PROFILE_DIRECTORY = "gramarye/presentation_profiles";
    private static final FileToIdConverter PROFILE_FILES =
            FileToIdConverter.json(PROFILE_DIRECTORY);
    private static final Gson CANONICAL_JSON =
            new GsonBuilder().disableHtmlEscaping().create();
    private static final ValidationContext PROFILE_VALIDATION_CONTEXT =
            new ValidationContext(MagicPolicyLimits.DEFAULTS);

    private static final ResourceLocation SOUND_TYPE_ID = id("sound");
    private static final ResourceLocation PARTICLE_TYPE_ID = id("particle");
    private static final ResourceLocation TRAIL_TYPE_ID = id("trail");

    private static final String DEFAULT_SOUND_CONFIGURATION =
            "{\"sound\":\"minecraft:entity.experience_orb.pickup\","
                    + "\"volume_milli\":600,\"pitch_milli\":1000}";
    private static final String DEFAULT_PARTICLE_CONFIGURATION =
            "{\"particle\":\"minecraft:enchant\",\"count\":8,"
                    + "\"speed_milli_blocks\":50,\"size_milli_blocks\":250,"
                    + "\"lifetime_ticks\":20}";
    private static final String DEFAULT_TRAIL_CONFIGURATION =
            "{\"particle\":\"minecraft:enchant\",\"segments\":8,"
                    + "\"sample_interval_ticks\":2,\"size_milli_blocks\":200,"
                    + "\"lifetime_ticks\":16}";

    private final ProfileAvailabilityView availabilityView = this::availability;

    private volatile CatalogSnapshot activeSnapshot;
    private MinecraftServer activeServer;
    private PendingCandidate pendingCandidate;
    private ReloadMarker currentReloadMarker;
    private long catalogGenerationHighWater;
    private boolean registered;

    private P8ServerPresentationService() {}

    static P8ServerPresentationService create() {
        return new P8ServerPresentationService();
    }

    ProfileAvailabilityView profileAvailabilityView() {
        return availabilityView;
    }

    Optional<AppearanceResolutionSnapshot> captureAppearanceResolutionSnapshot() {
        var captured = activeSnapshot;
        return captured == null
                ? Optional.empty()
                : Optional.of(new AppearanceResolutionSnapshot(captured));
    }

    static ReloadIdentity newReloadIdentityForTesting() {
        return new ReloadIdentity();
    }

    static PreparedCatalog loadCandidateForTesting(
            Registry<ProfileType<?>> registry,
            Map<ResourceLocation, byte[]> profileSources) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(profileSources, "profileSources");
        if (profileSources.size() > PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES + 1) {
            throw new IllegalArgumentException(
                    "test Profile source input exceeds the bounded discovery probe");
        }
        var ordered = new TreeMap<ResourceLocation, byte[]>();
        profileSources.forEach((id, source) -> ordered.put(
                Objects.requireNonNull(id, "profile ID"),
                Objects.requireNonNull(source, "profile source").clone()));
        return loadTestCatalog(registry, ordered);
    }

    void stageCandidateForTesting(ReloadIdentity identity, PreparedCatalog prepared) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(prepared, "prepared");
        var cycle = new ReloadCycle(identity);
        synchronized (this) {
            currentReloadMarker = cycle.marker;
            pendingCandidate = new PendingCandidate(cycle, prepared);
        }
    }

    void beginReloadCycleForTesting(ReloadIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        synchronized (this) {
            currentReloadMarker = identity.marker;
        }
    }

    void completeReloadCycleForTesting(ReloadIdentity identity, PreparedCatalog prepared) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(prepared, "prepared");
        stageCandidate(new ReloadCycle(identity), prepared);
    }

    boolean activateCandidateForTesting(ReloadIdentity identity) {
        Objects.requireNonNull(identity, "identity");
        synchronized (this) {
            return activateMatchingCandidate(identity);
        }
    }

    void stopForTesting() {
        synchronized (this) {
            activeSnapshot = null;
            pendingCandidate = null;
            currentReloadMarker = null;
            activeServer = null;
            catalogGenerationHighWater = 0L;
        }
    }

    void setCatalogGenerationHighWaterForTesting(long generation) {
        if (generation < 0) {
            throw new IllegalArgumentException("catalog generation high-water must be non-negative");
        }
        synchronized (this) {
            if (activeSnapshot != null && generation == 0) {
                throw new IllegalArgumentException(
                        "an active catalog requires a positive generation");
            }
            catalogGenerationHighWater = generation;
            if (activeSnapshot != null) {
                activeSnapshot = activeSnapshot.withGeneration(generation);
            }
        }
    }

    long catalogGenerationForTesting() {
        synchronized (this) {
            return catalogGenerationHighWater;
        }
    }

    int activeEntryCountForTesting() {
        var snapshot = activeSnapshot;
        return snapshot == null ? 0 : snapshot.entries.size();
    }

    int pendingEntryCountForTesting() {
        synchronized (this) {
            return pendingCandidate == null ? 0 : pendingCandidate.prepared.entries.size();
        }
    }

    int activeDiagnosticCountForTesting() {
        var snapshot = activeSnapshot;
        return snapshot == null ? 0 : snapshot.diagnostics.size();
    }

    long activeSuppressedDiagnosticCountForTesting() {
        var snapshot = activeSnapshot;
        return snapshot == null ? 0L : snapshot.suppressedDiagnostics;
    }

    int activeCatalogBodyBytesForTesting() {
        var snapshot = activeSnapshot;
        return snapshot == null ? 0 : snapshot.wireBodyBytes;
    }

    Optional<ProfileChannel> activeChannelForTesting(ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        var snapshot = activeSnapshot;
        return snapshot == null
                ? Optional.empty()
                : snapshot.entry(profileId).map(ProfileEntry::channel);
    }

    Optional<ProfileConfiguration> activeConfigurationForTesting(
            ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        var snapshot = activeSnapshot;
        return snapshot == null
                ? Optional.empty()
                : snapshot.entry(profileId).map(ProfileEntry::configuration);
    }

    Optional<ProfileType<?>> activeTypeForTesting(ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        var snapshot = activeSnapshot;
        return snapshot == null
                ? Optional.empty()
                : snapshot.entry(profileId).map(ProfileEntry::type);
    }

    Optional<byte[]> activeCanonicalConfigurationBytesForTesting(
            ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        var snapshot = activeSnapshot;
        return snapshot == null
                ? Optional.empty()
                : snapshot.entry(profileId)
                        .map(entry -> entry.canonicalConfigurationJson()
                                .getBytes(StandardCharsets.UTF_8));
    }

    OptionalInt activeEnvelopeBytesForTesting(ResourceLocation profileId) {
        Objects.requireNonNull(profileId, "profileId");
        var snapshot = activeSnapshot;
        if (snapshot == null) {
            return OptionalInt.empty();
        }
        var entry = snapshot.entry(profileId);
        return entry.isEmpty()
                ? OptionalInt.empty()
                : OptionalInt.of(entry.orElseThrow().envelopeBytes());
    }

    void registerAfterP5(IEventBus gameBus) {
        Objects.requireNonNull(gameBus, "gameBus");
        synchronized (this) {
            if (registered) {
                throw new IllegalStateException("P8 server presentation service already registered");
            }
            registered = true;
        }
        gameBus.addListener(EventPriority.LOWEST, this::addReloadListener);
        gameBus.addListener(EventPriority.LOWEST, this::handleDatapackSync);
        gameBus.addListener(EventPriority.LOWEST, this::handleServerStopping);
        gameBus.addListener(EventPriority.LOWEST, this::handleServerStopped);
    }

    void handleServerStarted(ServerStartedEvent event) {
        Objects.requireNonNull(event, "event");
        var server = Objects.requireNonNull(event.getServer(), "server");
        requireServerThread(server);
        if (!server.isRunning() || server.isStopped()) {
            throw new IllegalStateException("P8 catalog cannot start on a stopped server");
        }

        synchronized (this) {
            if (activeServer != null) {
                throw new IllegalStateException("P8 catalog already has an active server");
            }
            activeServer = server;
            activeSnapshot = null;
            catalogGenerationHighWater = 0L;
            if (!activateMatchingCandidate(new ReloadIdentity(
                    server.getServerResources().managers()))) {
                activeServer = null;
                activeSnapshot = null;
                pendingCandidate = null;
                currentReloadMarker = null;
                catalogGenerationHighWater = 0L;
                throw new IllegalStateException("P8 initial Profile catalog is unavailable");
            }
        }
    }

    void addReloadListener(AddReloadListenerEvent event) {
        Objects.requireNonNull(event, "event");
        var cycle = new ReloadCycle(new ReloadIdentity(
                Objects.requireNonNull(event.getServerResources(), "server resources")));
        synchronized (this) {
            currentReloadMarker = cycle.marker;
        }
        event.addListener(new ProfileCatalogReloadListener(this, cycle));
    }

    void handleDatapackSync(OnDatapackSyncEvent event) {
        Objects.requireNonNull(event, "event");
        if (event.getPlayer() != null) {
            return;
        }
        var server = Objects.requireNonNull(
                event.getPlayerList().getServer(), "datapack-sync server");
        requireServerThread(server);
        synchronized (this) {
            if (activeServer != server) {
                return;
            }
            activateMatchingCandidate(new ReloadIdentity(
                    server.getServerResources().managers()));
        }
    }

    void handleServerStopping(ServerStoppingEvent event) {
        Objects.requireNonNull(event, "event");
        clearServer(event.getServer());
    }

    void handleServerStopped(ServerStoppedEvent event) {
        Objects.requireNonNull(event, "event");
        clearServer(event.getServer());
    }

    private ProfileAvailability availability(
            AppearanceField field, ResourceLocation profileId) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(profileId, "profileId");
        var channel = switch (field) {
            case SOUND_PROFILE -> ProfileChannel.SOUND;
            case PARTICLE_PROFILE -> ProfileChannel.PARTICLE;
            case TRAIL_PROFILE -> ProfileChannel.TRAIL;
            case PRIMARY_ARGB, SECONDARY_ARGB, INTENSITY_MILLI ->
                    throw new IllegalArgumentException(
                            "Profile availability requires a Profile Appearance field");
        };
        var snapshot = activeSnapshot;
        if (snapshot == null) {
            return ProfileAvailability.UNKNOWN;
        }
        return snapshot.contains(channel, profileId)
                ? ProfileAvailability.AVAILABLE
                : ProfileAvailability.MISSING;
    }

    private void stageCandidate(ReloadCycle cycle, PreparedCatalog prepared) {
        Objects.requireNonNull(cycle, "cycle");
        Objects.requireNonNull(prepared, "prepared");
        synchronized (this) {
            if (currentReloadMarker != cycle.marker) {
                return;
            }
            pendingCandidate = new PendingCandidate(cycle, prepared);
        }
    }

    private boolean activateMatchingCandidate(ReloadIdentity exactToken) {
        var pending = pendingCandidate;
        if (pending == null
                || currentReloadMarker != pending.cycle.marker
                || !pending.cycle.identity.matches(exactToken)) {
            return false;
        }
        if (catalogGenerationHighWater == Long.MAX_VALUE) {
            releasePending(pending);
            return false;
        }
        var nextGeneration = catalogGenerationHighWater + 1L;
        var nextSnapshot = new CatalogSnapshot(nextGeneration, pending.prepared);
        activeSnapshot = nextSnapshot;
        catalogGenerationHighWater = nextGeneration;
        releasePending(pending);
        Gramarye.LOGGER.info(
                "Gramarye P8 Profile catalog activated at generation {}", nextGeneration);
        return true;
    }

    private void releasePending(PendingCandidate expected) {
        if (pendingCandidate == expected) {
            pendingCandidate = null;
        }
        if (currentReloadMarker == expected.cycle.marker) {
            currentReloadMarker = null;
        }
    }

    private void clearServer(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        synchronized (this) {
            if (activeServer != server) {
                return;
            }
            requireServerThread(server);
            activeSnapshot = null;
            pendingCandidate = null;
            currentReloadMarker = null;
            activeServer = null;
            catalogGenerationHighWater = 0L;
        }
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!server.isSameThread()) {
            throw new IllegalStateException("P8 catalog lifecycle requires the server thread");
        }
    }

    private static PreparedCatalog loadCatalog(ResourceManager resources) {
        Objects.requireNonNull(resources, "resources");
        var diagnostics = new DiagnosticCollector();
        var parseWork = new ParseWork();
        var registry = MagicRegistries.profileTypeRegistry();
        var defaults = builtInDefaults(registry, diagnostics);
        var ordinary = new TreeMap<ResourceLocation, ProfileEntry>();

        var discovered = PROFILE_FILES.listMatchingResources(resources);
        var iterator = discovered.entrySet().iterator();
        var inspected = 0;
        ResourceLocation previousResourceId = null;
        while (iterator.hasNext()
                && inspected < PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES) {
            var resourceEntry = iterator.next();
            if (previousResourceId != null
                    && previousResourceId.toString().compareTo(
                                    resourceEntry.getKey().toString())
                            >= 0) {
                throw new IllegalStateException(
                        "P8 Profile resource enumeration is not strictly ascending");
            }
            previousResourceId = resourceEntry.getKey();
            inspected++;
            ResourceLocation profileId;
            try {
                profileId = PROFILE_FILES.fileToId(resourceEntry.getKey());
            } catch (IllegalArgumentException ignored) {
                diagnostics.record(DiagnosticCode.INVALID_PROFILE_ID);
                continue;
            }
            if (!validResourceLocation(profileId)) {
                diagnostics.record(DiagnosticCode.INVALID_PROFILE_ID);
                continue;
            }
            if (isReservedDefault(profileId)) {
                diagnostics.record(DiagnosticCode.RESERVED_DEFAULT_COLLISION, profileId);
                continue;
            }

            byte[] source;
            try {
                source = readBounded(resourceEntry.getValue(), parseWork);
            } catch (IOException exception) {
                throw new IllegalStateException("P8 Profile resource read failed");
            }
            if (source == null) {
                diagnostics.record(DiagnosticCode.RAW_SOURCE_LIMIT, profileId);
                continue;
            }

            Optional<ProfileEntry> decoded;
            try {
                decoded = decodeResource(profileId, source, registry, diagnostics, parseWork);
            } catch (EntryRejectedException ignored) {
                diagnostics.record(DiagnosticCode.INVALID_PROFILE_ENTRY, profileId);
                continue;
            }
            decoded.ifPresent(entry -> ordinary.put(profileId, entry));
        }
        if (iterator.hasNext()) {
            diagnostics.record(DiagnosticCode.DISCOVERY_LIMIT);
        }

        return completeCatalog(defaults, ordinary, diagnostics);
    }

    private static PreparedCatalog loadTestCatalog(
            Registry<ProfileType<?>> registry,
            NavigableMap<ResourceLocation, byte[]> profileSources) {
        var diagnostics = new DiagnosticCollector();
        var parseWork = new ParseWork();
        var defaults = builtInDefaults(registry, diagnostics);
        var ordinary = new TreeMap<ResourceLocation, ProfileEntry>();
        var inspected = 0;
        for (var sourceEntry : profileSources.entrySet()) {
            if (inspected == PresentationLimits.MAX_PROFILE_DISCOVERED_RESOURCES) {
                diagnostics.record(DiagnosticCode.DISCOVERY_LIMIT);
                break;
            }
            inspected++;
            var profileId = sourceEntry.getKey();
            if (!validResourceLocation(profileId)) {
                diagnostics.record(DiagnosticCode.INVALID_PROFILE_ID);
                continue;
            }
            if (isReservedDefault(profileId)) {
                diagnostics.record(DiagnosticCode.RESERVED_DEFAULT_COLLISION, profileId);
                continue;
            }
            var source = boundedTestSource(sourceEntry.getValue(), parseWork);
            if (source == null) {
                diagnostics.record(DiagnosticCode.RAW_SOURCE_LIMIT, profileId);
                continue;
            }
            try {
                decodeResource(profileId, source, registry, diagnostics, parseWork)
                        .ifPresent(entry -> ordinary.put(profileId, entry));
            } catch (EntryRejectedException ignored) {
                diagnostics.record(DiagnosticCode.INVALID_PROFILE_ENTRY, profileId);
            }
        }

        return completeCatalog(defaults, ordinary, diagnostics);
    }

    private static PreparedCatalog completeCatalog(
            List<ProfileEntry> defaults,
            NavigableMap<ResourceLocation, ProfileEntry> ordinary,
            DiagnosticCollector diagnostics) {
        var retained = new TreeMap<ResourceLocation, ProfileEntry>();
        var channelCounts = new EnumMap<ProfileChannel, Integer>(ProfileChannel.class);
        for (var channel : ProfileChannel.values()) {
            channelCounts.put(channel, 0);
        }
        for (var entry : defaults) {
            retainRequired(entry, retained, channelCounts);
        }
        for (var entry : ordinary.values()) {
            var channelCount = channelCounts.get(entry.channel());
            if (retained.size() == PresentationLimits.MAX_PROFILE_INSTANCES
                    || channelCount
                            == PresentationLimits.MAX_PROFILE_INSTANCES_PER_CHANNEL) {
                diagnostics.record(DiagnosticCode.CAPACITY_LIMIT, entry.id());
                continue;
            }
            retained.put(entry.id(), entry);
            channelCounts.put(entry.channel(), channelCount + 1);
        }
        return new PreparedCatalog(retained, diagnostics.snapshot(), diagnostics.suppressed());
    }

    private static byte[] boundedTestSource(byte[] source, ParseWork parseWork) {
        var inspectedLength = Math.min(
                source.length, PresentationLimits.MAX_PROFILE_INSPECTED_SOURCE_BYTES);
        parseWork.addInspectedBytes(inspectedLength);
        if (source.length > PresentationLimits.MAX_PROFILE_RAW_SOURCE_BYTES) {
            return null;
        }
        parseWork.addAcceptedBytes(source.length);
        return source.clone();
    }

    private static List<ProfileEntry> builtInDefaults(
            Registry<ProfileType<?>> registry, DiagnosticCollector diagnostics) {
        var defaults = new ArrayList<ProfileEntry>(3);
        defaults.add(requiredDefault(
                AppearanceSemantics.DEFAULT_SOUND_PROFILE,
                SOUND_TYPE_ID,
                ProfileChannel.SOUND,
                DEFAULT_SOUND_CONFIGURATION,
                registry,
                diagnostics));
        defaults.add(requiredDefault(
                AppearanceSemantics.DEFAULT_PARTICLE_PROFILE,
                PARTICLE_TYPE_ID,
                ProfileChannel.PARTICLE,
                DEFAULT_PARTICLE_CONFIGURATION,
                registry,
                diagnostics));
        defaults.add(requiredDefault(
                AppearanceSemantics.DEFAULT_TRAIL_PROFILE,
                TRAIL_TYPE_ID,
                ProfileChannel.TRAIL,
                DEFAULT_TRAIL_CONFIGURATION,
                registry,
                diagnostics));
        return List.copyOf(defaults);
    }

    private static ProfileEntry requiredDefault(
            ResourceLocation profileId,
            ResourceLocation typeId,
            ProfileChannel expectedChannel,
            String configurationJson,
            Registry<ProfileType<?>> registry,
            DiagnosticCollector diagnostics) {
        JsonObject configuration;
        try {
            var parsed = parseJson(
                    configurationJson.getBytes(StandardCharsets.UTF_8), new ParseWork());
            if (!parsed.isJsonObject()) {
                throw new IllegalStateException("P8 built-in default is not an object");
            }
            configuration = parsed.getAsJsonObject();
        } catch (EntryRejectedException exception) {
            throw new IllegalStateException("P8 built-in default JSON is invalid");
        }
        var type = registry.getOptional(typeId)
                .orElseThrow(() -> new IllegalStateException("P8 built-in Profile type missing"));
        var decoded = decodeTyped(
                        profileId,
                        typeId,
                        type.currentConfigurationVersion(),
                        configuration,
                        type,
                        diagnostics)
                .orElseThrow(() -> new IllegalStateException(
                        "P8 built-in Profile default failed validation"));
        if (decoded.channel() != expectedChannel) {
            throw new IllegalStateException("P8 built-in Profile channel mismatch");
        }
        return decoded;
    }

    private static Optional<ProfileEntry> decodeResource(
            ResourceLocation profileId,
            byte[] source,
            Registry<ProfileType<?>> registry,
            DiagnosticCollector diagnostics,
            ParseWork parseWork) throws EntryRejectedException {
        var rootElement = parseJson(source, parseWork);
        if (!rootElement.isJsonObject()) {
            throw new EntryRejectedException();
        }
        var root = rootElement.getAsJsonObject();
        if (root.size() != 4
                || !root.has("format")
                || !root.has("type")
                || !root.has("type_version")
                || !root.has("configuration")) {
            throw new EntryRejectedException();
        }
        var format = exactInteger(root.get("format"));
        if (format.isEmpty() || format.orElseThrow() != 0) {
            throw new EntryRejectedException();
        }
        var version = exactInteger(root.get("type_version"));
        if (version.isEmpty()
                || version.orElseThrow() < 0
                || version.orElseThrow()
                        > PresentationLimits.MAX_PROFILE_CONFIGURATION_VERSION) {
            throw new EntryRejectedException();
        }
        var typeElement = root.get("type");
        if (!typeElement.isJsonPrimitive()
                || !typeElement.getAsJsonPrimitive().isString()) {
            throw new EntryRejectedException();
        }
        var typeId = ResourceLocation.tryParse(typeElement.getAsString());
        if (typeId == null || !validResourceLocation(typeId)) {
            throw new EntryRejectedException();
        }
        var configuration = root.get("configuration");
        if (!configuration.isJsonObject()) {
            throw new EntryRejectedException();
        }
        var type = registry.getOptional(typeId);
        if (type.isEmpty()) {
            diagnostics.record(DiagnosticCode.UNKNOWN_PROFILE_TYPE, profileId, typeId);
            return Optional.empty();
        }
        return decodeTyped(
                profileId,
                typeId,
                version.orElseThrow(),
                configuration.getAsJsonObject(),
                type.orElseThrow(),
                diagnostics);
    }

    private static <C extends ProfileConfiguration> Optional<ProfileEntry> decodeTyped(
            ResourceLocation profileId,
            ResourceLocation typeId,
            int configurationVersion,
            JsonObject configuration,
            ProfileType<C> type,
            DiagnosticCollector diagnostics) {
        Objects.requireNonNull(type, "type");
        if (configurationVersion != type.currentConfigurationVersion()) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_VERSION, profileId, typeId);
            return Optional.empty();
        }
        var channel = Objects.requireNonNull(type.channel(), "Profile channel");
        var codec = Objects.requireNonNull(
                type.configurationCodec(), "Profile configuration codec");
        var decoded = codec.codec().parse(JsonOps.INSTANCE, configuration);
        if (decoded.error().isPresent() || decoded.result().isEmpty()) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_CODEC, profileId, typeId);
            return Optional.empty();
        }
        var typedConfiguration = decoded.result().orElseThrow();
        var validation = Objects.requireNonNull(
                type.validate(typedConfiguration, PROFILE_VALIDATION_CONTEXT),
                "Profile validation result");
        if (!validation.isValid()) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_VALIDATION, profileId, typeId);
            return Optional.empty();
        }
        final ProfileCost estimatedCost;
        try {
            estimatedCost = Objects.requireNonNull(
                    type.estimateCost(typedConfiguration), "Profile estimated cost");
        } catch (IllegalArgumentException ignored) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_VALIDATION, profileId, typeId);
            return Optional.empty();
        }
        var factoryId = Objects.requireNonNull(
                        type.clientFactoryKey(), "Profile client factory key")
                .id();
        if (!validResourceLocation(typeId) || !validResourceLocation(factoryId)) {
            throw new IllegalStateException("Frozen Profile type contains an invalid ID");
        }

        var encoded = codec.codec().encodeStart(JsonOps.INSTANCE, typedConfiguration);
        if (encoded.error().isPresent() || encoded.result().isEmpty()) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_CODEC, profileId, typeId);
            return Optional.empty();
        }
        var canonicalSource = encoded.result().orElseThrow();
        if (!canonicalSource.isJsonObject()) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_CODEC, profileId, typeId);
            return Optional.empty();
        }
        try {
            verifyJsonBounds(canonicalSource);
        } catch (EntryRejectedException ignored) {
            diagnostics.record(DiagnosticCode.CONFIGURATION_CODEC, profileId, typeId);
            return Optional.empty();
        }
        var canonicalJson = CANONICAL_JSON.toJson(sortJson(canonicalSource));
        var canonicalBytes = canonicalJson.getBytes(StandardCharsets.UTF_8).length;
        var envelopeBytes = Math.addExact(varIntBytes(configurationVersion), canonicalBytes);
        if (envelopeBytes < PresentationLimits.MIN_PROFILE_ENVELOPE_BYTES
                || envelopeBytes > PresentationLimits.MAX_PROFILE_ENVELOPE_BYTES) {
            diagnostics.record(DiagnosticCode.PROFILE_ENVELOPE_LIMIT, profileId, typeId);
            return Optional.empty();
        }
        return Optional.of(new TypedProfileEntry<>(
                profileId,
                typeId,
                channel,
                factoryId,
                configurationVersion,
                type,
                typedConfiguration,
                estimatedCost,
                canonicalJson,
                envelopeBytes));
    }

    private static byte[] readBounded(Resource resource, ParseWork parseWork)
            throws IOException {
        Objects.requireNonNull(resource, "resource");
        try (var input = resource.open()) {
            var bytes = input.readNBytes(
                    PresentationLimits.MAX_PROFILE_INSPECTED_SOURCE_BYTES);
            parseWork.addInspectedBytes(bytes.length);
            if (bytes.length > PresentationLimits.MAX_PROFILE_RAW_SOURCE_BYTES) {
                return null;
            }
            parseWork.addAcceptedBytes(bytes.length);
            return bytes;
        }
    }

    private static JsonElement parseJson(byte[] source, ParseWork parseWork)
            throws EntryRejectedException {
        final String decoded;
        try {
            decoded = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(source))
                    .toString();
        } catch (CharacterCodingException ignored) {
            throw new EntryRejectedException();
        }

        var entryNodes = new EntryNodeCounter(parseWork);
        try (var reader = new JsonReader(new StringReader(decoded))) {
            reader.setLenient(false);
            var result = readJsonElement(reader, 1, entryNodes);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new EntryRejectedException();
            }
            return result;
        } catch (IOException | NumberFormatException ignored) {
            throw new EntryRejectedException();
        }
    }

    private static JsonElement readJsonElement(
            JsonReader reader, int depth, EntryNodeCounter nodes)
            throws IOException, EntryRejectedException {
        if (depth > PresentationLimits.MAX_PROFILE_JSON_DEPTH) {
            throw new EntryRejectedException();
        }
        nodes.addNode();
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> readJsonObject(reader, depth, nodes);
            case BEGIN_ARRAY -> readJsonArray(reader, depth, nodes);
            case STRING -> {
                var value = reader.nextString();
                requireJsonString(value);
                yield new JsonPrimitive(value);
            }
            case NUMBER -> new JsonPrimitive(new LazilyParsedNumber(reader.nextString()));
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            case NULL -> {
                reader.nextNull();
                yield JsonNull.INSTANCE;
            }
            default -> throw new EntryRejectedException();
        };
    }

    private static JsonObject readJsonObject(
            JsonReader reader, int depth, EntryNodeCounter nodes)
            throws IOException, EntryRejectedException {
        var result = new JsonObject();
        reader.beginObject();
        while (reader.hasNext()) {
            var name = reader.nextName();
            requireJsonString(name);
            if (result.has(name)) {
                throw new EntryRejectedException();
            }
            result.add(name, readJsonElement(reader, depth + 1, nodes));
        }
        reader.endObject();
        return result;
    }

    private static JsonArray readJsonArray(
            JsonReader reader, int depth, EntryNodeCounter nodes)
            throws IOException, EntryRejectedException {
        var result = new JsonArray();
        reader.beginArray();
        while (reader.hasNext()) {
            result.add(readJsonElement(reader, depth + 1, nodes));
        }
        reader.endArray();
        return result;
    }

    private static void requireJsonString(String value) throws EntryRejectedException {
        if (value.getBytes(StandardCharsets.UTF_8).length
                > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new EntryRejectedException();
        }
    }

    private static void verifyJsonBounds(JsonElement root) throws EntryRejectedException {
        verifyJsonBounds(root, 1, new int[] {0});
    }

    private static void verifyJsonBounds(JsonElement element, int depth, int[] nodes)
            throws EntryRejectedException {
        if (depth > PresentationLimits.MAX_PROFILE_JSON_DEPTH
                || nodes[0] == PresentationLimits.MAX_PROFILE_JSON_NODES_PER_ENTRY) {
            throw new EntryRejectedException();
        }
        nodes[0]++;
        if (element.isJsonObject()) {
            for (var member : element.getAsJsonObject().entrySet()) {
                requireJsonString(member.getKey());
                verifyJsonBounds(member.getValue(), depth + 1, nodes);
            }
        } else if (element.isJsonArray()) {
            for (var value : element.getAsJsonArray()) {
                verifyJsonBounds(value, depth + 1, nodes);
            }
        } else if (element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isString()) {
            requireJsonString(element.getAsString());
        }
    }

    private static JsonElement sortJson(JsonElement element) {
        if (element.isJsonObject()) {
            var result = new JsonObject();
            var members = new TreeMap<String, JsonElement>();
            for (var member : element.getAsJsonObject().entrySet()) {
                members.put(member.getKey(), member.getValue());
            }
            members.forEach((name, value) -> result.add(name, sortJson(value)));
            return result;
        }
        if (element.isJsonArray()) {
            var result = new JsonArray();
            for (var value : element.getAsJsonArray()) {
                result.add(sortJson(value));
            }
            return result;
        }
        return element.deepCopy();
    }

    private static Optional<Integer> exactInteger(JsonElement element) {
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isNumber()) {
            return Optional.empty();
        }
        try {
            return Optional.of(element.getAsBigDecimal().intValueExact());
        } catch (ArithmeticException | NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static void retainRequired(
            ProfileEntry entry,
            NavigableMap<ResourceLocation, ProfileEntry> retained,
            Map<ProfileChannel, Integer> channelCounts) {
        if (retained.put(entry.id(), entry) != null) {
            throw new IllegalStateException("Duplicate P8 built-in Profile default");
        }
        var current = channelCounts.get(entry.channel());
        if (current == null
                || current == PresentationLimits.MAX_PROFILE_INSTANCES_PER_CHANNEL) {
            throw new IllegalStateException("P8 built-in Profile capacity failure");
        }
        channelCounts.put(entry.channel(), current + 1);
    }

    private static boolean isReservedDefault(ResourceLocation id) {
        return id.equals(AppearanceSemantics.DEFAULT_SOUND_PROFILE)
                || id.equals(AppearanceSemantics.DEFAULT_PARTICLE_PROFILE)
                || id.equals(AppearanceSemantics.DEFAULT_TRAIL_PROFILE);
    }

    private static boolean validResourceLocation(ResourceLocation id) {
        return id != null
                && id.toString().getBytes(StandardCharsets.UTF_8).length
                        <= PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES;
    }

    private static int resourceLocationBytes(ResourceLocation id) {
        var payload = id.toString().getBytes(StandardCharsets.UTF_8).length;
        if (payload > PresentationLimits.MAX_RESOURCE_LOCATION_UTF8_BYTES) {
            throw new IllegalStateException("P8 Profile ID exceeds its UTF-8 bound");
        }
        return Math.addExact(varIntBytes(payload), payload);
    }

    private static int varIntBytes(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("canonical VarInt value must be non-negative");
        }
        if ((value & ~0x7F) == 0) {
            return 1;
        }
        if ((value & ~0x3FFF) == 0) {
            return 2;
        }
        if ((value & ~0x1F_FFFF) == 0) {
            return 3;
        }
        if ((value & ~0x0FFF_FFFF) == 0) {
            return 4;
        }
        return 5;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, path);
    }

    private enum DiagnosticCode {
        DISCOVERY_LIMIT,
        INVALID_PROFILE_ID,
        RESERVED_DEFAULT_COLLISION,
        RAW_SOURCE_LIMIT,
        INVALID_PROFILE_ENTRY,
        UNKNOWN_PROFILE_TYPE,
        CONFIGURATION_VERSION,
        CONFIGURATION_CODEC,
        CONFIGURATION_VALIDATION,
        PROFILE_ENVELOPE_LIMIT,
        CAPACITY_LIMIT
    }

    private record DiagnosticKey(
            DiagnosticCode code,
            Optional<ResourceLocation> primary,
            Optional<ResourceLocation> secondary) {
        private DiagnosticKey {
            Objects.requireNonNull(code, "code");
            primary = Objects.requireNonNull(primary, "primary");
            secondary = Objects.requireNonNull(secondary, "secondary");
            primary.ifPresent(P8ServerPresentationService::requireDiagnosticId);
            secondary.ifPresent(P8ServerPresentationService::requireDiagnosticId);
        }
    }

    private static final class DiagnosticCollector {
        private final LinkedHashSet<DiagnosticKey> keys = new LinkedHashSet<>();
        private long suppressed;

        void record(DiagnosticCode code) {
            record(code, Optional.empty(), Optional.empty());
        }

        void record(DiagnosticCode code, ResourceLocation primary) {
            record(code, Optional.of(primary), Optional.empty());
        }

        void record(
                DiagnosticCode code,
                ResourceLocation primary,
                ResourceLocation secondary) {
            record(code, Optional.of(primary), Optional.of(secondary));
        }

        List<DiagnosticKey> snapshot() {
            return List.copyOf(keys);
        }

        long suppressed() {
            return suppressed;
        }

        private void record(
                DiagnosticCode code,
                Optional<ResourceLocation> primary,
                Optional<ResourceLocation> secondary) {
            var key = new DiagnosticKey(code, primary, secondary);
            if (keys.contains(key)) {
                return;
            }
            if (keys.size() < PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS) {
                keys.add(key);
                return;
            }
            if (suppressed != Long.MAX_VALUE) {
                suppressed++;
            }
        }
    }

    private static void requireDiagnosticId(ResourceLocation id) {
        if (!validResourceLocation(id)) {
            throw new IllegalArgumentException("diagnostic Profile ID exceeds its bound");
        }
    }

    private static final class ParseWork {
        private long acceptedBytes;
        private long inspectedBytes;
        private long jsonNodes;

        void addAcceptedBytes(int value) {
            acceptedBytes = Math.addExact(acceptedBytes, value);
            if (acceptedBytes > PresentationLimits.MAX_PROFILE_ACCEPTED_RELOAD_BYTES) {
                throw new IllegalStateException("P8 accepted-source reload bound exceeded");
            }
        }

        void addInspectedBytes(int value) {
            inspectedBytes = Math.addExact(inspectedBytes, value);
            if (inspectedBytes > PresentationLimits.MAX_PROFILE_INSPECTED_RELOAD_BYTES) {
                throw new IllegalStateException("P8 inspected-source reload bound exceeded");
            }
        }

        void addJsonNode() throws EntryRejectedException {
            if (jsonNodes == PresentationLimits.MAX_PROFILE_JSON_NODES_PER_RELOAD) {
                throw new EntryRejectedException();
            }
            jsonNodes++;
        }
    }

    private static final class EntryNodeCounter {
        private final ParseWork reload;
        private int nodes;

        private EntryNodeCounter(ParseWork reload) {
            this.reload = Objects.requireNonNull(reload, "reload");
        }

        void addNode() throws EntryRejectedException {
            if (nodes == PresentationLimits.MAX_PROFILE_JSON_NODES_PER_ENTRY) {
                throw new EntryRejectedException();
            }
            reload.addJsonNode();
            nodes++;
        }
    }

    private static final class EntryRejectedException extends Exception {
        private EntryRejectedException() {
            super(null, null, false, false);
        }
    }

    private abstract static class ProfileEntry {
        abstract ResourceLocation id();

        abstract ProfileChannel channel();

        abstract ProfileConfiguration configuration();

        abstract ProfileType<?> type();

        abstract PresentationProfileDescriptor descriptor();

        abstract String canonicalConfigurationJson();

        abstract int envelopeBytes();

        abstract int wireBodyBytes();
    }

    private static final class TypedProfileEntry<C extends ProfileConfiguration>
            extends ProfileEntry {
        private final ResourceLocation id;
        private final ResourceLocation typeId;
        private final ProfileChannel channel;
        private final ResourceLocation clientFactoryId;
        private final int configurationVersion;
        private final ProfileType<C> type;
        private final C configuration;
        private final ProfileCost estimatedCost;
        private final PresentationProfileDescriptor descriptor;
        private final String canonicalConfigurationJson;
        private final int envelopeBytes;

        private TypedProfileEntry(
                ResourceLocation id,
                ResourceLocation typeId,
                ProfileChannel channel,
                ResourceLocation clientFactoryId,
                int configurationVersion,
                ProfileType<C> type,
                C configuration,
                ProfileCost estimatedCost,
                String canonicalConfigurationJson,
                int envelopeBytes) {
            this.id = Objects.requireNonNull(id, "id");
            this.typeId = Objects.requireNonNull(typeId, "typeId");
            this.channel = Objects.requireNonNull(channel, "channel");
            this.clientFactoryId = Objects.requireNonNull(clientFactoryId, "clientFactoryId");
            this.configurationVersion = configurationVersion;
            this.type = Objects.requireNonNull(type, "type");
            this.configuration = Objects.requireNonNull(configuration, "configuration");
            this.estimatedCost = Objects.requireNonNull(estimatedCost, "estimatedCost");
            this.descriptor = new PresentationProfileDescriptor(
                    id,
                    channel,
                    Objects.requireNonNull(type.capabilities(), "Profile type capabilities"),
                    estimatedCost);
            this.canonicalConfigurationJson = Objects.requireNonNull(
                    canonicalConfigurationJson, "canonicalConfigurationJson");
            this.envelopeBytes = envelopeBytes;
        }

        @Override
        ResourceLocation id() {
            return id;
        }

        @Override
        ProfileChannel channel() {
            return channel;
        }

        @Override
        ProfileConfiguration configuration() {
            return configuration;
        }

        @Override
        ProfileType<?> type() {
            return type;
        }

        @Override
        PresentationProfileDescriptor descriptor() {
            return descriptor;
        }

        @Override
        String canonicalConfigurationJson() {
            return canonicalConfigurationJson;
        }

        @Override
        int envelopeBytes() {
            return envelopeBytes;
        }

        @Override
        int wireBodyBytes() {
            var bytes = resourceLocationBytes(id);
            bytes = Math.addExact(bytes, resourceLocationBytes(typeId));
            bytes = Math.addExact(bytes, 1);
            bytes = Math.addExact(bytes, resourceLocationBytes(clientFactoryId));
            bytes = Math.addExact(bytes, varIntBytes(envelopeBytes));
            return Math.addExact(bytes, envelopeBytes);
        }
    }

    static final class PreparedCatalog {
        private final NavigableMap<ResourceLocation, ProfileEntry> entries;
        private final Map<ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>> byChannel;
        private final List<DiagnosticKey> diagnostics;
        private final long suppressedDiagnostics;
        private final int wireBodyBytes;

        private PreparedCatalog(
                NavigableMap<ResourceLocation, ProfileEntry> entries,
                List<DiagnosticKey> diagnostics,
                long suppressedDiagnostics) {
            if (entries.size() < 3
                    || entries.size() > PresentationLimits.MAX_PROFILE_INSTANCES) {
                throw new IllegalArgumentException("P8 catalog entry count is outside bounds");
            }
            this.entries = Collections.unmodifiableNavigableMap(new TreeMap<>(entries));
            this.byChannel = channelViews(this.entries);
            this.diagnostics = List.copyOf(diagnostics);
            if (this.diagnostics.size()
                            > PresentationLimits.MAX_PROFILE_DIAGNOSTIC_KEYS
                    || suppressedDiagnostics < 0) {
                throw new IllegalArgumentException("P8 diagnostic state is outside bounds");
            }
            this.suppressedDiagnostics = suppressedDiagnostics;
            this.wireBodyBytes = catalogBodyBytes(this.entries);
        }

        private static Map<ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>>
                channelViews(NavigableMap<ResourceLocation, ProfileEntry> entries) {
            var mutable = new EnumMap<
                    ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>>(
                    ProfileChannel.class);
            for (var channel : ProfileChannel.values()) {
                mutable.put(channel, new TreeMap<>());
            }
            for (var entry : entries.values()) {
                mutable.get(entry.channel()).put(entry.id(), entry);
            }
            var immutable = new EnumMap<
                    ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>>(
                    ProfileChannel.class);
            mutable.forEach((channel, index) -> immutable.put(
                    channel, Collections.unmodifiableNavigableMap(index)));
            return Collections.unmodifiableMap(immutable);
        }

        private static int catalogBodyBytes(
                NavigableMap<ResourceLocation, ProfileEntry> entries) {
            var bytes = Math.addExact(9, varIntBytes(entries.size()));
            for (var entry : entries.values()) {
                bytes = Math.addExact(bytes, entry.wireBodyBytes());
            }
            if (bytes > PresentationLimits.MAX_PROFILE_CATALOG_BODY_BYTES) {
                throw new IllegalArgumentException("P8 catalog body exceeds its byte bound");
            }
            return bytes;
        }
    }

    private static final class CatalogSnapshot {
        private final long generation;
        private final NavigableMap<ResourceLocation, ProfileEntry> entries;
        private final Map<ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>> byChannel;
        private final List<DiagnosticKey> diagnostics;
        private final long suppressedDiagnostics;
        private final int wireBodyBytes;

        private CatalogSnapshot(long generation, PreparedCatalog prepared) {
            if (generation <= 0) {
                throw new IllegalArgumentException("P8 catalog generation must be positive");
            }
            this.generation = generation;
            this.entries = prepared.entries;
            this.byChannel = prepared.byChannel;
            this.diagnostics = prepared.diagnostics;
            this.suppressedDiagnostics = prepared.suppressedDiagnostics;
            this.wireBodyBytes = prepared.wireBodyBytes;
        }

        private boolean contains(ProfileChannel channel, ResourceLocation id) {
            return byChannel.get(channel).containsKey(id);
        }

        private Optional<ProfileEntry> entry(ResourceLocation id) {
            return Optional.ofNullable(entries.get(id));
        }

        private CatalogSnapshot withGeneration(long nextGeneration) {
            if (nextGeneration <= 0) {
                throw new IllegalArgumentException("P8 catalog generation must be positive");
            }
            return new CatalogSnapshot(
                    nextGeneration,
                    entries,
                    byChannel,
                    diagnostics,
                    suppressedDiagnostics,
                    wireBodyBytes);
        }

        private CatalogSnapshot(
                long generation,
                NavigableMap<ResourceLocation, ProfileEntry> entries,
                Map<ProfileChannel, NavigableMap<ResourceLocation, ProfileEntry>> byChannel,
                List<DiagnosticKey> diagnostics,
                long suppressedDiagnostics,
                int wireBodyBytes) {
            this.generation = generation;
            this.entries = entries;
            this.byChannel = byChannel;
            this.diagnostics = diagnostics;
            this.suppressedDiagnostics = suppressedDiagnostics;
            this.wireBodyBytes = wireBodyBytes;
        }
    }

    static final class AppearanceResolutionSnapshot {
        private final CatalogSnapshot snapshot;
        private final PresentationProfileView profiles;

        private AppearanceResolutionSnapshot(CatalogSnapshot snapshot) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.profiles = id -> this.snapshot.entry(id).map(ProfileEntry::descriptor);
        }

        long catalogGeneration() {
            return snapshot.generation;
        }

        PresentationProfileView profiles() {
            return profiles;
        }
    }

    static final class ReloadIdentity {
        private final ReloadMarker marker = new ReloadMarker();
        private final ReloadableServerResources platformIdentity;

        private ReloadIdentity() {
            this.platformIdentity = null;
        }

        private ReloadIdentity(ReloadableServerResources platformIdentity) {
            this.platformIdentity = Objects.requireNonNull(platformIdentity, "platformIdentity");
        }

        private boolean matches(ReloadIdentity other) {
            if (platformIdentity == null || other.platformIdentity == null) {
                return this == other;
            }
            return platformIdentity == other.platformIdentity;
        }
    }

    private static final class ReloadCycle {
        private final ReloadIdentity identity;
        private final ReloadMarker marker;

        private ReloadCycle(ReloadIdentity identity) {
            this.identity = Objects.requireNonNull(identity, "identity");
            this.marker = identity.marker;
        }
    }

    private static final class ReloadMarker {}

    private static final class PendingCandidate {
        private final ReloadCycle cycle;
        private final PreparedCatalog prepared;

        private PendingCandidate(ReloadCycle cycle, PreparedCatalog prepared) {
            this.cycle = Objects.requireNonNull(cycle, "cycle");
            this.prepared = Objects.requireNonNull(prepared, "prepared");
        }
    }

    private static final class ProfileCatalogReloadListener
            extends SimplePreparableReloadListener<PreparedCatalog> {
        private final P8ServerPresentationService owner;
        private final ReloadCycle cycle;

        private ProfileCatalogReloadListener(
                P8ServerPresentationService owner, ReloadCycle cycle) {
            this.owner = Objects.requireNonNull(owner, "owner");
            this.cycle = Objects.requireNonNull(cycle, "cycle");
        }

        @Override
        protected PreparedCatalog prepare(
                ResourceManager resources, ProfilerFiller preparationsProfiler) {
            Objects.requireNonNull(preparationsProfiler, "preparationsProfiler");
            return loadCatalog(resources);
        }

        @Override
        protected void apply(
                PreparedCatalog prepared,
                ResourceManager resources,
                ProfilerFiller reloadProfiler) {
            Objects.requireNonNull(resources, "resources");
            Objects.requireNonNull(reloadProfiler, "reloadProfiler");
            owner.stageCandidate(cycle, prepared);
        }
    }
}
