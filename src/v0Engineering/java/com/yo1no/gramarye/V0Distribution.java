package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0EngineeringCatalogAssembly;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Packaged replacement of the ordinary inert leaf. The recipe exists only in the owned fixture. */
final class V0Distribution {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> RECIPE_KEYS = Set.of(
            "format", "topology", "roleA", "roleB", "routeA", "routeB", "fee", "publishUpdate");
    // Private published roles from first physical reservation: Scope D stride 256 plus ordinals 1 and 3.
    private static final long STANDALONE_ROOT = 257L;
    private static final long MANIFEST_ROOT = 259L;
    private static final long MANIFEST_ENTRY = 1L;
    private static V0EngineeringColdLane previousLane;
    private static Scenario active;

    private V0Distribution() {}

    static V0StartupProfile profile(MinecraftServer server, V0StartupLoadState engineeringState) {
        var recipe = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .resolve("v0-engineering.properties");
        return Files.notExists(recipe, LinkOption.NOFOLLOW_LINKS)
                ? V0StartupProfile.normal() : V0StartupProfile.engineering(engineeringState);
    }

    static Optional<V0SlotBinding> open(V0SlotStartup startup) {
        var server = startup.server();
        if (startup.profile() == V0StartupProfile.Kind.NORMAL) {
            var recipe = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .resolve("v0-engineering.properties");
            // A newly present, unreadable or malformed recipe is never absence fallback.
            return server.isSameThread() && active == null
                    && (previousLane == null || previousLane.finished())
                    && Files.notExists(recipe, LinkOption.NOFOLLOW_LINKS)
                    ? V0NormalComposition.open(startup) : Optional.empty();
        }
        if (!server.isSameThread() || !(startup.v0State() instanceof V0StartupLoadState.Ready)
                || active != null || !packagedArtifact() || previousLane != null && !previousLane.finished()) {
            return Optional.empty();
        }
        var world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        var input = world.resolve("v0-engineering.properties");
        if (!Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try {
            var recipe = Recipe.load(server, world, input);
            return install(startup, world, recipe);
        } catch (IOException | IllegalArgumentException invalid) {
            // The owned recipe's content and account identifiers are never copied to diagnostics.
            LOGGER.warn("V0_ENGINEERING_START outcome=PRIVATE_UNIVERSE_UNPROVED");
            return Optional.empty();
        }
    }

    private static Optional<V0SlotBinding> install(V0SlotStartup startup, Path world, Recipe recipe)
            throws IOException {
        var catalogRoot = world.resolve("v0-fixture-catalog");
        if (Files.notExists(catalogRoot, LinkOption.NOFOLLOW_LINKS)) {
            Files.createDirectory(catalogRoot);
        }
        if (!Files.isDirectory(catalogRoot, LinkOption.NOFOLLOW_LINKS)
                || !Files.getOwner(catalogRoot).equals(Files.getOwner(world))) {
            throw new IOException("Unowned catalog directory");
        }
        var server = startup.server();
        var authority = startup.authority();
        var catalog = V0EngineeringCatalogAssembly.open(catalogRoot, authority);
        var mailbox = new V0ColdMailbox(authority);
        var cold = new V0EngineeringColdLane(authority, mailbox, catalog, recipe.first.owner, recipe.second.owner);
        var scenario = new Scenario(startup, cold, recipe);
        var binding = V0SlotBinding.create(catalog, mailbox);
        binding.bindZeroFeeSelections(recipe.zeroFee ? Set.of(scenario.standalone, scenario.manifest) : Set.of());
        if (!V0AuthenticatedLoginObservation.bind(server, authority, recipe.first.owner.value(), recipe.second.owner.value())) {
            return Optional.empty();
        }
        scenario.register();
        previousLane = cold;
        active = scenario;
        cold.start();
        return Optional.of(binding);
    }

    private static boolean packagedArtifact() {
        var mod = ModList.get().getModFileById(Gramarye.MOD_ID);
        if (mod == null) {
            return false;
        }
        Path artifact = mod.getFile().getFilePath();
        return artifact != null && artifact.getFileName().toString().endsWith(".jar")
                && Files.isRegularFile(artifact, LinkOption.NOFOLLOW_LINKS);
    }

    static V0Submission submit(V0SlotStartup startup, ServerPlayer actor, V0LogicalIntent intent) {
        var exact = active;
        if (exact == null || exact.startup != startup || actor == null || intent == null || !exact.ready()) {
            return V0Submission.NOT_READY;
        }
        var role = exact.authorizedRole(actor);
        return role == null ? V0Submission.UNAUTHORIZED : exact.submit(actor, role, intent);
    }

    private static boolean provedPlay(Scenario scenario, Role role, ServerPlayer actor) {
        var server = scenario.startup.server();
        if (actor == null || actor.getServer() != server || !role.owner.value().equals(actor.getUUID())
                || server.getPlayerList().getPlayer(actor.getUUID()) != actor || !actor.isAlive()
                || actor.isRemoved() || actor.isFakePlayer() || !server.usesAuthentication()) {
            return false;
        }
        var listener = actor.connection;
        if (listener == null || listener.player != actor || !listener.isAcceptingMessages()) {
            return false;
        }
        Connection connection = listener.getConnection();
        if (connection.getPacketListener() != listener || !connection.isConnected() || connection.isMemoryConnection()) {
            return false;
        }
        if (role.exactPlay != connection) {
            if (!V0AuthenticatedLoginObservation.consume(server, connection, actor.getUUID(), scenario.startup.authority())) {
                return false;
            }
            role.exactPlay = connection;
            role.issued = false;
            LOGGER.info("V0_ENGINEERING_PLAY role={} nativeSuccess=true", role.label);
        }
        return true;
    }

    private static final class Role {
        private final String label;
        private final SkillOwnerId owner;
        private final V0LogicalIntent route;
        private Connection exactPlay;
        private boolean issued;
        private ServerPlayer performanceActor;
        private ServerGamePacketListenerImpl performanceListener;
        private int offers;
        private int reservedCold;
        private int busy;

        private Role(String label, UUID owner, V0LogicalIntent route) {
            this.label = label;
            this.owner = new SkillOwnerId(owner);
            this.route = route;
        }
    }

    private static final class Scenario {
        private static final int PERFORMANCE_FIRST_TICK = 2_400;
        private static final int PERFORMANCE_WARMUP_TICKS = 2_000;
        private static final int PERFORMANCE_SAMPLE_TICKS = 10_000;
        private static final int PERFORMANCE_TICKS = PERFORMANCE_WARMUP_TICKS + PERFORMANCE_SAMPLE_TICKS;

        private final V0SlotStartup startup;
        private final V0EngineeringColdLane cold;
        private final Recipe recipe;
        private final V0EngineeringEntry entry;
        private final Consumer<ServerTickEvent.Post> tick;
        private final Consumer<ServerStoppedEvent> stopped;
        private final V0RuntimeCatalog.Selection standalone = V0RuntimeCatalog.Selection.standalone(STANDALONE_ROOT);
        private final V0RuntimeCatalog.Selection manifest = V0RuntimeCatalog.Selection.manifest(MANIFEST_ROOT, MANIFEST_ENTRY);
        private boolean closing;
        private boolean updated;
        private int firstReservationTick = -1;
        private int performanceTicks;
        private boolean performanceDone;
        private Role performanceOffer;

        private Scenario(V0SlotStartup startup, V0EngineeringColdLane cold, Recipe recipe) {
            this.startup = startup;
            this.cold = cold;
            this.recipe = recipe;
            entry = V0EngineeringEntry.open(startup);
            tick = this::step;
            stopped = this::stop;
        }

        private void register() {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, tick);
            NeoForge.EVENT_BUS.addListener(stopped);
        }

        boolean ready() {
            return !closing && startup.server().isSameThread() && cold.ready();
        }

        Role authorizedRole(ServerPlayer actor) {
            Role role;
            if (recipe.first.owner.value().equals(actor.getUUID())) {
                role = recipe.first;
            } else if (recipe.second.owner.value().equals(actor.getUUID())) {
                role = recipe.second;
            } else {
                return null;
            }
            return provedPlay(this, role, actor) ? role : null;
        }

        V0Submission submit(ServerPlayer actor, Role role, V0LogicalIntent intent) {
            if (intent != role.route) {
                return V0Submission.REJECTED;
            }
            if (recipe.performance == null) {
                if (role.issued) {
                    return V0Submission.REJECTED;
                }
            } else {
                if (performanceOffer != role || role.performanceActor != actor
                        || role.performanceListener != actor.connection
                        || startup.server().getTickCount() != PERFORMANCE_FIRST_TICK + performanceTicks) {
                    return V0Submission.REJECTED;
                }
                // Take the scheduled offer before entering P5, including when P5 is busy.
                performanceOffer = null;
            }
            var route = intent == V0LogicalIntent.STANDALONE ? standalone : manifest;
            if (!startup.runtime().reserveV0Capture(startup.server(), startup.authority(), actor, role.owner, route)) {
                return V0Submission.BUSY;
            }
            if (recipe.performance == null) {
                role.issued = true;
            }
            cold.wake();
            return V0Submission.RESERVED_COLD;
        }

        private void step(ServerTickEvent.Post event) {
            if (event.getServer() != startup.server() || closing) {
                return;
            }
            V0AuthenticatedLoginObservation.advance(startup.server(), startup.authority());
            if (recipe.performance != null) {
                if (startup.server().isSameThread()) {
                    stepPerformance(startup.server().getTickCount());
                }
                return;
            }
            if (!ready()) {
                return;
            }
            offer(recipe.first);
            offer(recipe.second);
            if (recipe.publishUpdate && !updated && firstReservationTick >= 0
                    && (long) startup.server().getTickCount() - firstReservationTick >= 12) {
                updated = true;
                cold.requestUpdate();
            }
        }

        private void stepPerformance(int nativeTick) {
            if (performanceDone) {
                return;
            }
            if (performanceTicks == PERFORMANCE_TICKS) {
                finishPerformance("COMPLETE_WINDOW");
                return;
            }
            boolean arm = performanceTicks == 0 && nativeTick < PERFORMANCE_FIRST_TICK;
            if (!verifyPerformancePlay(recipe.first, arm) || !verifyPerformancePlay(recipe.second, arm)) {
                finishPerformance("EXACT_PLAY_LOST_OR_LATE");
                return;
            }
            if (arm) {
                return;
            }
            if (!ready() || nativeTick != PERFORMANCE_FIRST_TICK + performanceTicks) {
                finishPerformance("WINDOW_OR_COLD_NOT_READY");
                return;
            }
            if (performanceTicks == 0) {
                LOGGER.info("V0_ENGINEERING_PERFORMANCE_START mode={} firstTick={} warmupTicks={} sampleTicks={}",
                        recipe.performance, PERFORMANCE_FIRST_TICK, PERFORMANCE_WARMUP_TICKS, PERFORMANCE_SAMPLE_TICKS);
            }
            if ("MIXED".equals(recipe.performance)) {
                var role = (performanceTicks & 1) == 0 ? recipe.first : recipe.second;
                performanceOffer = role;
                try {
                    offer(role);
                } finally {
                    performanceOffer = null;
                }
            }
            if (!performanceDone) {
                performanceTicks++;
            }
        }

        private boolean verifyPerformancePlay(Role role, boolean arm) {
            var actor = startup.server().getPlayerList().getPlayer(role.owner.value());
            if (role.performanceActor == null) {
                if (!arm) {
                    return false;
                }
                if (actor != null && actor.connection != null && actor.connection.protocol() == ConnectionProtocol.PLAY
                        && provedPlay(this, role, actor)) {
                    role.performanceActor = actor;
                    role.performanceListener = actor.connection;
                }
                return true;
            }
            // Detect a replacement before provedPlay can consume another connection's proof.
            return actor == role.performanceActor && actor.connection == role.performanceListener
                    && actor.connection.protocol() == ConnectionProtocol.PLAY
                    && actor.connection.getConnection() == role.exactPlay && provedPlay(this, role, actor);
        }

        private void finishPerformance(String outcome) {
            if (performanceDone) {
                return;
            }
            performanceDone = true;
            performanceOffer = null;
            var a = recipe.first;
            var b = recipe.second;
            a.performanceActor = null;
            a.performanceListener = null;
            b.performanceActor = null;
            b.performanceListener = null;
            LOGGER.info("V0_ENGINEERING_PERFORMANCE_TERMINAL mode={} outcome={} ticks={} offersA={} reservedColdA={} "
                            + "busyA={} offersB={} reservedColdB={} busyB={}",
                    recipe.performance, outcome, performanceTicks, a.offers, a.reservedCold, a.busy,
                    b.offers, b.reservedCold, b.busy);
        }

        private void offer(Role role) {
            var actor = startup.server().getPlayerList().getPlayer(role.owner.value());
            if (recipe.performance == null) {
                if (actor == null || actor.isRemoved()) {
                    role.exactPlay = null;
                    return;
                }
                if (role.issued && actor.connection != null && role.exactPlay == actor.connection.getConnection()) {
                    return;
                }
            } else if (actor != role.performanceActor) {
                finishPerformance("EXACT_PLAY_LOST_OR_LATE");
                return;
            }
            var result = entry.submit(actor, role.route);
            if (recipe.performance != null) {
                role.offers++;
                if (result == V0Submission.RESERVED_COLD) {
                    role.reservedCold++;
                } else if (result == V0Submission.BUSY) {
                    role.busy++;
                } else {
                    finishPerformance("UNEXPECTED_INGRESS");
                }
            } else if (result == V0Submission.RESERVED_COLD) {
                if (firstReservationTick < 0) {
                    firstReservationTick = startup.server().getTickCount();
                }
                LOGGER.info("V0_ENGINEERING_INGRESS role={} route={} result=RESERVED_COLD tick={}",
                        role.label, role.route, startup.server().getTickCount());
            }
        }

        private void stop(ServerStoppedEvent event) {
            if (event.getServer() != startup.server()) {
                return;
            }
            closing = true;
            if (recipe.performance != null) {
                finishPerformance(performanceTicks == PERFORMANCE_TICKS ? "COMPLETE_WINDOW" : "SERVER_STOPPED");
            }
            if (active == this) { active = null; }
            V0AuthenticatedLoginObservation.retire(startup.server(), startup.authority());
            NeoForge.EVENT_BUS.unregister(tick);
            NeoForge.EVENT_BUS.unregister(stopped);
            cold.wake();
        }
    }

    private static final class Recipe {
        private final Role first;
        private final Role second;
        private final boolean zeroFee;
        private final boolean publishUpdate;
        private final String performance;

        private Recipe(Role first, Role second, boolean zeroFee, boolean publishUpdate, String performance) {
            this.first = first;
            this.second = second;
            this.zeroFee = zeroFee;
            this.publishUpdate = publishUpdate;
            this.performance = performance;
        }

        private static Recipe load(MinecraftServer server, Path world, Path recipe) throws IOException {
            if (!world.equals(world.toRealPath()) || !Files.getOwner(recipe).equals(Files.getOwner(world))
                    || Files.size(recipe) > 1_024) {
                throw new IOException("Unqualified world fixture");
            }
            Properties fields = new Properties();
            try (Reader reader = Files.newBufferedReader(recipe)) {
                fields.load(reader);
            }
            String performance = switch (fields.getProperty("format", "")) {
                case "1" -> {
                    if (!RECIPE_KEYS.equals(fields.stringPropertyNames())) {
                        throw new IOException("Engineering recipe schema");
                    }
                    yield null;
                }
                case "2" -> readPerformance(recipe, fields);
                default -> throw new IOException("Engineering recipe schema");
            };
            String topology = fields.getProperty("topology");
            boolean allowed = "dedicated".equals(topology) && server.isDedicatedServer()
                    && server.usesAuthentication() && !server.isSingleplayer()
                    || "host_lan".equals(topology) && !server.isDedicatedServer() && server.isSingleplayer();
            if (!allowed) {
                throw new IOException("Engineering server topology");
            }
            var first = new Role("A", UUID.fromString(fields.getProperty("roleA")),
                    V0LogicalIntent.valueOf(fields.getProperty("routeA")));
            var second = new Role("B", UUID.fromString(fields.getProperty("roleB")),
                    V0LogicalIntent.valueOf(fields.getProperty("routeB")));
            if (first.owner.equals(second.owner) || first.owner.value().equals(new UUID(0, 0))
                    || second.owner.value().equals(new UUID(0, 0))) {
                throw new IOException("Engineering roles require two distinct subjects");
            }
            boolean zeroFee = switch (fields.getProperty("fee")) {
                case "zero" -> true;
                case "positive" -> false;
                default -> throw new IOException("Engineering fee recipe");
            };
            boolean update = switch (fields.getProperty("publishUpdate")) {
                case "true" -> true;
                case "false" -> false;
                default -> throw new IOException("Engineering publication recipe");
            };
            if (performance != null && (!"dedicated".equals(topology) || !zeroFee || update || first.route == second.route)) {
                throw new IOException("Engineering performance recipe scope");
            }
            return new Recipe(first, second, zeroFee, update, performance);
        }

        private static String readPerformance(Path recipe, Properties fields) throws IOException {
            String text;
            try (var input = Files.newInputStream(recipe, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(1_025);
                if (bytes.length > 1_024) {
                    throw new IOException("Unqualified world fixture");
                }
                text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            }
            var rows = new HashSet<String>();
            for (String line : text.lines().toList()) {
                int split = line.indexOf('=');
                if (split < 1) {
                    throw new IOException("Engineering performance recipe row");
                }
                String key = line.substring(0, split);
                if (!rows.add(key) || !line.substring(split + 1).equals(fields.getProperty(key))) {
                    throw new IOException("Engineering performance recipe row");
                }
            }
            if (rows.size() != RECIPE_KEYS.size() + 1 || !rows.containsAll(RECIPE_KEYS) || !rows.contains("performance")) {
                throw new IOException("Engineering performance recipe schema");
            }
            return switch (fields.getProperty("performance")) {
                case "REFERENCE", "MIXED" -> fields.getProperty("performance");
                default -> throw new IOException("Engineering performance recipe mode");
            };
        }
    }
}
