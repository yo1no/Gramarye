package com.yo1no.gramarye;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Excluded owned portals drive actual native dimension travel, credits and client return. */
@EventBusSubscriber(modid = Gramarye.MOD_ID, value = Dist.CLIENT)
public final class P11NativeEndProbe {
    private static final ResourceLocation ADVANCEMENT = ResourceLocation.fromNamespaceAndPath(
            "gramarye_p11_engineering", "native_end_return");
    private static final ResourceLocation SKILLS = ResourceLocation.fromNamespaceAndPath(Gramarye.MOD_ID, "player_skills");
    private static final String MARKER = "p11_native_true_end_return";
    private static final int REWARD = 29;
    private static final long READ_BOUND = 32L * 1024 * 1024;
    private static volatile State active;

    private P11NativeEndProbe() {}

    static void start(MinecraftServer server, ServerPlayer actor, Path output) {
        require(active == null && "native-end".equals(System.getProperty("gramarye.p11.sourceWriter.case"))
                        && System.getProperty(P11SourceWriterClientHarness.OUTPUT_PROPERTY) != null,
                "End probe requires its exact excluded case");
        require(server.isSameThread() && server.isSingleplayerOwner(actor.getGameProfile())
                        && !actor.isFakePlayer() && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                        && actor.connection != null && actor.connection.player == actor
                        && actor.connection.getConnection().isConnected() && actor.serverLevel() == server.overworld()
                        && !actor.seenCredits && !actor.wonGame && !actor.isRemoved(),
                "End fixture requires the actual fresh authenticated Overworld actor");
        var diagnostic = P11NativeStorageBoundary.diagnostics(server, actor.getUUID());
        require(diagnostic.bodyComplete() && !diagnostic.candidatePresent() && diagnostic.sourceFault().equals("NONE"),
                "End fixture lacks complete qualified source");
        var advancement = server.getAdvancements().get(ADVANCEMENT);
        require(advancement != null && !actor.getAdvancements().getOrStartProgress(advancement).isDone(),
                "End return criterion is missing or already completed");
        try { Files.createDirectory(output); }
        catch (IOException failure) { throw new IllegalStateException("End output creation failed", failure); }
        var state = new State(actor, output, diagnostic.sourceEpoch());
        active = state;
        actor.addTag(MARKER);
        require(nativeCredits(actor) == 0, "End credit fixture did not start without native N");
        nativeSelfCredit(actor);
        state.overworldPortal = placeOwnedPortal(actor);
        state.stage = "WAIT_NATIVE_END_TRAVEL";
    }

    /** Only observes the actual native packet-created screen; its original callback sends the packet. */
    static void clientObserve(Minecraft minecraft) {
        var state = active;
        if (state == null) { return; }
        require(minecraft.isSameThread(), "End client observer is off thread");
        if (state.failure != null) { throw new IllegalStateException("End native observation failed", state.failure); }
        if (minecraft.level != null && minecraft.level.dimension() == Level.END && minecraft.player != null
                && minecraft.screen == null) { state.clientEndReady = true; }
        if (state.creditsObserved && !state.screenClosed && minecraft.screen instanceof WinScreen screen) {
            require(minecraft.level != null && minecraft.level.dimension() == Level.END
                            && minecraft.getConnection() != null,
                    "native credits screen lost its actual End PLAY connection");
            state.screenClosed = true;
            screen.onClose();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void tick(ServerTickEvent.Post event) {
        var state = active;
        if (state == null || state.complete || state.failure != null || event.getServer() != state.original.getServer()) { return; }
        try {
            var actor = state.original;
            var server = actor.getServer();
            if (state.stage.equals("WAIT_NATIVE_END_TRAVEL") && actor.level().dimension() == Level.END) {
                require(state.dimensionEvents == 1 && state.clones == 0 && !actor.isRemoved()
                                && server.getPlayerList().getPlayer(actor.getUUID()) == actor
                                && P11NativeStorageBoundary.diagnostics(server, actor.getUUID()).sourceEpoch() == state.epoch
                                && actor.getKillCredit() == actor && actor.getLastHurtByMob() == actor
                                && nativeCredits(actor) == 2,
                        "ordinary End entry did not retain exact native A/source");
                state.sameBodyCreditsRetained = true;
                restore(state.overworldPortal);
                state.overworldPortal = null;
                state.stage = "WAIT_ACTUAL_CLIENT_END";
            }
            if (state.stage.equals("WAIT_ACTUAL_CLIENT_END") && state.clientEndReady) {
                // Native expiry may occur while the real client loads its End view. A new
                // native setter batch supplies the credits-entry input; no timer/proof is edited.
                nativeSelfCredit(actor);
                state.endPortal = placeOwnedPortal(actor);
                state.stage = "WAIT_NATIVE_CREDITS";
            }
            if (state.stage.equals("WAIT_NATIVE_CREDITS") && actor.wonGame) {
                require(actor.seenCredits && actor.isRemoved() && actor.level().dimension() == Level.END
                                && state.endLeaves == 1 && state.clones == 0
                                && actor.getLastHurtByMob() == actor && nativeCredits(actor) == 0,
                        "native End portal did not execute showEndCredits before return");
                state.finalOldCreditsReleased = true;
                restore(state.endPortal);
                state.endPortal = null;
                state.creditsObserved = true;
                state.stage = "WAIT_ACTUAL_CREDITS_RETURN_PACKET";
            }
            if (state.stage.equals("WAIT_ACTUAL_CREDITS_RETURN_PACKET") && state.next != null
                    && actor.connection.player == state.next && state.respawns == 1 && state.awards == 1) {
                var next = state.next;
                var owner = P11NativeStorageBoundary.nativeSourceOwner(next);
                var after = P11NativeStorageBoundary.diagnostics(server, next.getUUID());
                var skills = NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS);
                require(state.screenClosed && state.clones == 1 && state.dimensionEvents == 1
                                && state.overworldLeaves == 1 && state.endLeaves == 1 && state.logoutEvents == 0
                                && next != actor && next.level().dimension() == Level.OVERWORLD
                                && next.connection == actor.connection && next.seenCredits && !next.wonGame
                                && server.getPlayerList().getPlayer(next.getUUID()) == next
                                && next.getAdvancements() == actor.getAdvancements() && next.getStats() == actor.getStats()
                                && next.getFoodData() == actor.getFoodData()
                                && next.getEnderChestInventory() == actor.getEnderChestInventory()
                                && next.getKillCredit() == null && next.getLastHurtByMob() == null
                                && next.totalExperience == state.experience + REWARD
                                && next.getHealth() == Math.min(actor.getHealth(), next.getMaxHealth())
                                && skills != null && next.getExistingDataOrNull(skills) != state.skills
                                && state.attachments.equals(next.serializeAttachments(server.registryAccess()))
                                && next.getTags().contains(MARKER),
                        "actual End keepEverything copy/reward/alias/event parity failed");
                require(owner != null && owner.body(actor) == null && owner.body(next) != null
                                && after.bodyComplete() && !after.candidatePresent() && after.sourceEpoch() > state.epoch
                                && after.sourceInput().equals("MEMORY") && after.sourceFault().equals("NONE")
                                && after.nativeResponsibilities().roots().stream().allMatch(root -> root.count() == 0)
                                && state.sameBodyCreditsRetained && state.finalOldCreditsReleased
                                && P11NativeOperationBoundary.observerFailureCount() == state.observerFailures,
                        "actual End B did not qualify and release native scopes");
                state.stage = "COMPLETE";
                state.complete = true;
                state.observation = "P11-NATIVE-END-PROBE-V1\n"
                        + "entry=ACTUAL_OWNED_OVERWORLD_END_PORTAL_COLLISION_THEN_OWNED_END_PORTAL_COLLISION\n"
                        + "return=ACTUAL_WIN_GAME_PACKET_WINSCREEN_ONCLOSE_CALLBACK_CLIENT_PERFORM_RESPAWN\n"
                        + "wonGameAssignedByFixture=false\nseenCreditsAssignedByFixture=false\nmanualListenerAssignment=false\n"
                        + "syntheticActor=false\nkeepEverything=true\nwasDeath=false\nendConquered=true\n"
                        + "ordinaryDimensionEvents=" + state.dimensionEvents + "\ncloneEvents=" + state.clones
                        + "\nrespawnEvents=" + state.respawns + "\noriginalOverworldLeaves=" + state.overworldLeaves
                        + "\noriginalEndLeaves=" + state.endLeaves + "\nloggedOutEvents=" + state.logoutEvents
                        + "\nactualEndToOverworldCriterionAwards=" + state.awards + "\nexactRewardExperience=" + REWARD
                        + "\ncopy=FRESH_SKILLS_EQUAL_SERIALIZED_ATTACHMENTS_NATIVE_FOOD_ENDERCHEST_ALIAS\n"
                        + "nativeCreditProducer=ACTUAL_AUTHENTICATED_A_NATIVE_PLAYER_AND_MOB_SELF_CREDIT_SETTERS\n"
                        + "pvpOrSecondAuthenticatedAccountClaimed=false\nnativeSetterBatches=2\n"
                        + "sameBodyDimensionNativeN=2_RETAINED_AFTER_ORIGINAL_REVIVE_AND_FINALLY\n"
                        + "showEndCreditsOldANativeN=0_FINAL_REMOVAL_NOT_REASON_WIDE_EXEMPT\n"
                        + "replacementBNativeCreditFields=ORIGINAL_EMPTY_NOT_COPIED\n"
                        + "ownedPortalBlocksRestored=true\ndragonVictoryClaimed=false\nfullOfflineP5Claimed=false\n"
                        + "source=" + after + "\nRESULT=PASS\n";
                write(state.output.resolve("native-end-observation.txt"), state.observation);
            }
        } catch (RuntimeException | Error failure) { state.failure = failure; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        var state = active;
        if (state == null || event.getEntity() != state.original) { return; }
        state.dimensionEvents++;
        require(event.getFrom() == Level.OVERWORLD && event.getTo() == Level.END && state.dimensionEvents == 1,
                "unexpected actual native dimension event");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void cloned(PlayerEvent.Clone event) {
        var state = active;
        if (state == null || event.getOriginal() != state.original) { return; }
        require(event.getEntity() instanceof ServerPlayer && !event.isWasDeath() && state.creditsObserved,
                "End return did not use actual nondeath clone after credits");
        state.next = (ServerPlayer) event.getEntity();
        state.clones++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        var state = active;
        if (state == null || event.getEntity() != state.next) { return; }
        require(event.isEndConquered() && state.clones == 1, "End respawn event has wrong parameter/order");
        state.respawns++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void progress(AdvancementEvent.AdvancementProgressEvent event) {
        var state = active;
        if (state == null || !event.getAdvancement().id().equals(ADVANCEMENT)
                || event.getEntity().getServer() != state.original.getServer()) { return; }
        require(event.getEntity() == state.next && state.respawns == 1
                        && event.getProgressType() == AdvancementEvent.AdvancementProgressEvent.ProgressType.GRANT,
                "End criterion did not run after actual handler installed B");
        state.awards++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void left(EntityLeaveLevelEvent event) {
        var state = active;
        if (state == null || state.complete || event.getEntity() != state.original) { return; }
        if (event.getLevel().dimension() == Level.OVERWORLD) { state.overworldLeaves++; }
        if (event.getLevel().dimension() == Level.END) { state.endLeaves++; }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        var state = active;
        if (state != null && !state.complete && (event.getEntity() == state.original || event.getEntity() == state.next)) {
            state.logoutEvents++;
        }
    }

    static boolean ready() {
        var state = active;
        if (state != null && state.failure != null) { throw new IllegalStateException("End native probe failed", state.failure); }
        return state != null && state.complete;
    }

    static String report() { require(ready(), "End native probe is not complete"); return active.observation; }
    static String pendingDiagnostic() { return active == null ? "END_NOT_STARTED" : "endStage=" + active.stage; }

    static String saved(MinecraftServer server, ServerPlayer actor, String label) {
        var state = active;
        require(ready() && server.isSameThread() && actor.getUUID().equals(state.original.getUUID()), "End save observer lacks actual actor");
        try {
            var root = server.getWorldPath(LevelResource.ROOT);
            var primary = root.resolve("playerdata").resolve(actor.getUUID() + ".dat");
            var dat = NbtIo.readCompressed(primary, NbtAccounter.create(READ_BOUND));
            var host = NbtIo.readCompressed(root.resolve("level.dat"), NbtAccounter.create(READ_BOUND)).getCompound("Data").getCompound("Player");
            for (var payload : List.of(dat, host)) {
                require(payload.getInt("XpTotal") == state.experience + REWARD && payload.getBoolean("seenCredits")
                                && payload.getString("Dimension").equals("minecraft:overworld")
                                && payload.getList("Tags", Tag.TAG_STRING).stream().anyMatch(tag -> tag.getAsString().equals(MARKER)),
                        "original native whole writer lost true End prefix");
            }
            var json = JsonParser.parseString(Files.readString(root.resolve("advancements").resolve(actor.getUUID() + ".json"))).getAsJsonObject();
            require(json.has(ADVANCEMENT.toString()) && json.getAsJsonObject(ADVANCEMENT.toString()).get("done").getAsBoolean(),
                    "original canonical PA writer lost End return criterion");
            Files.copy(primary, state.output.resolve(label + "-player.dat"));
            return "actualEndFiles=PLAYER_HOST_XP_SEEN_CREDITS_TAG_AND_CANONICAL_PA\nlabel=" + label + "\n";
        } catch (IOException failure) { throw new IllegalStateException("End native file readback failed", failure); }
    }

    static void reopened(MinecraftServer server, ServerPlayer actor) {
        var state = active;
        require(ready() && server != state.original.getServer() && actor != state.next && actor.seenCredits
                        && !actor.wonGame && actor.totalExperience == state.experience + REWARD
                        && actor.getAdvancements().getOrStartProgress(server.getAdvancements().get(ADVANCEMENT)).isDone()
                        && state.awards == 1 && actor.getTags().contains(MARKER),
                "actual restart lost End outcome or repeated the original reward");
    }

    private static void nativeSelfCredit(ServerPlayer actor) {
        require(!actor.isRemoved() && actor.getServer().isSameThread(), "native credit producer lacks current A");
        actor.setLastHurtByPlayer(actor);
        actor.setLastHurtByMob(actor);
        require(actor.getKillCredit() == actor && actor.getLastHurtByMob() == actor && nativeCredits(actor) == 2,
                "actual native self-credit field producers did not acquire exactly two N roots");
    }

    private static long nativeCredits(ServerPlayer actor) {
        return P11NativeStorageBoundary.diagnostics(actor.getServer(), actor.getUUID()).nativeResponsibilities()
                .roots().stream().filter(root -> root.kind().equals("NATIVE_CREDIT")).findFirst().orElseThrow().count();
    }

    private static PortalFixture placeOwnedPortal(ServerPlayer actor) {
        var level = actor.serverLevel();
        var pos = actor.blockPosition().above(4).offset(4, 0, 0);
        require(pos.getY() > level.getMinBuildHeight() && pos.getY() + 2 < level.getMaxBuildHeight(), "owned End portal position is outside level");
        var positions = List.of(pos.below(), pos, pos.above(), pos.above(2));
        var previous = new ArrayList<BlockState>();
        for (var target : positions) {
            require(level.getBlockEntity(target) == null, "owned portal cannot overwrite a block entity");
            previous.add(level.getBlockState(target));
        }
        var placed = List.of(Blocks.OBSIDIAN.defaultBlockState(), Blocks.END_PORTAL.defaultBlockState(),
                Blocks.AIR.defaultBlockState(), Blocks.AIR.defaultBlockState());
        for (int i = 0; i < positions.size(); i++) { level.setBlock(positions.get(i), placed.get(i), 3); }
        actor.connection.teleport(pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, actor.getYRot(), actor.getXRot());
        return new PortalFixture(level, positions, previous, placed);
    }

    private static void restore(PortalFixture fixture) {
        require(fixture != null, "owned portal restoration has no exact fixture");
        for (int i = fixture.positions.size() - 1; i >= 0; i--) {
            var pos = fixture.positions.get(i);
            require(fixture.level.getBlockState(pos).equals(fixture.placed.get(i)), "owned portal block changed unexpectedly");
            fixture.level.setBlock(pos, fixture.previous.get(i), 3);
        }
    }

    private static void write(Path target, String value) {
        try { Files.writeString(target, value, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
        catch (IOException failure) { throw new IllegalStateException("End evidence write failed", failure); }
    }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
    private record PortalFixture(ServerLevel level, List<BlockPos> positions, List<BlockState> previous, List<BlockState> placed) {}
    private static final class State {
        final ServerPlayer original;
        final Path output;
        final long epoch;
        final int experience;
        final long observerFailures;
        final Object skills;
        final CompoundTag attachments;
        volatile String stage = "STARTING";
        volatile boolean clientEndReady, creditsObserved, screenClosed, complete;
        volatile Throwable failure;
        volatile String observation;
        boolean sameBodyCreditsRetained, finalOldCreditsReleased;
        ServerPlayer next;
        PortalFixture overworldPortal, endPortal;
        int dimensionEvents, clones, respawns, awards, overworldLeaves, endLeaves, logoutEvents;
        State(ServerPlayer actor, Path output, long epoch) {
            original = actor; this.output = output; this.epoch = epoch; experience = actor.totalExperience;
            observerFailures = P11NativeOperationBoundary.observerFailureCount();
            var type = NeoForgeRegistries.ATTACHMENT_TYPES.get(SKILLS);
            require(type != null && actor.hasData(type), "End fixture missing existing skills");
            skills = actor.getExistingDataOrNull(type);
            attachments = actor.serializeAttachments(actor.registryAccess());
            require(attachments != null, "End fixture missing existing attachment serialization");
        }
    }
}
