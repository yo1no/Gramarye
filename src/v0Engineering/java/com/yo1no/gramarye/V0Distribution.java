package com.yo1no.gramarye;

import com.mojang.logging.LogUtils;
import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import com.yo1no.gramarye.magic.definition.scoped.V0EngineeringCatalogAssembly;
import com.yo1no.gramarye.magic.definition.scoped.V0RuntimeCatalog;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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

    static Optional<V0SlotBinding> open(V0SlotStartup startup) {
        var server = startup.server();
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

        private Role(String label, UUID owner, V0LogicalIntent route) {
            this.label = label;
            this.owner = new SkillOwnerId(owner);
            this.route = route;
        }
    }

    private static final class Scenario {
        private final V0SlotStartup startup;
        private final V0EngineeringColdLane cold;
        private final Recipe recipe;
        private final V0EngineeringEntry entry;
        private final Consumer<ServerTickEvent.Post> tick;
        private final Consumer<LivingDamageEvent.Post> damage;
        private final Consumer<ServerStoppedEvent> stopped;
        private final V0RuntimeCatalog.Selection standalone = V0RuntimeCatalog.Selection.standalone(STANDALONE_ROOT);
        private final V0RuntimeCatalog.Selection manifest = V0RuntimeCatalog.Selection.manifest(MANIFEST_ROOT, MANIFEST_ENTRY);
        private boolean closing;
        private boolean updated;
        private int firstReservationTick = -1;

        private Scenario(V0SlotStartup startup, V0EngineeringColdLane cold, Recipe recipe) {
            this.startup = startup;
            this.cold = cold;
            this.recipe = recipe;
            entry = V0EngineeringEntry.open(startup);
            tick = this::step;
            damage = P9V0WorldEffectHandoff::observeV0DamagePost;
            stopped = this::stop;
        }

        private void register() {
            NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, tick);
            NeoForge.EVENT_BUS.addListener(damage);
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
            if (role.issued || intent != role.route) {
                return V0Submission.REJECTED;
            }
            var route = intent == V0LogicalIntent.STANDALONE ? standalone : manifest;
            if (!startup.runtime().reserveV0Capture(startup.server(), startup.authority(), actor, role.owner, route)) {
                return V0Submission.BUSY;
            }
            role.issued = true;
            cold.wake();
            return V0Submission.RESERVED_COLD;
        }

        private void step(ServerTickEvent.Post event) {
            if (event.getServer() != startup.server() || closing) {
                return;
            }
            V0AuthenticatedLoginObservation.advance(startup.server(), startup.authority());
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

        private void offer(Role role) {
            var actor = startup.server().getPlayerList().getPlayer(role.owner.value());
            if (actor == null || actor.isRemoved()) {
                role.exactPlay = null;
                return;
            }
            if (role.issued && actor.connection != null && role.exactPlay == actor.connection.getConnection()) {
                return;
            }
            if (entry.submit(actor, role.route) == V0Submission.RESERVED_COLD) {
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
            if (active == this) { active = null; }
            V0AuthenticatedLoginObservation.retire(startup.server(), startup.authority());
            NeoForge.EVENT_BUS.unregister(tick);
            NeoForge.EVENT_BUS.unregister(damage);
            NeoForge.EVENT_BUS.unregister(stopped);
            cold.wake();
        }
    }

    private static final class Recipe {
        private final Role first;
        private final Role second;
        private final boolean zeroFee;
        private final boolean publishUpdate;

        private Recipe(Role first, Role second, boolean zeroFee, boolean publishUpdate) {
            this.first = first;
            this.second = second;
            this.zeroFee = zeroFee;
            this.publishUpdate = publishUpdate;
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
            if (!RECIPE_KEYS.equals(fields.stringPropertyNames()) || !"1".equals(fields.getProperty("format"))) {
                throw new IOException("Engineering recipe schema");
            }
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
            return new Recipe(first, second, zeroFee, update);
        }
    }
}
